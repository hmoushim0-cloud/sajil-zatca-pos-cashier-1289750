# سقار بوينت — بناء روابط ذكي لنقاط البيع (sajil-zatca-pos-cashier-1289750)
> نظام نقاط بيع متكامل مع بناء روابط خلفية آمنة — متوافق مع طابعات ESC/POS

- **Project ID**: #1289750
- **Commercial Archetype**: pos_cashier
- **Bound Hardware Peripheral**: ESC/POS Thermal Receipt Printer + Barcode Scanner
- **Package Name**: sa.factory.sajilzatcaposcashi
- **Zero-Emoji Invariant**: Strictly Enforced (100% Vector Icons and Clean Typography)

---

## Production Domain Engines (Iteration 93)

### 1. ZATCA Phase 2 Cryptographic e-Invoicing Engine (`ZatcaPhase2CryptographicEngine.kt`)
- **FATOORA Phase 2 Compliance**:
  - Implements 8-Tag TLV binary formatting (Seller Name, 15-digit VAT number, ISO 8601 Timestamp, Total SAR, VAT SAR, Invoice SHA-256 Hash, ECDSA Signature, Public Key).
  - Validates Saudi Tax Identification Numbers (15-digit TIN starting and ending with '3').
  - Computes canonical SHA-256 invoice hashes for cryptographic invoice chaining.
  - Generates Base64 encoded ZATCA Phase 2 QR codes for simplified and standard tax receipts.

### 2. ESC/POS Thermal Receipt Printer Protocol Engine (`EscPosThermalPrinterProtocolEngine.kt`)
- **Binary ESC/POS Protocol Implementation**:
  - Hardware initialization (`ESC @`), text alignment (`ESC a 0/1/2`), double-sizing (`GS ! 17`), line spacing, and partial paper cut (`GS V 66 0`).
  - Native 2D QR Code binary sequence generation (`GS ( k`) directly embedded into the thermal byte stream.
  - Code128 1D barcode command formatting for automated scanning workflows.
  - End-to-end receipt assembler producing ready-to-transmit binary print buffers with two-column formatted Arabic and English lines.

---

## Verification & Unit Testing

- 10 comprehensive unit tests in `SajilZatcaRegressionTest.kt`:
  1. `testCheckoutCalculationAndTax`: Subtotal, 15% VAT, and total SAR verification.
  2. `testSaudiVatNumberValidationValid`: Verification of valid 15-digit Saudi TIN.
  3. `testSaudiVatNumberValidationInvalid`: Rejection of non-compliant VAT numbers.
  4. `testTlvTagEncodingSingleAndMultiByte`: TLV tag and length formatting.
  5. `testCanonicalInvoiceSha256HashCalculation`: Deterministic SHA-256 hash chaining.
  6. `testZatcaPhase2QrBase64PayloadStructure`: Base64 QR code and TLV structure validation.
  7. `testZatcaInvoiceComplianceProcessing`: End-to-end simplified tax invoice processing.
  8. `testEscPosBinaryCommandsInitAndAlignment`: Binary ESC/POS command sequences.
  9. `testEscPosQrCodeCommandGeneration`: Native 2D QR code command framing.
  10. `testEscPosThermalReceiptAssemblyAndCut`: Full receipt assembly and paper cut termination.
