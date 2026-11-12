package sa.factory.sajilzatcaposcashi

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import sa.factory.sajilzatcaposcashi.data.repository.SajilZatcaRepositoryImpl
import sa.factory.sajilzatcaposcashi.engine.EscPosThermalPrinterProtocolEngine
import sa.factory.sajilzatcaposcashi.engine.EscPosThermalPrinterProtocolEngine.ReceiptLineItem
import sa.factory.sajilzatcaposcashi.engine.EscPosThermalPrinterProtocolEngine.ReceiptPayload
import sa.factory.sajilzatcaposcashi.engine.ZatcaPhase2CryptographicEngine
import sa.factory.sajilzatcaposcashi.engine.ZatcaPhase2CryptographicEngine.ZatcaInvoiceDetails
import sa.factory.sajilzatcaposcashi.hardware.HardwareSensorDriver
import sa.factory.sajilzatcaposcashi.model.SajilZatcaItem
import java.util.Base64

class SajilZatcaRegressionTest {

    // 1. Checkout Calculation & 15% VAT
    @Test
    fun testCheckoutCalculationAndTax() = runBlocking {
        val sensorDriver = HardwareSensorDriver()
        val repository = SajilZatcaRepositoryImpl(sensorDriver)

        val item1 = SajilZatcaItem("t1", "خدمة تجريبية أ", "الوصف", 100.0, "معتمد", "عام")
        val item2 = SajilZatcaItem("t2", "خدمة تجريبية ب", "الوصف", 200.0, "معتمد", "عام")

        repository.addToCart(item1)
        repository.addToCart(item2)

        val order = repository.checkout("Mada EMV")

        assertEquals(300.0, order.subtotalSar, 0.01)
        assertEquals(45.0, order.vatSar, 0.01)
        assertEquals(345.0, order.totalSar, 0.01)
        assertEquals(0, repository.activeCart.value.size)
    }

    // 2. Saudi VAT TIN number validation (valid cases)
    @Test
    fun testSaudiVatNumberValidationValid() {
        val engine = ZatcaPhase2CryptographicEngine()
        assertTrue(engine.isValidSaudiVatNumber("310123456700003"))
        assertTrue(engine.isValidSaudiVatNumber("300000000000003"))
    }

    // 3. Saudi VAT TIN number validation (invalid cases)
    @Test
    fun testSaudiVatNumberValidationInvalid() {
        val engine = ZatcaPhase2CryptographicEngine()
        assertFalse(engine.isValidSaudiVatNumber("123456789012345")) // Doesn't start/end with 3
        assertFalse(engine.isValidSaudiVatNumber("31012345670003"))  // 14 digits (must be 15)
        assertFalse(engine.isValidSaudiVatNumber("3101234567000033")) // 16 digits
        assertFalse(engine.isValidSaudiVatNumber("31012345670000A")) // Non-digits
    }

    // 4. TLV tag encoding formatting
    @Test
    fun testTlvTagEncodingSingleAndMultiByte() {
        val engine = ZatcaPhase2CryptographicEngine()
        val encoded = engine.encodeTlvString(ZatcaPhase2CryptographicEngine.TAG_SELLER_NAME, "سقار")

        assertEquals(1, encoded[0].toInt()) // Tag 1
        val len = "سقار".toByteArray(Charsets.UTF_8).size
        assertEquals(len, encoded[1].toInt()) // Length in bytes
    }

    // 5. Canonical invoice SHA-256 hash calculation
    @Test
    fun testCanonicalInvoiceSha256HashCalculation() {
        val engine = ZatcaPhase2CryptographicEngine()
        val invoice = ZatcaInvoiceDetails(
            invoiceId = "INV-2026-0001",
            sellerName = "شركة سقار لنقاط البيع",
            vatNumber = "310123456700003",
            timestampIso = "2026-11-12T04:00:00Z",
            subtotalSar = 100.0,
            vatSar = 15.0,
            totalSar = 115.0
        )

        val hash = engine.calculateInvoiceHash(invoice)
        assertNotNull(hash)
        assertEquals(64, hash.length) // SHA-256 produces 64 hex characters
    }

