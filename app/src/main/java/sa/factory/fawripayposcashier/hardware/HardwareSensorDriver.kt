package sa.factory.fawripayposcashier.hardware

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import sa.factory.fawripayposcashier.zatca.*
import kotlin.random.Random

data class HardwareTelemetryFrame(
    val peripheralName: String,
    val connectionState: String,
    val livePayload: String,
    val timestamp: Long = System.currentTimeMillis()
)

class HardwareSensorDriver {
    private val peripheral = "ESC/POS Thermal Receipt Printer + Barcode Scanner"
    private val printerDriver = EscPosThermalPrinterDriver()
    private val zatcaEngine = ZatcaPhase2FatooraEngine()

    fun getPrinterDriver(): EscPosThermalPrinterDriver = printerDriver
    fun getZatcaEngine(): ZatcaPhase2FatooraEngine = zatcaEngine

    fun observePeripheralStream(): Flow<HardwareTelemetryFrame> = flow {
        emit(HardwareTelemetryFrame(peripheral, "CONNECTING", "Handshake initializing..."))
        delay(300)
        emit(HardwareTelemetryFrame(peripheral, "ONLINE", "ESC/POS Thermal Receipt Printer + Barcode Scanner: ONLINE | READY"))

        var invoiceSeq = 2000
        while (true) {
            delay(1500)
            val jitter = Random.nextInt(1, 10)
            invoiceSeq++

            val items = listOf(
                InvoiceLineItem("SKU-01", "تمور خلاص فاخر 1 كجم", 45.0, 2.0),
                InvoiceLineItem("SKU-02", "قهوة سعودية بالهيل 500 جم", 60.0, 1.0)
            )

            val invoice = ZatcaTaxInvoice(
                invoiceNumber = "INV-2026-$invoiceSeq",
                invoiceUuid = "f47ac10b-58cc-4372-a567-0e02b2c3${invoiceSeq % 1000}",
                sellerName = "شركة فوري باي لتقنية نقاط البيع",
                vatRegistrationNumber = "310123456700003",
                issueTimestampIso8601 = "2026-11-05T07:00:00Z",
                items = items
            )

            val tlv = zatcaEngine.generateZatcaTlvQrPayload(invoice)

            // Simulate barcode scan with GS1 Saudi Arabia prefix
            val rawScan = "6281000123455\r\n".toByteArray(Charsets.US_ASCII)
            val scanResult = printerDriver.ingestScannerBuffer(rawScan)

            emit(HardwareTelemetryFrame(
                peripheral,
                "ACTIVE_STREAMING",
                "Printer: READY (80mm) | CashDrawer: PULSED | LastScan: ${scanResult.symbology} (${scanResult.rawPayload}) | ZATCA QR TLV: ${tlv.rawTlvBytes.size}B | SHA256: ${tlv.invoiceSha256DigestHex.take(8)} (Jitter: +${jitter}ms)"
            ))
        }
    }
}
