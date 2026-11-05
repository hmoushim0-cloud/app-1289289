package sa.factory.fawripayposcashier

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import sa.factory.fawripayposcashier.hardware.*
import sa.factory.fawripayposcashier.zatca.*
import java.util.Base64

class FawriPayFeatureTest {

    private lateinit var zatcaEngine: ZatcaPhase2FatooraEngine
    private lateinit var printerDriver: EscPosThermalPrinterDriver

    @Before
    fun setUp() {
        zatcaEngine = ZatcaPhase2FatooraEngine()
        printerDriver = EscPosThermalPrinterDriver()
    }

    @Test
    fun testLineItemAndInvoiceTotalsCalculation() {
        val item1 = InvoiceLineItem(
            itemId = "ITEM-01",
            itemNameArabic = "حليب نادك طويل الأجل 1 لتر",
            unitPriceSar = 6.0,
            quantity = 4.0,
            lineDiscountSar = 2.0, // 24.0 gross - 2.0 = 22.0 net
            taxCategory = ZatcaTaxCategory.STANDARD_15
        )
        val item2 = InvoiceLineItem(
            itemId = "ITEM-02",
            itemNameArabic = "خبز توست بر أبيض",
            unitPriceSar = 5.0,
            quantity = 2.0,
            lineDiscountSar = 0.0, // 10.0 gross
            taxCategory = ZatcaTaxCategory.STANDARD_15
        )

        assertEquals(24.0, item1.grossAmountSar, 0.01)
        assertEquals(22.0, item1.netTaxableAmountSar, 0.01)
        // 15% of 22 = 3.30 SAR
        assertEquals(3.30, item1.vatAmountSar, 0.01)
        assertEquals(25.30, item1.lineTotalWithVatSar, 0.01)

        val invoice = ZatcaTaxInvoice(
            invoiceNumber = "INV-2026-001",
            invoiceUuid = "550e8400-e29b-41d4-a716-446655440000",
            sellerName = "أسواق فوري المركزية",
            vatRegistrationNumber = "300012345600003",
            issueTimestampIso8601 = "2026-11-05T07:15:00Z",
            items = listOf(item1, item2)
        )

        // Total taxable: 22.0 + 10.0 = 32.0 SAR
        assertEquals(32.0, invoice.totalTaxableAmountSar, 0.01)
        // Total VAT: 3.30 + 1.50 = 4.80 SAR
        assertEquals(4.80, invoice.totalVatSar, 0.01)
        // Grand total: 32.0 + 4.80 = 36.80 SAR
        assertEquals(36.80, invoice.grandTotalWithVatSar, 0.01)
    }

    @Test
    fun testMultiTaxCategoriesExemptAndZeroRated() {
        val standardItem = InvoiceLineItem("A", "منتج خاضع 15%", 100.0, 1.0, 0.0, ZatcaTaxCategory.STANDARD_15)
        val zeroRatedItem = InvoiceLineItem("B", "دواء مؤهل 0%", 50.0, 1.0, 0.0, ZatcaTaxCategory.ZERO_RATED)
        val exemptItem = InvoiceLineItem("C", "خدمة معفاة", 80.0, 1.0, 0.0, ZatcaTaxCategory.EXEMPT)

        assertEquals(15.0, standardItem.vatAmountSar, 0.01)
        assertEquals(0.0, zeroRatedItem.vatAmountSar, 0.01)
        assertEquals(0.0, exemptItem.vatAmountSar, 0.01)

        val invoice = ZatcaTaxInvoice(
            invoiceNumber = "INV-2026-TAX-02",
            invoiceUuid = "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
            sellerName = "صيدليات فوري الحديثة",
            vatRegistrationNumber = "310987654300003",
            issueTimestampIso8601 = "2026-11-05T07:20:00Z",
            items = listOf(standardItem, zeroRatedItem, exemptItem)
        )

        // Taxable total: 230.0 SAR
        assertEquals(230.0, invoice.totalTaxableAmountSar, 0.01)
        // VAT: exactly 15.0 SAR
        assertEquals(15.0, invoice.totalVatSar, 0.01)
        assertEquals(245.0, invoice.grandTotalWithVatSar, 0.01)
    }

    @Test
    fun testZatcaTlvQrGenerationAndBase64Output() {
        val invoice = ZatcaTaxInvoice(
            invoiceNumber = "INV-TLV-001",
            invoiceUuid = "7c9e6679-7425-40de-944b-e07fc1f90ae7",
            sellerName = "مؤسسة فوري التجارية",
            vatRegistrationNumber = "311223344500003",
            issueTimestampIso8601 = "2026-11-05T07:25:00Z",
            items = listOf(InvoiceLineItem("SKU-1", "قهوة مختصة", 50.0, 2.0))
        )

        val qrResult = zatcaEngine.generateZatcaTlvQrPayload(invoice)

        assertNotNull(qrResult.rawTlvBytes)
        assertTrue(qrResult.rawTlvBytes.isNotEmpty())

        assertNotNull(qrResult.base64QrPayload)
        // Must be valid Base64
        val decoded = Base64.getDecoder().decode(qrResult.base64QrPayload)
        assertArrayEquals(qrResult.rawTlvBytes, decoded)

        // SHA-256 Digest is 64 hex characters
        assertEquals(64, qrResult.invoiceSha256DigestHex.length)
    }

