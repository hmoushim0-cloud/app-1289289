package sa.factory.fawripayposcashier.zatca

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64
import kotlin.math.round

enum class ZatcaTaxCategory(val ratePercentage: Double, val categoryCode: String, val descriptionArabic: String) {
    STANDARD_15(15.0, "S", "خاضع للضريبة بالنسبة الأساسية 15%"),
    ZERO_RATED(0.0, "Z", "خاضع للضريبة بنسبة الصفر 0%"),
    EXEMPT(0.0, "E", "معفى من ضريبة القيمة المضافة")
}

data class InvoiceLineItem(
    val itemId: String,
    val itemNameArabic: String,
    val unitPriceSar: Double,
    val quantity: Double,
    val lineDiscountSar: Double = 0.0,
    val taxCategory: ZatcaTaxCategory = ZatcaTaxCategory.STANDARD_15
) {
    init {
        require(unitPriceSar >= 0.0) { "Unit price must be non-negative" }
        require(quantity > 0.0) { "Quantity must be positive" }
        require(lineDiscountSar >= 0.0) { "Discount must be non-negative" }
    }

    val grossAmountSar: Double
        get() = roundToCents(unitPriceSar * quantity)

    val netTaxableAmountSar: Double
        get() = roundToCents(grossAmountSar - lineDiscountSar)

    val vatAmountSar: Double
        get() = roundToCents(netTaxableAmountSar * (taxCategory.ratePercentage / 100.0))

    val lineTotalWithVatSar: Double
        get() = roundToCents(netTaxableAmountSar + vatAmountSar)

    private fun roundToCents(value: Double): Double = round(value * 100.0) / 100.0
}

data class ZatcaTaxInvoice(
    val invoiceNumber: String,
    val invoiceUuid: String,
    val sellerName: String,
    val vatRegistrationNumber: String, // 15 digits, starts and ends with 3
    val issueTimestampIso8601: String, // e.g., 2026-11-05T07:00:00Z
    val items: List<InvoiceLineItem>,
    val additionalInvoiceDiscountSar: Double = 0.0
) {
    init {
        require(sellerName.isNotBlank()) { "Seller name must not be blank" }
        require(vatRegistrationNumber.length == 15 && vatRegistrationNumber.startsWith("3") && vatRegistrationNumber.endsWith("3")) {
            "Saudi VAT registration number must be 15 digits starting and ending with 3"
        }
        require(items.isNotEmpty()) { "Invoice must contain at least one line item" }
    }

    val totalGrossSar: Double
        get() = roundToCents(items.sumByDouble { it.grossAmountSar })

    val totalItemDiscountsSar: Double
        get() = roundToCents(items.sumByDouble { it.lineDiscountSar })

    val totalDiscountSar: Double
        get() = roundToCents(totalItemDiscountsSar + additionalInvoiceDiscountSar)

    val totalTaxableAmountSar: Double
        get() = roundToCents(items.sumByDouble { it.netTaxableAmountSar } - additionalInvoiceDiscountSar)

    val totalVatSar: Double
        get() = roundToCents(items.sumByDouble { it.vatAmountSar })

    val grandTotalWithVatSar: Double
        get() = roundToCents(totalTaxableAmountSar + totalVatSar)

    private fun roundToCents(value: Double): Double = round(value * 100.0) / 100.0
}

data class ZatcaTlvQrResult(
    val rawTlvBytes: ByteArray,
    val base64QrPayload: String,
    val invoiceSha256DigestHex: String
)

class ZatcaPhase2FatooraEngine {

    companion object {
        const val TAG_SELLER_NAME = 1
        const val TAG_VAT_REGISTRATION_NUMBER = 2
        const val TAG_ISSUE_TIMESTAMP = 3
        const val TAG_INVOICE_TOTAL_WITH_VAT = 4
        const val TAG_VAT_TOTAL = 5
        const val TAG_INVOICE_HASH = 6
        const val TAG_ECDSA_SIGNATURE = 7
        const val TAG_ECDSA_PUBLIC_KEY = 8
    }

