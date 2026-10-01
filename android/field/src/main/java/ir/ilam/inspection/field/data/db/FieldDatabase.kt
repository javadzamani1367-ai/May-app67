package ir.ilam.inspection.field.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import ir.ilam.inspection.data.SecureDatabase

/**
 * The field app's own encrypted database. It shares nothing with the
 * inspection apps' schema (`SCHEMA_VERSION` there is untouched): these tables
 * travel only to the server, whose `field_items` and `field_files` they mirror.
 */
@Database(
    entities = [FieldItemEntity::class, FieldFileEntity::class],
    version = FieldDatabase.VERSION,
    exportSchema = true
)
abstract class FieldDatabase : RoomDatabase() {
    abstract fun dao(): FieldDao

    companion object {
        const val VERSION = 1
        private const val NAME = "field.db"

        @Volatile private var instance: FieldDatabase? = null

        fun get(context: Context): FieldDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, FieldDatabase::class.java, NAME)
                .openHelperFactory(SecureDatabase.factory(context))
                .build()
                .also { instance = it }
        }
    }
}