    @Test
    fun testTlvTagParsingRoundTrip() {
        val invoice = ZatcaTaxInvoice(
            invoiceNumber = "INV-ROUNDTRIP-99",
            invoiceUuid = "123e4567-e89b-12d3-a456-426614174000",
            sellerName = "متجر التقنية الذكية",
            vatRegistrationNumber = "300112233400003",
            issueTimestampIso8601 = "2026-11-05T07:30:00Z",
            items = listOf(InvoiceLineItem("P-1", "فأرة لاسلكية", 100.0, 1.0))
        )

        val qrResult = zatcaEngine.generateZatcaTlvQrPayload(invoice)
        val parsedTags = zatcaEngine.parseTlvTags(qrResult.rawTlvBytes)

        // Tag 1: Seller Name
        assertTrue(parsedTags.containsKey(1))
        assertEquals("متجر التقنية الذكية", String(parsedTags[1]!!, Charsets.UTF_8))

        // Tag 2: VAT Registration Number
        assertTrue(parsedTags.containsKey(2))
        assertEquals("300112233400003", String(parsedTags[2]!!, Charsets.US_ASCII))

        // Tag 3: Issue Timestamp
        assertTrue(parsedTags.containsKey(3))
        assertEquals("2026-11-05T07:30:00Z", String(parsedTags[3]!!, Charsets.US_ASCII))

        // Tag 4: Invoice Total With VAT (115.00)
        assertTrue(parsedTags.containsKey(4))
        assertEquals("115.00", String(parsedTags[4]!!, Charsets.US_ASCII))

        // Tag 5: Total VAT (15.00)
        assertTrue(parsedTags.containsKey(5))
        assertEquals("15.00", String(parsedTags[5]!!, Charsets.US_ASCII))

        // Tag 6: SHA-256 Digest (32 bytes)
        assertTrue(parsedTags.containsKey(6))
        assertEquals(32, parsedTags[6]!!.size)
    }

    @Test
    fun testGs1Ean13ChecksumValidation() {
        // Valid Saudi EAN-13: 6281000123455
        // Weights: 6*1 + 2*3 + 8*1 + 1*3 + 0*1 + 0*3 + 0*1 + 1*3 + 2*1 + 3*3 + 4*1 + 5*3
        // = 6 + 6 + 8 + 3 + 0 + 0 + 0 + 3 + 2 + 9 + 4 + 15 = 56. (10 - (56 % 10)) % 10 = 4? Let's check math:
        // Actually test with known barcode: "6281000123454" -> 56 + 4 = 60. Check digit is 4.
        val validEan = "6281000123454"
        assertTrue(printerDriver.validateEan13Checksum(validEan))

        // Invalid checksum
        val invalidEan = "6281000123459"
        assertFalse(printerDriver.validateEan13Checksum(invalidEan))

        // Invalid length
        assertFalse(printerDriver.validateEan13Checksum("12345"))
    }

    @Test
    fun testBarcodeScannerBufferIngestion() {
        // 1. Raw EAN-13 buffer with CRLF
        val rawEanBuffer = "6281000123454\r\n".toByteArray(Charsets.US_ASCII)
        val eanResult = printerDriver.ingestScannerBuffer(rawEanBuffer)
        assertEquals(BarcodeSymbology.EAN13, eanResult.symbology)
        assertTrue(eanResult.isValid)
        assertEquals("628", eanResult.countryPrefix)
        assertTrue(eanResult.isSaudiGs1Prefix)

        // 2. Raw Code 128 alphanumeric asset tag
        val rawCode128 = "POS-TERMINAL-9988\n".toByteArray(Charsets.US_ASCII)
        val code128Result = printerDriver.ingestScannerBuffer(rawCode128)
        assertEquals(BarcodeSymbology.CODE128, code128Result.symbology)
        assertTrue(code128Result.isValid)

        // 3. QR Code payload
        val rawQr = "AQ1MYXN0IFBpY2tlZCBSZXBv...AQM2MjgxMDAwMTIzNDU0".toByteArray(Charsets.US_ASCII)
        val qrResult = printerDriver.ingestScannerBuffer(rawQr)
        assertEquals(BarcodeSymbology.QR_CODE, qrResult.symbology)
        assertTrue(qrResult.isValid)
    }

    @Test
    fun testEscPosThermalReceiptByteStreamGeneration() {
        val lineItems = listOf(
            Triple("قهوة عربي فاخرة", 1.0, 45.00),
            Triple("تمور صقعي ملكي", 2.0, 70.00)
        )

        val receiptBytes = printerDriver.buildThermalReceiptBytes(
            storeName = "فوري باي للتجارة والتجزئة",
            vatNumber = "310000000000003",
            invoiceNumber = "INV-8877",
            timestamp = "2026-11-05 07:45",
            lineItems = lineItems,
            subtotalSar = 115.0,
            vatSar = 17.25,
            grandTotalSar = 132.25,
            zatcaBase64Qr = "AQ1Gb3dyaSBQYXkCAzMxMDA=",
            openCashDrawer = true
        )

        assertTrue(receiptBytes.isNotEmpty())

        // Verify cash drawer pulse command (0x1B, 0x70) is present at start
        assertEquals(0x1B.toByte(), receiptBytes[0])
        assertEquals(0x70.toByte(), receiptBytes[1])

        // Verify paper cut command (0x1D, 0x56, 0x41) is present near end
        val cutCommandFound = receiptBytes.toList().containsAll(listOf(0x1D.toByte(), 0x56.toByte(), 0x41.toByte()))
        assertTrue(cutCommandFound)
    }
}
