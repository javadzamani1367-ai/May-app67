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
const val SCHEMA_VERSION = 4

/**
 * Room's own version. It moves ahead of [SCHEMA_VERSION] whenever the phone
 * gains a table the archive has no business knowing about — a local typing
 * convenience must not make an up-to-date archive look incompatible.
 */
const val DATABASE_VERSION = 7

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
        UserEntity::class,
        ServerSyncEntity::class
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
    abstract fun serverSyncDao(): ServerSyncDao

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

        /**
         * Version 3 of the shared schema: a dispatch now records how it left,
         * by when the unit has to answer, and what came back; and a case
         * carries the manager's decision, because it cannot be filed for good
         * until that decision exists.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "ALTER TABLE dispatches ADD COLUMN channel INTEGER NOT NULL DEFAULT 0",
                    "ALTER TABLE dispatches ADD COLUMN deadline_at INTEGER",
                    "ALTER TABLE dispatches ADD COLUMN status INTEGER NOT NULL DEFAULT 0",
                    "ALTER TABLE dispatches ADD COLUMN answered_at INTEGER",
                    "ALTER TABLE dispatches ADD COLUMN answer TEXT",
                    "ALTER TABLE reports ADD COLUMN approval_state INTEGER NOT NULL DEFAULT 0",
                    "ALTER TABLE reports ADD COLUMN approval_comment TEXT",
                    "ALTER TABLE reports ADD COLUMN approval_at INTEGER"
                ).forEach { db.execSQL(it) }
            }
        }

        /**
         * When this phone last sent each case to the server — which is not the
         * same thing as `synced_at`, the watermark the Windows archive pulls
         * against. Phone only, so the shared schema version does not move.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS server_sync (" +
                        "report_id TEXT NOT NULL PRIMARY KEY, " +
                        "sent_at INTEGER NOT NULL)"
                )
            }
        }

        /**
         * Version 4 of the shared schema: the case carries its area code.
         *
         * Cases that already exist are not left blank. The area is in their
         * tracking code — `M-401-050614-482917` — so it is read back out of
         * the segment between the first two dashes, and only when that segment
         * is entirely digits. A manually entered code from the Soragh system
         * has no such segment, and is left null rather than guessed at.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reports ADD COLUMN area_code TEXT")

                val code = "COALESCE(NULLIF(tracking_code, ''), NULLIF(temp_code, ''))"
                val tail = "substr($code, instr($code, '-') + 1)"
                db.execSQL(
                    "UPDATE reports SET area_code = " +
                        "substr($tail, 1, instr($tail, '-') - 1) " +
                        "WHERE $code IS NOT NULL " +
                        "AND instr($code, '-') > 0 " +
                        "AND instr($tail, '-') > 1"
                )
                // Anything that came out with a non digit in it was not an
                // area code to begin with.
                db.execSQL(
                    "UPDATE reports SET area_code = NULL " +
                        "WHERE area_code IS NOT NULL AND area_code GLOB '*[^0-9]*'"
                )
            }
        }

        private fun build(context: Context): AppDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = KeyStoreVault(context).databasePassphrase()
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .openHelperFactory(factory)
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7
                )
                .build()
        }
    }
}
