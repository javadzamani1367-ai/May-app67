package ir.ilam.inspection.field.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ir.ilam.inspection.data.KeyStoreVault
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
        const val VERSION = 3
        private const val BASE = "field"

        @Volatile private var instance: FieldDatabase? = null

        /** 0.5: what the office says back about an item (reason, result) is kept for the item's page. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE field_item ADD COLUMN server_info TEXT")
            }
        }

        /** 0.7: when a position was recorded, and where the user stood then. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE field_item ADD COLUMN position_at INTEGER")
                db.execSQL("ALTER TABLE field_item ADD COLUMN observer_lat REAL")
                db.execSQL("ALTER TABLE field_item ADD COLUMN observer_lon REAL")
                db.execSQL("ALTER TABLE field_item ADD COLUMN observer_accuracy REAL")
            }
        }

        fun get(context: Context): FieldDatabase = instance ?: synchronized(this) {
            // One file per account: a person with an account in two areas sees each area's items apart.
            instance ?: Room.databaseBuilder(context.applicationContext, FieldDatabase::class.java,
                KeyStoreVault(context).databaseName(BASE))
                .openHelperFactory(SecureDatabase.factory(context))
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { instance = it }
        }
    }
}
