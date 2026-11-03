package sa.factory.fawripayposcashier.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [FawriPayEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pos_cashierDao(): FawriPayDao
}
