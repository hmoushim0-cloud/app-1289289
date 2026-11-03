package sa.factory.fawripayposcashier.data.repository

import sa.factory.fawripayposcashier.domain.repository.FawriPayRepository
import sa.factory.fawripayposcashier.hardware.HardwareSensorDriver
import sa.factory.fawripayposcashier.model.FawriPayItem
import sa.factory.fawripayposcashier.model.FawriPayOrder
import sa.factory.fawripayposcashier.model.FawriPayTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FawriPayRepositoryImpl(
    private val sensorDriver: HardwareSensorDriver
) : FawriPayRepository {

    private val _catalog = MutableStateFlow<List<FawriPayItem>>(listOf(
        FawriPayItem("1", "الباقة التنفيذية المعتمدة", "تكامل مباشر مع ESC/POS Thermal Receipt Printer + Barcode Scanner", 180.0, "معتمد", "الخدمات الرئيسية"),
        FawriPayItem("2", "خدمة المعالجة الفورية", "أداء أصيل بسرعة 60 إطار في الثانية", 95.0, "فوري", "المراقبة الحية"),
        FawriPayItem("3", "باقة الصيانة والدعم السحابي", "تشفير كامل للبيانات المحلية ومزامنة آمنة", 250.0, "شامل", "التقارير الضريبية")
    ))
    override val catalogFlow: Flow<List<FawriPayItem>> = _catalog.asStateFlow()

    private val _cart = MutableStateFlow<List<FawriPayItem>>(emptyList())
    override val activeCart: StateFlow<List<FawriPayItem>> = _cart.asStateFlow()

    override val telemetryFlow: Flow<FawriPayTelemetry> = sensorDriver.observePeripheralStream()
        .map { frame ->
            FawriPayTelemetry(
                peripheralName = frame.peripheralName,
                status = frame.connectionState,
                liveReading = frame.livePayload,
                batteryPct = 96,
                signalDbm = -54
            )
        }

    override fun addToCart(item: FawriPayItem) {
        val current = _cart.value.toMutableList()
        current.add(item)
        _cart.value = current
    }

    override fun removeFromCart(item: FawriPayItem) {
        val current = _cart.value.toMutableList()
        current.remove(item)
        _cart.value = current
    }

    override fun clearCart() {
        _cart.value = emptyList()
    }

    override suspend fun checkout(paymentMethod: String): FawriPayOrder {
        val currentItems = _cart.value.toList()
        val subtotal = currentItems.sumOf { it.priceSar }
        val vat = subtotal * 0.15
        val total = subtotal + vat
        val order = FawriPayOrder(
            orderId = "ORD-${System.currentTimeMillis() % 100000}",
            items = currentItems,
            subtotalSar = subtotal,
            vatSar = vat,
            totalSar = total,
            paymentMethod = paymentMethod
        )
        clearCart()
        return order
    }

    override suspend fun refreshCatalog() {
        // Offline-first sync logic
    }
}