    /**
     * Generates standard ZATCA Phase 2 compliant TLV structure and Base64 QR payload.
     */
    fun generateZatcaTlvQrPayload(
        invoice: ZatcaTaxInvoice,
        ecdsaSignatureBytes: ByteArray? = null,
        ecdsaPublicKeyBytes: ByteArray? = null
    ): ZatcaTlvQrResult {
        val out = ByteArrayOutputStream()

        // Tag 1: Seller Name
        writeTlvTag(out, TAG_SELLER_NAME, invoice.sellerName.toByteArray(Charsets.UTF_8))

        // Tag 2: VAT Registration Number (15 digits)
        writeTlvTag(out, TAG_VAT_REGISTRATION_NUMBER, invoice.vatRegistrationNumber.toByteArray(Charsets.US_ASCII))

        // Tag 3: Issue Timestamp (ISO 8601)
        writeTlvTag(out, TAG_ISSUE_TIMESTAMP, invoice.issueTimestampIso8601.toByteArray(Charsets.US_ASCII))

        // Tag 4: Invoice Total (with VAT) formatted to 2 decimals
        val totalFormatted = String.format(java.util.Locale.US, "%.2f", invoice.grandTotalWithVatSar)
        writeTlvTag(out, TAG_INVOICE_TOTAL_WITH_VAT, totalFormatted.toByteArray(Charsets.US_ASCII))

        // Tag 5: Total VAT Amount formatted to 2 decimals
        val vatFormatted = String.format(java.util.Locale.US, "%.2f", invoice.totalVatSar)
        writeTlvTag(out, TAG_VAT_TOTAL, vatFormatted.toByteArray(Charsets.US_ASCII))

        // Tag 6: SHA-256 Digest of canonical invoice summary
        val invoiceDigest = computeInvoiceCanonicalDigest(invoice)
        writeTlvTag(out, TAG_INVOICE_HASH, invoiceDigest)

        // Tag 7: Optional ECDSA Cryptographic Stamp (Phase 2 Integration)
        if (ecdsaSignatureBytes != null && ecdsaSignatureBytes.isNotEmpty()) {
            writeTlvTag(out, TAG_ECDSA_SIGNATURE, ecdsaSignatureBytes)
        }

        // Tag 8: Optional ECDSA Public Key (Phase 2 Integration)
        if (ecdsaPublicKeyBytes != null && ecdsaPublicKeyBytes.isNotEmpty()) {
            writeTlvTag(out, TAG_ECDSA_PUBLIC_KEY, ecdsaPublicKeyBytes)
        }

        val rawBytes = out.toByteArray()
        val base64String = Base64.getEncoder().encodeToString(rawBytes)
        val digestHex = invoiceDigest.joinToString("") { String.format("%02x", it) }

        return ZatcaTlvQrResult(
            rawTlvBytes = rawBytes,
            base64QrPayload = base64String,
            invoiceSha256DigestHex = digestHex
        )
    }

    /**
     * Decodes and parses raw TLV bytes into individual tag records.
     */
    fun parseTlvTags(tlvBytes: ByteArray): Map<Int, ByteArray> {
        val tags = mutableMapOf<Int, ByteArray>()
        var index = 0
        while (index < tlvBytes.size) {
            val tag = tlvBytes[index].toInt() and 0xFF
            index++
            if (index >= tlvBytes.size) break
            val length = tlvBytes[index].toInt() and 0xFF
            index++
            if (index + length > tlvBytes.size) break

            val value = ByteArray(length)
            System.arraycopy(tlvBytes, index, value, 0, length)
            tags[tag] = value
            index += length
        }
        return tags
    }

    /**
     * Computes canonical SHA-256 digest of invoice metadata for Tag 6.
     */
    fun computeInvoiceCanonicalDigest(invoice: ZatcaTaxInvoice): ByteArray {
        val canonical = "${invoice.invoiceNumber}|${invoice.invoiceUuid}|${invoice.vatRegistrationNumber}|${invoice.issueTimestampIso8601}|${String.format(java.util.Locale.US, "%.2f", invoice.grandTotalWithVatSar)}"
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(canonical.toByteArray(Charsets.UTF_8))
    }

    private fun writeTlvTag(out: ByteArrayOutputStream, tag: Int, value: ByteArray) {
        out.write(tag)
        out.write(value.size)
        out.write(value)
    }
}
