package sa.factory.fawripayposcashier.domain.repository

import sa.factory.fawripayposcashier.model.FawriPayItem
import sa.factory.fawripayposcashier.model.FawriPayOrder
import sa.factory.fawripayposcashier.model.FawriPayTelemetry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface FawriPayRepository {
    val catalogFlow: Flow<List<FawriPayItem>>
    val activeCart: StateFlow<List<FawriPayItem>>
    val telemetryFlow: Flow<FawriPayTelemetry>
    
    fun addToCart(item: FawriPayItem)
    fun removeFromCart(item: FawriPayItem)
    fun clearCart()
    suspend fun checkout(paymentMethod: String): FawriPayOrder
    suspend fun refreshCatalog()
}
