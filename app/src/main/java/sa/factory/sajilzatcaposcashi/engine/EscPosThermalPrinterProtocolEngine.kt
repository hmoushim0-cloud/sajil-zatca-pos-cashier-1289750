package sa.factory.sajilzatcaposcashi.engine

import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * EscPosThermalPrinterProtocolEngine
 * Production domain engine for ESC/POS binary receipt printing and barcode scanning hardware.
 * Formats ESC/POS byte streams for 58mm and 80mm thermal receipt printers, including alignment,
 * font sizing, ZATCA QR code embedding, barcode generation, and cutter actuation.
 *
 * Strict zero-emoji compliance.
 */
class EscPosThermalPrinterProtocolEngine {

    companion object {
        val CMD_INIT = byteArrayOf(0x1B, 0x40) // ESC @
        val CMD_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00) // ESC a 0
        val CMD_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01) // ESC a 1
        val CMD_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02) // ESC a 2
        val CMD_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01) // ESC E 1
        val CMD_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00) // ESC E 0
        val CMD_DOUBLE_SIZE = byteArrayOf(0x1D, 0x21, 0x11) // GS ! 17 (2x width & height)
        val CMD_NORMAL_SIZE = byteArrayOf(0x1D, 0x21, 0x00) // GS ! 0
        val CMD_PAPER_CUT_PARTIAL = byteArrayOf(0x1D, 0x56, 0x42, 0x00) // GS V 66 0
        val CMD_DRAWER_KICK = byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0xFA.toByte()) // ESC p 0 25 250
    }

    data class ReceiptLineItem(
        val name: String,
        val quantity: Int,
        val unitPriceSar: Double,
        val totalPriceSar: Double
    )

    data class ReceiptPayload(
        val storeName: String,
        val branchName: String,
        val vatNumber: String,
        val invoiceId: String,
        val items: List<ReceiptLineItem>,
        val subtotalSar: Double,
        val vatSar: Double,
        val totalSar: Double,
        val zatcaQrBase64: String
    )

    /**
     * Formats two-column tabular line with left and right aligned text within a fixed width (e.g. 32 or 42 cols).
     */
    fun formatTwoColumns(left: String, right: String, totalColumns: Int = 32): String {
        val spaceCount = totalColumns - (left.length + right.length)
        val padding = if (spaceCount > 0) " ".repeat(spaceCount) else " "
        return "$left$padding$right\n"
    }

    /**
     * Constructs ESC/POS native 2D QR Code binary commands (Model 2, Error Correction Level M).
     */
    fun buildNativeQrCodeCommand(qrData: String): ByteArray {
        val out = ByteArrayOutputStream()
        val dataBytes = qrData.toByteArray(Charsets.UTF_8)
        val dataLen = dataBytes.size + 3

        val pL = (dataLen and 0xFF).toByte()
        val pH = ((dataLen ushr 8) and 0xFF).toByte()

        // 1. Set QR Code Model (Model 2)
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00))

        // 2. Set Module Size (size = 4 dots)
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 0x04))

        // 3. Set Error Correction Level (Level M = 49)
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x31))

        // 4. Store Data in Symbol Storage Area
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30))
        out.write(dataBytes)

        // 5. Print QR Code Symbol
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30))

        return out.toByteArray()
    }

    /**
     * Builds Code128 1D Barcode command stream for invoice tracking.
     */
    fun buildCode128BarcodeCommand(barcodeData: String): ByteArray {
        val out = ByteArrayOutputStream()
        val cleanData = barcodeData.trim()
        val dataBytes = cleanData.toByteArray(Charsets.US_ASCII)

        // Set barcode height to 50 dots
        out.write(byteArrayOf(0x1D, 0x68, 0x32))

        // Set barcode width to 2
        out.write(byteArrayOf(0x1D, 0x77, 0x02))

        // Print Code128 (GS k 73 len bytes)
        out.write(byteArrayOf(0x1D, 0x6B, 0x49, dataBytes.size.toByte()))
        out.write(dataBytes)
        out.write(0x0A) // LF

        return out.toByteArray()
    }

    /**
     * Assembles a complete, ready-to-print binary receipt byte buffer.
     */
    fun assembleThermalReceipt(receipt: ReceiptPayload, columnsWidth: Int = 32): ByteArray {
        val out = ByteArrayOutputStream()

        // 1. Initialize Printer
        out.write(CMD_INIT)

        // 2. Store Header
        out.write(CMD_ALIGN_CENTER)
        out.write(CMD_DOUBLE_SIZE)
        out.write(CMD_BOLD_ON)
        out.write("${receipt.storeName}\n".toByteArray(Charsets.UTF_8))
        out.write(CMD_NORMAL_SIZE)
        out.write(CMD_BOLD_OFF)
        out.write("${receipt.branchName}\n".toByteArray(Charsets.UTF_8))
        out.write("الرقم الضريبي: ${receipt.vatNumber}\n".toByteArray(Charsets.UTF_8))
        out.write("فاتورة ضريبية مبسطة\n".toByteArray(Charsets.UTF_8))
        out.write("رقم الفاتورة: ${receipt.invoiceId}\n".toByteArray(Charsets.UTF_8))
        out.write(("-".repeat(columnsWidth) + "\n").toByteArray(Charsets.US_ASCII))

        // 3. Itemized Products
        out.write(CMD_ALIGN_LEFT)
        for (item in receipt.items) {
            val itemTitle = "${item.quantity}x ${item.name}"
            val itemPrice = String.format(Locale.US, "%.2f", item.totalPriceSar)
            out.write(formatTwoColumns(itemTitle, itemPrice, columnsWidth).toByteArray(Charsets.UTF_8))
        }
        out.write(("-".repeat(columnsWidth) + "\n").toByteArray(Charsets.US_ASCII))

        // 4. Financial Totals
        val subtotalStr = String.format(Locale.US, "%.2f", receipt.subtotalSar)
        val vatStr = String.format(Locale.US, "%.2f", receipt.vatSar)
        val totalStr = String.format(Locale.US, "%.2f", receipt.totalSar)

        out.write(formatTwoColumns("المجموع الفرعي:", "$subtotalStr SAR", columnsWidth).toByteArray(Charsets.UTF_8))
        out.write(formatTwoColumns("ضريبة القيمة المضافة (15%):", "$vatStr SAR", columnsWidth).toByteArray(Charsets.UTF_8))
        out.write(CMD_BOLD_ON)
        out.write(formatTwoColumns("الإجمالي النهائي:", "$totalStr SAR", columnsWidth).toByteArray(Charsets.UTF_8))
        out.write(CMD_BOLD_OFF)
        out.write(("-".repeat(columnsWidth) + "\n").toByteArray(Charsets.US_ASCII))

        // 5. Embedded ZATCA Phase 2 QR Code
        out.write(CMD_ALIGN_CENTER)
        out.write(buildNativeQrCodeCommand(receipt.zatcaQrBase64))
        out.write(0x0A) // LF
        out.write("شكرا لزيارتكم - سجّل زاتكا معتمد\n".toByteArray(Charsets.UTF_8))
        out.write(byteArrayOf(0x0A, 0x0A, 0x0A)) // 3 feeds

        // 6. Cut Paper
        out.write(CMD_PAPER_CUT_PARTIAL)

        return out.toByteArray()
    }
}
