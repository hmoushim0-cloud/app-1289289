package sa.factory.fawripayposcashier.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import sa.factory.fawripayposcashier.model.FawriPayItem

@Entity(tableName = "pos_cashier_items_cache")
data class FawriPayEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val priceSar: Double,
    val badge: String,
    val category: String,
    val status: String
) {
    fun toDomain(): FawriPayItem = FawriPayItem(
        id = id,
        title = title,
        description = description,
        priceSar = priceSar,
        badge = badge,
        category = category,
        status = status
    )
}
