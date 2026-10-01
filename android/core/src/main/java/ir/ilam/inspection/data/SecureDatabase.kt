package ir.ilam.inspection.data

import android.content.Context
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * The SQLCipher open-helper every TavanKav database is built with. The
 * passphrase is created once per install and kept by [KeyStoreVault], so it
 * never exists as plain text on disk.
 */
object SecureDatabase {
    fun factory(context: Context): SupportOpenHelperFactory {
        System.loadLibrary("sqlcipher")
        return SupportOpenHelperFactory(KeyStoreVault(context).databasePassphrase())
    }
}
