package ir.roozban.ai.runtime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.roozban.ai.core.EngineConfig
import ir.roozban.ai.core.EngineState
import ir.roozban.ai.core.GenerationRequest
import ir.roozban.ai.core.LlmEngine
import ir.roozban.ai.core.LlmException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * [LlmEngine] backed by [LlmService] in the `:ai` process. Binding starts that process; [unload]
 * unbinds so the system can reclaim its memory. If the process dies (e.g. killed for memory),
 * the state turns [EngineState.Failed] and the next [load] starts it again.
 */
@Singleton
class RemoteLlmEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : LlmEngine {
    private val _state = MutableStateFlow<EngineState>(EngineState.Unloaded)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var service: ILlmService? = null
    private var connection: ServiceConnection? = null

    /** Whether this device can run the native engine at all (binds the service to ask). */
    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock { runCatching { connect().available() }.getOrDefault(false) }
    }

    override suspend fun load(path: String, config: EngineConfig) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = _state.value
            if (current is EngineState.Ready && current.path == path && current.config == config && service != null) return@withLock
            _state.value = EngineState.Loading(path)
            val error = try {
                connect().load(path, config.contextTokens, config.threads, config.batchTokens)
            } catch (e: Exception) {
                e.message ?: "service error"
            }
            if (error != null) {
                _state.value = EngineState.Failed(error)
                throw LlmException(error)
            }
            _state.value = EngineState.Ready(path, config)
        }
    }

    override fun generate(request: GenerationRequest): Flow<String> = callbackFlow {
        val s = service
        if (s == null || _state.value !is EngineState.Ready) throw LlmException("no model loaded")
        val finished = AtomicBoolean(false)
        val callback = object : ILlmCallback.Stub() {
            override fun onPiece(piece: String) {
                trySend(piece)
            }

            override fun onDone(tokens: Int) {
                finished.set(true)
                channel.close()
            }

            override fun onError(message: String) {
                finished.set(true)
                channel.close(LlmException(message))
            }
        }
        val p = request.sampling
        try {
            s.generate(request.prompt, request.grammar.orEmpty(), p.temperature, p.topP, p.minP, p.seed, request.maxTokens, callback)
        } catch (e: Exception) {
            throw LlmException("service error", e)
        }
        awaitClose {
            if (!finished.get()) runCatching { s.cancel() }
        }
    }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.IO)

    override suspend fun countTokens(text: String): Int? = withContext(Dispatchers.IO) {
        runCatching { service?.countTokens(text) }.getOrNull()?.takeIf { it >= 0 }
    }

    override suspend fun unload() = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching { service?.unload() }
            connection?.let { runCatching { context.unbindService(it) } }
            connection = null
            service = null
            _state.value = EngineState.Unloaded
        }
    }

    private suspend fun connect(): ILlmService {
        service?.let { if (it.asBinder().isBinderAlive) return it }
        connection?.let { runCatching { context.unbindService(it) } }
        return suspendCancellableCoroutine { cont ->
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    val s = ILlmService.Stub.asInterface(binder)
                    service = s
                    if (cont.isActive) cont.resume(s)
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    service = null
                    if (_state.value !is EngineState.Unloaded) _state.value = EngineState.Failed("process died")
                }
            }
            connection = conn
            val bound = context.bindService(Intent(context, LlmService::class.java), conn, Context.BIND_AUTO_CREATE)
            if (!bound && cont.isActive) cont.resumeWith(Result.failure(LlmException("cannot start the model service")))
        }
    }
}
