package sa.factory.fawripayposcashier.data.remote

import sa.factory.fawripayposcashier.model.FawriPayItem
import sa.factory.fawripayposcashier.model.FawriPayOrder

interface FawriPayApiService {
    suspend fun fetchCatalog(): List<FawriPayItem>
    suspend fun submitOrder(order: FawriPayOrder): Boolean
}
