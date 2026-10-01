package ir.ilam.inspection.field.data

/**
 * The field app's vocabulary, mirroring the server's (`api/lib/Field.php`):
 * the codes travel over the wire, so both sides must agree on every number.
 */

/** What the manager has turned on for this account, as bits. */
object Permission {
    const val INSPECT = 1
    const val REPORT = 2

    fun has(granted: Int, permission: Int): Boolean = (granted and permission) != 0
}

/** The four kinds of item; [permission] is the bit an account needs to make one. */
enum class FieldKind(val code: Int, val permission: Int) {
    CRYPTO(1, Permission.REPORT),
    ILLEGAL(2, Permission.REPORT),
    THERMAL(3, Permission.INSPECT),
    FEEDER(4, Permission.INSPECT);

    companion object {
        fun of(code: Int): FieldKind = entries.firstOrNull { it.code == code } ?: CRYPTO
    }
}

/**
 * Where an item stands on this phone. A draft is still being filled in and is
 * never sent; the user finishes it and it joins the queue.
 */
enum class SyncState(val code: Int) {
    DRAFT(0), PENDING(1), SENT(2), ERROR(3);

    companion object {
        fun of(code: Int): SyncState = entries.firstOrNull { it.code == code } ?: PENDING
    }
}

/** Where an item stands on the server, once it has reached it. */
enum class ServerStatus(val code: Int) {
    REGISTERED(0), REVIEWING(1), REFERRED(2), RESULT(3), CLOSED(4), REJECTED(5), REVISIT(6);

    companion object {
        fun of(code: Int?): ServerStatus? = entries.firstOrNull { it.code == code }
    }
}

/** What a file is to its item, and the type it travels as. */
object FileRole {
    const val PHOTO = 0
    const val PHOTO_STAMPED = 1
    const val VIDEO = 2
    const val AUDIO = 3
    const val THERMAL = 4
    const val THERMAL_EXIF = 5
    const val SIDECAR = 6
    const val TRACK = 7
}
