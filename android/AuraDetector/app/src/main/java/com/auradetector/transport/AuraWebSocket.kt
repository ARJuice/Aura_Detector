package com.auradetector.transport

import android.os.SystemClock
import android.util.Base64
import com.auradetector.data.ServerConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

enum class VisionLinkState { CONNECTING, READY, DEGRADED, OFFLINE }

data class TransportStatus(
    val state: VisionLinkState = VisionLinkState.CONNECTING,
    val detail: String = "Connecting…",
    val lastFrameId: Long? = null,
    val latencyMs: Long? = null
)

/**
 * A single-in-flight frame sender. CameraX retains the newest camera frame while
 * this transport waits for an acknowledgement, avoiding a latency-growing queue.
 */
class AuraWebSocket(private val config: ServerConfig) : Closeable {
    private companion object {
        const val MIN_FRAME_INTERVAL_MS = 67L // 15 FPS maximum for the LAN prototype.
    }
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
    private val frameId = AtomicLong(0)
    private val lastAcceptedFrameId = AtomicLong(-1)
    private val awaitingFrameState = AtomicBoolean(false)
    private val helloAcknowledged = AtomicBoolean(false)
    private val frameSentAtMs = AtomicLong(0)
    private val lastFrameSentAtMs = AtomicLong(0)
    private var webSocket: WebSocket? = null

    private val _status = MutableStateFlow(TransportStatus())
    val status = _status.asStateFlow()

    fun connect() {
        _status.value = TransportStatus(VisionLinkState.CONNECTING, "Connecting…")
        webSocket = client.newWebSocket(
            Request.Builder().url(config.wsUrl).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    val hello = JSONObject()
                        .put("type", "hello")
                        .put("version", 1)
                        .put("token", config.token)
                        .put("clientId", UUID.randomUUID().toString())
                    webSocket.send(hello.toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleMessage(text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    _status.value = TransportStatus(VisionLinkState.OFFLINE, "Socket closing: $reason")
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    _status.value = TransportStatus(VisionLinkState.OFFLINE, "Socket closed: $reason")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    awaitingFrameState.set(false)
                    _status.value = TransportStatus(VisionLinkState.OFFLINE, t.message ?: "Connection failed")
                }
            }
        )
    }

    fun sendFrame(jpeg: ByteArray, width: Int, height: Int): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (!helloAcknowledged.get() || now - lastFrameSentAtMs.get() < MIN_FRAME_INTERVAL_MS) return false
        if (!awaitingFrameState.compareAndSet(false, true)) return false

        val id = frameId.incrementAndGet()
        frameSentAtMs.set(now)
        lastFrameSentAtMs.set(now)
        val message = JSONObject()
            .put("type", "frame")
            .put("version", 1)
            .put("frameId", id)
            .put("capturedAtMs", System.currentTimeMillis())
            .put("width", width)
            .put("height", height)
            .put("jpeg", Base64.encodeToString(jpeg, Base64.NO_WRAP))

        val socket = webSocket
        if (socket == null || !socket.send(message.toString())) {
            awaitingFrameState.set(false)
            _status.value = TransportStatus(VisionLinkState.OFFLINE, "Frame send failed")
            return false
        }
        return true
    }

    private fun handleMessage(text: String) {
        val message = runCatching { JSONObject(text) }.getOrElse {
            _status.value = TransportStatus(VisionLinkState.DEGRADED, "Invalid server response")
            return
        }
        when (message.optString("type")) {
            "hello_ack" -> {
                helloAcknowledged.set(true)
                _status.value = TransportStatus(VisionLinkState.READY, "LINK: OK")
            }
            "frame_state" -> {
                val id = message.optLong("frameId", -1)
                if (id > lastAcceptedFrameId.get()) {
                    lastAcceptedFrameId.set(id)
                    awaitingFrameState.set(false)
                    _status.value = TransportStatus(
                        state = VisionLinkState.READY,
                        detail = "LINK: OK",
                        lastFrameId = id,
                        latencyMs = SystemClock.elapsedRealtime() - frameSentAtMs.get()
                    )
                }
            }
            "error" -> {
                awaitingFrameState.set(false)
                val recoverable = message.optBoolean("recoverable", false)
                _status.value = TransportStatus(
                    if (recoverable) VisionLinkState.DEGRADED else VisionLinkState.OFFLINE,
                    message.optString("message", "Server error")
                )
            }
            else -> _status.value = TransportStatus(VisionLinkState.DEGRADED, "Unknown server message")
        }
    }

    override fun close() {
        webSocket?.close(1000, "Scanner closed")
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
