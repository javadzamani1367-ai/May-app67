package ir.ilam.inspection.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ir.ilam.inspection.data.KeyStoreVault
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Bumped only together with `windows/SCHEMA.md`; the sync handshake refuses to
 * talk to an archive built against a different version.
 */
const val SCHEMA_VERSION = 2

@Database(
    entities = [
        ReportEntity::class,
        DeviceEntity::class,
        AttendeeEntity::class,
        MediaEntity::class,
        AttachmentEntity::class,
        DispatchEntity::class,
        SettingEntity::class
    ],
    version = SCHEMA_VERSION,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun reportDao(): ReportDao
    abstract fun deviceDao(): DeviceDao
    abstract fun attendeeDao(): AttendeeDao
    abstract fun mediaDao(): MediaDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun dispatchDao(): DispatchDao
    abstract fun settingDao(): SettingDao

    companion object {
        private const val DB_NAME = "inspection.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

        /**
         * Version 2 carries the per-phase measurement and the coded meter
         * answers. Added, never dropped: an expert's phone holds the only copy
         * of a case until it is synced, so a destructive migration would throw
         * away field work.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "tap_point INTEGER",
                    "phase_type INTEGER",
                    "amperage_r REAL",
                    "amperage_s REAL",
                    "amperage_t REAL",
                    "voltage_r REAL",
                    "voltage_s REAL",
                    "voltage_t REAL",
                    "total_watt REAL",
                    "tariff_type INTEGER",
                    "meter_type INTEGER",
                    "seal_external INTEGER",
                    "seal_external_serial TEXT",
                    "seal_internal INTEGER",
                    "meter_appearance_ok INTEGER",
                    "meter_tampered INTEGER"
                ).forEach { column ->
                    db.execSQL("ALTER TABLE reports ADD COLUMN $column")
                }
            }
        }

        private fun build(context: Context): AppDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = KeyStoreVault(context).databasePassphrase()
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
