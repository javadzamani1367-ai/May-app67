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
 * The schema the phone and the Windows archive share. Bumped only together
 * with `windows/SCHEMA.md`; the sync handshake refuses to talk to an archive
 * built against a different version.
 */
const val SCHEMA_VERSION = 2

/**
 * Room's own version. It moves ahead of [SCHEMA_VERSION] whenever the phone
 * gains a table the archive has no business knowing about — a local typing
 * convenience must not make an up-to-date archive look incompatible.
 */
const val DATABASE_VERSION = 4

@Database(
    entities = [
        ReportEntity::class,
        DeviceEntity::class,
        AttendeeEntity::class,
        MediaEntity::class,
        AttachmentEntity::class,
        DispatchEntity::class,
        SettingEntity::class,
        SnippetEntity::class,
        UserEntity::class
    ],
    version = DATABASE_VERSION,
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
    abstract fun snippetDao(): SnippetDao
    abstract fun userDao(): UserDao

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

        /** The saved phrases behind the star on a text field: phone only. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS snippets (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "field_key TEXT NOT NULL, " +
                        "text TEXT NOT NULL, " +
                        "created_at INTEGER NOT NULL, " +
                        "used_at INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_snippets_field_key_text " +
                        "ON snippets (field_key, text)"
                )
            }
        }

        /** The manager's register of experts and the installations they use. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS users (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "user_code TEXT NOT NULL, " +
                        "full_name TEXT NOT NULL, " +
                        "county TEXT, " +
                        "phone TEXT, " +
                        "device_code TEXT, " +
                        "active INTEGER NOT NULL, " +
                        "created_at INTEGER NOT NULL, " +
                        "updated_at INTEGER NOT NULL, " +
                        "note TEXT)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_users_user_code ON users (user_code)"
                )
            }
        }

        private fun build(context: Context): AppDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = KeyStoreVault(context).databasePassphrase()
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
        }
    }
}
