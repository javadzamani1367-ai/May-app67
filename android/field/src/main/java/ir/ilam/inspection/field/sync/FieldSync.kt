package ir.ilam.inspection.field.sync

import ir.ilam.inspection.field.data.FieldAccount
import ir.ilam.inspection.field.data.FieldPrefs
import ir.ilam.inspection.field.data.SyncState
import ir.ilam.inspection.field.data.db.FieldDao
import ir.ilam.inspection.field.data.db.FieldFileEntity
import ir.ilam.inspection.field.data.db.FieldItemEntity
import ir.ilam.inspection.sync.ApiResult
import ir.ilam.inspection.sync.ServerApi
import ir.ilam.inspection.util.FileStore
import java.io.RandomAccessFile

/** How a sync run ended, for the screen that started it. */
enum class SyncOutcome { DONE, OFFLINE, SIGN_IN_NEEDED, NOT_SIGNED_IN }

/**
 * Sends the queue and brings back what the office did with it.
 *
 * Items first, then their files: an item on the server with its photos still
 * on the way is better than nothing, and it gets its tracking code at once.
 * Every file resumes from wherever the server says it got to. Nothing here
 * throws away local data except, when the manager has asked for it, the local
 * copy of a file the server has confirmed byte for byte.
 */
class FieldSync(
    private val dao: FieldDao,
    private val prefs: FieldPrefs,
    private val account: FieldAccount,
    private val files: FileStore
) {

    suspend fun run(): SyncOutcome {
        val token = account.token() ?: return SyncOutcome.NOT_SIGNED_IN
        val api = ServerApi(prefs.serverAddress)

        when (val me = api.fieldMe(token)) {
            is ApiResult.Ok -> with(me.value) {
                prefs.fullName = fullName
                prefs.permissions = permissions
                prefs.ampTolerancePct = ampTolerancePct
                prefs.ampToleranceMinA = ampToleranceMinA
                prefs.purgeAfterSync = purgeAfterSync
                prefs.sessionExpired = false
            }
            is ApiResult.Refused -> return refused(me.code)
            ApiResult.Unreachable -> return SyncOutcome.OFFLINE
        }

        for (item in dao.unsent()) {
            val outcome = send(api, token, item)
            if (outcome != SyncOutcome.DONE) return outcome
        }
        pull(api, token)?.let { return it }
        prefs.lastSyncAt = System.currentTimeMillis()
        return SyncOutcome.DONE
    }

    private suspend fun send(api: ServerApi, token: String, item: FieldItemEntity): SyncOutcome {
        val itemFiles = dao.files(item.id)
        val pushed = when (val result = api.pushFieldItem(token, FieldWire.item(item, account.deviceCode), FieldWire.files(itemFiles))) {
            is ApiResult.Ok -> result.value
            is ApiResult.Refused -> {
                if (result.code in SESSION_CODES) return refused(result.code)
                // The server judged the item itself — missing description, no
                // permission. Retrying unchanged would fail the same way, so it
                // waits, marked, with the server's own words.
                dao.setSyncState(item.id, SyncState.ERROR.code, result.message, null)
                return SyncOutcome.DONE
            }
            ApiResult.Unreachable -> return SyncOutcome.OFFLINE
        }
        dao.setServerState(item.id, pushed.trackingCode, pushed.status)

        for (file in itemFiles.filter { it.id in pushed.missingFiles }) {
            // DONE here means "this file cannot go now, carry on with the rest";
            // OFFLINE or a lost session stops the whole run.
            val stopped = upload(api, token, file)
            if (stopped != null && stopped != SyncOutcome.DONE) return stopped
        }
        val stillMissing = dao.files(item.id).any { !it.complete }
        if (stillMissing) {
            dao.setSyncState(item.id, SyncState.ERROR.code, ERROR_FILE, null)
        } else {
            dao.setSyncState(item.id, SyncState.SENT.code, null, System.currentTimeMillis())
            if (prefs.purgeAfterSync) purge(item.id)
        }
        return SyncOutcome.DONE
    }

    /** Null when the file arrived; otherwise why the run has to stop or move on. */
    private suspend fun upload(api: ServerApi, token: String, file: FieldFileEntity): SyncOutcome? {
        val local = file.path?.let { files.resolve(it) }?.takeIf { it.exists() } ?: return SyncOutcome.DONE
        var received = when (val state = api.uploadFieldChunk(token, file.id, 0, ByteArray(0))) {
            is ApiResult.Ok -> state.value.received
            is ApiResult.Refused -> return if (state.code in SESSION_CODES) refused(state.code) else SyncOutcome.DONE
            ApiResult.Unreachable -> return SyncOutcome.OFFLINE
        }
        var restarts = 0
        RandomAccessFile(local, "r").use { source ->
            while (true) {
                val range = ChunkPlan.next(file.size, received) ?: break
                val bytes = ByteArray((range.last - range.first + 1).toInt())
                source.seek(range.first)
                source.readFully(bytes)
                when (val result = api.uploadFieldChunk(token, file.id, range.first, bytes)) {
                    is ApiResult.Ok -> {
                        val state = result.value
                        if (state.complete) {
                            dao.setUploaded(file.id, file.size, true)
                            return null
                        }
                        // A file that does not hash the same arrived damaged in
                        // transit. Once more from the start; twice is not transit.
                        if (state.hashMismatch && ++restarts > 1) return SyncOutcome.DONE
                        received = state.received
                        dao.setUploaded(file.id, received, false)
                    }
                    is ApiResult.Refused -> return if (result.code in SESSION_CODES) refused(result.code) else SyncOutcome.DONE
                    ApiResult.Unreachable -> return SyncOutcome.OFFLINE
                }
            }
        }
        return null
    }

    /** Codes and statuses the office has set since the last pull. */
    private suspend fun pull(api: ServerApi, token: String): SyncOutcome? {
        when (val page = api.pullFieldItems(token, prefs.lastPull)) {
            is ApiResult.Ok -> {
                page.value.items.forEach { dao.setServerState(it.id, it.trackingCode, it.status) }
                prefs.lastPull = page.value.serverTime
            }
            is ApiResult.Refused -> return refused(page.code)
            ApiResult.Unreachable -> return SyncOutcome.OFFLINE
        }
        return null
    }

    private suspend fun purge(itemId: String) {
        dao.files(itemId).filter { it.complete && it.path != null }.forEach { file ->
            files.deleteQuietly(file.path!!)
            dao.forgetPath(file.id)
        }
    }

    private fun refused(code: String): SyncOutcome {
        if (code in SESSION_CODES) {
            prefs.sessionExpired = true
            return SyncOutcome.SIGN_IN_NEEDED
        }
        return SyncOutcome.DONE
    }

    private companion object {
        val SESSION_CODES = setOf("bad_token", "no_token")
        const val ERROR_FILE = "file_incomplete"
    }
}
