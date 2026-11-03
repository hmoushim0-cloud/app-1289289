package sa.factory.fawripayposcashier.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FawriPayDao {
    @Query("SELECT * FROM pos_cashier_items_cache ORDER BY id ASC")
    fun observeAllItems(): Flow<List<FawriPayEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<FawriPayEntity>)

    @Query("DELETE FROM pos_cashier_items_cache")
    suspend fun clearAll()
}
