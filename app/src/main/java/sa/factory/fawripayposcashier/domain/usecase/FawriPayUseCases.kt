package sa.factory.fawripayposcashier.domain.usecase

import sa.factory.fawripayposcashier.domain.repository.FawriPayRepository
import sa.factory.fawripayposcashier.model.FawriPayItem
import sa.factory.fawripayposcashier.model.FawriPayOrder
import sa.factory.fawripayposcashier.model.FawriPayTelemetry
import kotlinx.coroutines.flow.Flow

class GetCatalogUseCase(private val repository: FawriPayRepository) {
    operator fun invoke(): Flow<List<FawriPayItem>> = repository.catalogFlow
}

class ManageCartUseCase(private val repository: FawriPayRepository) {
    val cart = repository.activeCart
    fun add(item: FawriPayItem) = repository.addToCart(item)
    fun remove(item: FawriPayItem) = repository.removeFromCart(item)
    fun clear() = repository.clearCart()
}

class ProcessCheckoutUseCase(private val repository: FawriPayRepository) {
    suspend operator fun invoke(paymentMethod: String = "Mada Contactless EMV"): FawriPayOrder =
        repository.checkout(paymentMethod)
}

class ObserveHardwareTelemetryUseCase(private val repository: FawriPayRepository) {
    operator fun invoke(): Flow<FawriPayTelemetry> = repository.telemetryFlow
}
