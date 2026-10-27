package ir.ilam.inspection.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// The later migrations, kept beside AppDatabase so that file stays readable.

/** Schema 7: crypto-mining visit or illegal-power inspection. */
internal val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reports ADD COLUMN case_kind INTEGER NOT NULL DEFAULT 0")
    }
}

/** People the expert proposes for a centre's rewards. Phone only. */
internal val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `reward_people` (" +
                "`id` TEXT NOT NULL, `report_id` TEXT NOT NULL, `kind` TEXT NOT NULL, `full_name` TEXT NOT NULL, " +
                "`post` TEXT, `org` TEXT, `action` TEXT, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`report_id`) REFERENCES `reports`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reward_people_report_id` ON `reward_people` (`report_id`)")
    }
}

/** Schema 6: the account holder's name, for the miner device registry. */
internal val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reports ADD COLUMN subscriber_name TEXT")
    }
}