    // 6. ZATCA Phase 2 QR Base64 payload decoding
    @Test
    fun testZatcaPhase2QrBase64PayloadStructure() {
        val engine = ZatcaPhase2CryptographicEngine()
        val invoice = ZatcaInvoiceDetails(
            invoiceId = "INV-2026-0002",
            sellerName = "متجر سقار المركزي",
            vatNumber = "310123456700003",
            timestampIso = "2026-11-12T05:00:00Z",
            subtotalSar = 200.0,
            vatSar = 30.0,
            totalSar = 230.0
        )

        val hash = engine.calculateInvoiceHash(invoice)
        val qrBase64 = engine.generateZatcaPhase2QrPayload(invoice, hash)

        assertNotNull(qrBase64)
        val decodedBytes = Base64.getDecoder().decode(qrBase64)
        assertTrue(decodedBytes.size > 50)
        assertEquals(1, decodedBytes[0].toInt()) // First tag is Seller Name (Tag 1)
    }

    // 7. Full ZATCA compliance processing & audit note
    @Test
    fun testZatcaInvoiceComplianceProcessing() {
        val engine = ZatcaPhase2CryptographicEngine()
        val invoice = ZatcaInvoiceDetails(
            invoiceId = "INV-2026-0003",
            sellerName = "سقار للأنظمة الرقمية",
            vatNumber = "310123456700003",
            timestampIso = "2026-11-12T06:00:00Z",
            subtotalSar = 500.0,
            vatSar = 75.0,
            totalSar = 575.0
        )

        val result = engine.processZatcaInvoice(invoice)
        assertTrue(result.isCompliant)
        assertEquals(64, result.invoiceHashHex.length)
        assertTrue(result.auditTrail.contains("[OK] ZATCA Phase 2 Compliant"))
        assertTrue(result.tlvByteLength > 0)
    }

    // 8. ESC/POS binary initialization and text alignment
    @Test
    fun testEscPosBinaryCommandsInitAndAlignment() {
        assertArrayEquals(byteArrayOf(0x1B, 0x40), EscPosThermalPrinterProtocolEngine.CMD_INIT)
        assertArrayEquals(byteArrayOf(0x1B, 0x61, 0x00), EscPosThermalPrinterProtocolEngine.CMD_ALIGN_LEFT)
        assertArrayEquals(byteArrayOf(0x1B, 0x61, 0x01), EscPosThermalPrinterProtocolEngine.CMD_ALIGN_CENTER)
        assertArrayEquals(byteArrayOf(0x1B, 0x61, 0x02), EscPosThermalPrinterProtocolEngine.CMD_ALIGN_RIGHT)
    }

    // 9. ESC/POS native QR Code binary sequence generation
    @Test
    fun testEscPosQrCodeCommandGeneration() {
        val engine = EscPosThermalPrinterProtocolEngine()
        val qrPayload = "AQVTYWppbA=="
        val commands = engine.buildNativeQrCodeCommand(qrPayload)

        assertTrue(commands.isNotEmpty())
        assertEquals(0x1D.toByte(), commands[0]) // GS
        assertEquals(0x28.toByte(), commands[1]) // (
        assertEquals(0x6B.toByte(), commands[2]) // k
    }

    // 10. Complete ESC/POS receipt assembly and paper cut
    @Test
    fun testEscPosThermalReceiptAssemblyAndCut() {
        val engine = EscPosThermalPrinterProtocolEngine()
        val receipt = ReceiptPayload(
            storeName = "سقار بوينت للمبيعات",
            branchName = "فرع الرياض - طريق الملك فهد",
            vatNumber = "310123456700003",
            invoiceId = "INV-9901",
            items = listOf(
                ReceiptLineItem("قارئ باركود ليزري", 1, 150.0, 150.0),
                ReceiptLineItem("ورق طابعة حرارية 80مم", 2, 25.0, 50.0)
            ),
            subtotalSar = 200.0,
            vatSar = 30.0,
            totalSar = 230.0,
            zatcaQrBase64 = "AQhTYWppbCBQT1MCEDEwMTIzNDU2NzAwMDAz..."
        )

        val receiptBytes = engine.assembleThermalReceipt(receipt, columnsWidth = 32)
        assertTrue(receiptBytes.isNotEmpty())

        // Check initialization at start
        assertEquals(0x1B.toByte(), receiptBytes[0])
        assertEquals(0x40.toByte(), receiptBytes[1])

        // Check partial cut command at end
        val cutCommand = EscPosThermalPrinterProtocolEngine.CMD_PAPER_CUT_PARTIAL
        val endsWithCut = receiptBytes.takeLast(cutCommand.size).toByteArray().contentEquals(cutCommand)
        assertTrue(endsWithCut)
    }
}
