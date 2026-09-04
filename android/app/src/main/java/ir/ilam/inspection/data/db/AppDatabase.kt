package ir.ilam.inspection.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import ir.ilam.inspection.data.KeyStoreVault
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Bumped only together with `windows/SCHEMA.md`; the sync handshake refuses to
 * talk to an archive built against a different version.
 */
const val SCHEMA_VERSION = 1

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
        const val SCHEMA_VERSION = ir.ilam.inspection.data.db.SCHEMA_VERSION
        private const val DB_NAME = "inspection.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

        private fun build(context: Context): AppDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = KeyStoreVault(context).databasePassphrase()
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .openHelperFactory(factory)
                .build()
        }
    }
}
