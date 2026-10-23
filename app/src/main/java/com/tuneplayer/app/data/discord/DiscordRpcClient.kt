package com.tuneplayer.app.data.discord

import com.tuneplayer.app.log.LogCatcher
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal Discord gateway client for "Listening to TunePlayer" presence.
 *
 * A Discord application id is deliberately not required: the activity carries
 * its own title, artist, and timestamps, while artwork and buttons (which do
 * need a registered application) are omitted. This keeps the feature usable
 * for anyone who supplies a user token instead of tying the app to an identity
 * token it does not own.
 *
 * The token is only kept in memory here and in local preferences by the app.
 * It is never logged.
 */
class DiscordRpcClient {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = HttpClient(OkHttp) {
        install(WebSockets)
    }
    private val sendMutex = Mutex()

    private var loopJob: Job? = null

    @Volatile
    private var heartbeatJob: Job? = null

    @Volatile
    private var token: String = ""

    @Volatile
    private var active: Boolean = false

    @Volatile
    private var session: DefaultClientWebSocketSession? = null

    @Volatile
    private var ready: Boolean = false

    @Volatile
    private var sequence: Int = 0

    @Volatile
    private var heartbeatAcked: Boolean = true

    @Volatile
    private var heartbeatIntervalMs: Long = DEFAULT_HEARTBEAT_MS

    @Volatile
    private var lastPresence: String? = null

    /** Guards against a cancelled socket's cleanup touching a newer session. */
    @Volatile
    private var generation: Int = 0

    private val _connected = MutableStateFlow(false)
    val connected = _connected.asStateFlow()

    /** Starts or replaces the gateway session. Blank tokens are ignored. */
    @Synchronized
    fun start(rawToken: String) {
        val normalized = rawToken.trim().removePrefix("Bearer ").trim()
        if (normalized.isEmpty()) return
        if (active && token == normalized && loopJob?.isActive == true) return
        stopLocked()
        token = normalized
        active = true
        generation++
        val sessionGeneration = generation
        loopJob = scope.launch { runLoop(sessionGeneration) }
    }

    @Synchronized
    fun stop() {
        stopLocked()
    }

    private fun stopLocked() {
        active = false
        generation++
        loopJob?.cancel()
        loopJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        session?.cancel()
        session = null
        ready = false
        sequence = 0
        lastPresence = null
        _connected.value = false
    }

    /**
     * Publishes an activity, or clears the card when [activity] is null.
     *
     * The payload is remembered even when the socket is down. Discord drops
     * presence with its session, so READY has to replay the latest one.
     */
    fun setActivity(activity: JSONObject?) {
        if (!active) return
        lastPresence = if (activity == null) {
            emptyPresence().toString()
        } else {
            JSONObject().apply {
                put("status", "online")
                put("since", JSONObject.NULL)
                put("afk", false)
                put("activities", JSONArray().put(activity))
            }.toString()
        }
        scope.launch { sendPresenceIfReady() }
    }

