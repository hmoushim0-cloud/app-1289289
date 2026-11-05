package sa.factory.fawripayposcashier.hardware

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

enum class BarcodeSymbology {
    EAN13,
    CODE128,
    QR_CODE,
    UNKNOWN
}

data class ScannedBarcodeResult(
    val rawPayload: String,
    val symbology: BarcodeSymbology,
    val isValid: Boolean,
    val countryPrefix: String? = null,
    val isSaudiGs1Prefix: Boolean = false
)

class EscPosThermalPrinterDriver {

    companion object {
        val CMD_INIT = byteArrayOf(0x1B, 0x40) // ESC @
        val CMD_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00) // ESC a 0
        val CMD_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01) // ESC a 1
        val CMD_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02) // ESC a 2
        val CMD_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01) // ESC E 1
        val CMD_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00) // ESC E 0
        val CMD_DOUBLE_SIZE_ON = byteArrayOf(0x1D, 0x21, 0x11) // GS ! 0x11
        val CMD_NORMAL_SIZE = byteArrayOf(0x1D, 0x21, 0x00) // GS ! 0x00
        val CMD_PAPER_CUT = byteArrayOf(0x1D, 0x56, 0x41, 0x03) // GS V 65 3
        val CMD_DRAWER_PULSE = byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0xFA.toByte()) // ESC p 0 25 250

        const val RECEIPT_WIDTH_CHARS = 42
    }

    /**
     * Builds ESC/POS binary print sequence for a standard retail cashier invoice.
     */
    fun buildThermalReceiptBytes(
        storeName: String,
        vatNumber: String,
        invoiceNumber: String,
        timestamp: String,
        lineItems: List<Triple<String, Double, Double>>, // Title, Qty, TotalSar
        subtotalSar: Double,
        vatSar: Double,
        grandTotalSar: Double,
        zatcaBase64Qr: String,
        openCashDrawer: Boolean = true
    ): ByteArray {
        val out = ByteArrayOutputStream()

        // 1. Kick cash drawer if requested
        if (openCashDrawer) {
            out.write(CMD_DRAWER_PULSE)
        }

        // 2. Initialize printer
        out.write(CMD_INIT)

        // 3. Header: Store Name centered & bold
        out.write(CMD_ALIGN_CENTER)
        out.write(CMD_DOUBLE_SIZE_ON)
        out.write(CMD_BOLD_ON)
        out.write("$storeName\n".toByteArray(Charsets.UTF_8))
        out.write(CMD_NORMAL_SIZE)
        out.write(CMD_BOLD_OFF)

        // Subheader metadata
        out.write("الرقم الضريبي: $vatNumber\n".toByteArray(Charsets.UTF_8))
        out.write("فاتورة ضريبية مبسطة: $invoiceNumber\n".toByteArray(Charsets.UTF_8))
        out.write("التاريخ: $timestamp\n".toByteArray(Charsets.UTF_8))
        out.write(makeDivider("=").toByteArray(Charsets.US_ASCII))

        // 4. Items table
        out.write(CMD_ALIGN_LEFT)
        for (item in lineItems) {
            val title = item.first
            val qty = item.second
            val total = item.third
            val line = formatItemLine(title, qty, total)
            out.write(line.toByteArray(Charsets.UTF_8))
        }
        out.write(makeDivider("-").toByteArray(Charsets.US_ASCII))

        // 5. Totals
        out.write(formatTotalsLine("المجموع الخاضع للضريبة", subtotalSar).toByteArray(Charsets.UTF_8))
        out.write(formatTotalsLine("ضريبة القيمة المضافة (15%)", vatSar).toByteArray(Charsets.UTF_8))
        out.write(CMD_BOLD_ON)
        out.write(formatTotalsLine("المبلغ الإجمالي المستحق", grandTotalSar).toByteArray(Charsets.UTF_8))
        out.write(CMD_BOLD_OFF)
        out.write(makeDivider("=").toByteArray(Charsets.US_ASCII))

        // 6. Native ZATCA QR Code (ESC/POS GS ( k commands)
        out.write(CMD_ALIGN_CENTER)
        val qrBytes = buildNativeQrCommands(zatcaBase64Qr)
        out.write(qrBytes)

        // 7. Footer note
        out.write("\nشكراً لتسوقكم معنا\n".toByteArray(Charsets.UTF_8))
        out.write("\n\n\n".toByteArray(Charsets.US_ASCII))

        // 8. Feed and paper cut
        out.write(CMD_PAPER_CUT)

        return out.toByteArray()
    }

    /**
     * Builds ESC/POS native 2D QR Code print command sequence (GS ( k).
     */
    fun buildNativeQrCommands(data: String): ByteArray {
        val out = ByteArrayOutputStream()
        val dataBytes = data.toByteArray(Charsets.UTF_8)
        val numBytes = dataBytes.size + 3

        val pL = (numBytes and 0xFF).toByte()
        val pH = ((numBytes shr 8) and 0xFF).toByte()

        // Select model 2: GS ( k 4 0 49 65 50 0
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00))

        // Module size 5: GS ( k 3 0 49 67 5
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 0x05))

        // Error correction M: GS ( k 3 0 49 69 49
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x31))

        // Store data: GS ( k pL pH 49 80 48 <data>
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30))
        out.write(dataBytes)

        // Print symbol: GS ( k 3 0 49 81 48
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30))

        return out.toByteArray()
    }

    /**
     * Ingests raw scanner buffer and validates barcode checksums and symbologies.
     */
    fun ingestScannerBuffer(buffer: ByteArray): ScannedBarcodeResult {
        // Strip trailing CR/LF
        var end = buffer.size
        while (end > 0 && (buffer[end - 1] == '\r'.toByte() || buffer[end - 1] == '\n'.toByte() || buffer[end - 1] == 0.toByte())) {
            end--
        }
        val cleanString = String(buffer, 0, end, Charsets.US_ASCII).trim()

        if (cleanString.matches(Regex("^[0-9]{13}$"))) {
            val validEan = validateEan13Checksum(cleanString)
            val prefix = cleanString.substring(0, 3)
            val isSaudi = (prefix == "628")
            return ScannedBarcodeResult(
                rawPayload = cleanString,
                symbology = BarcodeSymbology.EAN13,
                isValid = validEan,
                countryPrefix = prefix,
                isSaudiGs1Prefix = isSaudi
            )
        }

        if (cleanString.startsWith("AQ") || cleanString.length > 50) {
            return ScannedBarcodeResult(
                rawPayload = cleanString,
                symbology = BarcodeSymbology.QR_CODE,
                isValid = cleanString.isNotBlank()
            )
        }

        if (cleanString.matches(Regex("^[A-Za-z0-9\\-_]{4,32}$"))) {
            return ScannedBarcodeResult(
                rawPayload = cleanString,
                symbology = BarcodeSymbology.CODE128,
                isValid = true
            )
        }

        return ScannedBarcodeResult(
            rawPayload = cleanString,
            symbology = BarcodeSymbology.UNKNOWN,
            isValid = false
        )
    }

    /**
     * GS1 EAN-13 Modulo 10 Check Digit verification.
     */
    fun validateEan13Checksum(ean13: String): Boolean {
        if (ean13.length != 13 || !ean13.all { it.isDigit() }) return false

        var sum = 0
        for (i in 0 until 12) {
            val digit = ean13[i] - '0'
            val weight = if (i % 2 == 0) 1 else 3
            sum += digit * weight
        }

        val calculatedCheckDigit = (10 - (sum % 10)) % 10
        val actualCheckDigit = ean13[12] - '0'
        return calculatedCheckDigit == actualCheckDigit
    }

    private fun makeDivider(char: String): String {
        return char.repeat(RECEIPT_WIDTH_CHARS) + "\n"
    }

    private fun formatItemLine(title: String, qty: Double, totalSar: Double): String {
        val qtyStr = if (qty % 1.0 == 0.0) "${qty.toInt()}x" else String.format(java.util.Locale.US, "%.2fx", qty)
        val priceStr = String.format(java.util.Locale.US, "%.2f SAR", totalSar)
        val left = "$qtyStr $title"
        val padding = RECEIPT_WIDTH_CHARS - left.length - priceStr.length
        val spaces = if (padding > 0) " ".repeat(padding) else " "
        return "$left$spaces$priceStr\n"
    }

    private fun formatTotalsLine(label: String, amountSar: Double): String {
        val priceStr = String.format(java.util.Locale.US, "%.2f SAR", amountSar)
        val padding = RECEIPT_WIDTH_CHARS - label.length - priceStr.length
        val spaces = if (padding > 0) " ".repeat(padding) else " "
        return "$label$spaces$priceStr\n"
    }
}