    private suspend fun runLoop(sessionGeneration: Int) {
        var backoff = INITIAL_RECONNECT_MS
        while (active && generation == sessionGeneration && currentCoroutineContext().isActive) {
            try {
                val socket = httpClient.webSocketSession(GATEWAY_URL) {
                    header("User-Agent", USER_AGENT)
                    header("Accept-Language", "en-US")
                }
                if (!active || generation != sessionGeneration) {
                    socket.cancel()
                    break
                }
                session = socket
                ready = false
                sequence = 0
                heartbeatAcked = true
                backoff = INITIAL_RECONNECT_MS

                for (frame in socket.incoming) {
                    if (!active || generation != sessionGeneration) break
                    if (frame is Frame.Text) {
                        handleFrame(frame.readText())
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogCatcher.w(TAG, "gateway disconnected: ${error.message}")
            } finally {
                if (generation == sessionGeneration) {
                    ready = false
                    _connected.value = false
                    heartbeatJob?.cancel()
                    heartbeatJob = null
                    session = null
                }
            }
            if (!active || generation != sessionGeneration) break
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(MAX_RECONNECT_MS)
        }
    }

    private suspend fun handleFrame(raw: String) {
        val payload = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val op = payload.optInt("op", -1)
        if (payload.has("s") && !payload.isNull("s")) {
            val incoming = payload.optInt("s", 0)
            if (incoming > sequence) sequence = incoming
        }

        when (op) {
            OP_HELLO -> {
                heartbeatIntervalMs = payload.optJSONObject("d")
                    ?.optLong("heartbeat_interval", DEFAULT_HEARTBEAT_MS)
                    ?.coerceAtLeast(MIN_HEARTBEAT_MS)
                    ?: DEFAULT_HEARTBEAT_MS
                startHeartbeat()
                sendIdentify()
            }

            OP_HEARTBEAT -> sendHeartbeat()

            OP_HEARTBEAT_ACK -> heartbeatAcked = true

            OP_RECONNECT, OP_INVALID_SESSION -> {
                ready = false
                _connected.value = false
                session?.cancel()
            }

            OP_DISPATCH -> {
                if (payload.optString("t") == "READY" || payload.optString("t") == "RESUMED") {
                    ready = true
                    heartbeatAcked = true
                    _connected.value = true
                    sendPresenceIfReady()
                }
            }
        }
    }

    private suspend fun sendIdentify() {
        val presence = emptyPresence()
        val data = JSONObject().apply {
            put("token", token)
            put("capabilities", 16381)
            put("compress", false)
            put("properties", JSONObject().apply {
                put("os", "Android")
                put("browser", "TunePlayer")
                put("device", "Android")
            })
            put("presence", presence)
        }
        sendOp(OP_IDENTIFY, data)
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            delay(heartbeatIntervalMs)
            while (isActive) {
                if (!heartbeatAcked) {
                    LogCatcher.w(TAG, "heartbeat timed out")
                    session?.cancel()
                    return@launch
                }
                heartbeatAcked = false
                sendHeartbeat()
                delay(heartbeatIntervalMs)
            }
        }
    }

    private suspend fun sendHeartbeat() {
        val seq = sequence.takeIf { it > 0 } ?: JSONObject.NULL
        sendOp(OP_HEARTBEAT, seq)
    }

    private suspend fun sendPresenceIfReady() {
        if (!ready) return
        val presence = lastPresence ?: return
        val body = runCatching { JSONObject(presence) }.getOrNull() ?: return
        sendOp(OP_PRESENCE_UPDATE, body)
    }

    private suspend fun sendOp(op: Int, data: Any?) {
        val socket = session ?: return
        val payload = JSONObject().apply {
            put("op", op)
            put("d", data ?: JSONObject.NULL)
        }
        sendMutex.withLock {
            runCatching { socket.send(Frame.Text(payload.toString())) }
                .onFailure { LogCatcher.w(TAG, "gateway send failed: ${it.message}") }
        }
    }

    private fun emptyPresence(): JSONObject = JSONObject().apply {
        put("activities", JSONArray())
        put("afk", false)
        put("since", JSONObject.NULL)
        put("status", "online")
    }

    private companion object {
        const val TAG = "DiscordRpc"
        const val GATEWAY_URL = "wss://gateway.discord.gg/?v=9&encoding=json"
        const val USER_AGENT = "Discord-Android/314013;RNA"
        const val OP_DISPATCH = 0
        const val OP_HEARTBEAT = 1
        const val OP_IDENTIFY = 2
        const val OP_PRESENCE_UPDATE = 3
        const val OP_RECONNECT = 7
        const val OP_INVALID_SESSION = 9
        const val OP_HELLO = 10
        const val OP_HEARTBEAT_ACK = 11
        const val DEFAULT_HEARTBEAT_MS = 41_250L
        const val MIN_HEARTBEAT_MS = 5_000L
        const val INITIAL_RECONNECT_MS = 3_000L
        const val MAX_RECONNECT_MS = 60_000L
    }
}
