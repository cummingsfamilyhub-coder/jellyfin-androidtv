package org.jellyfin.vesper.sendspin

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import io.ktor.websocket.Frame as KtorFrame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import java.io.Closeable
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Music Assistant answered a command with an error. The socket itself is healthy,
 * so this must not trigger a reconnect, and the message is MA's own details.
 */
class MusicAssistantCommandException(message: String) : Exception(message)

/**
 * Persistent Music Assistant command socket for Vesper's private on-device player.
 *
 * MA binds a Sendspin web/app player to websocket API sessions holding the same
 * token. Keeping this socket authenticated before Sendspin connects makes
 * "This Device" controllable by the same client that owns it.
 *
 * Every network wait is bounded, and a dropped socket is replaced automatically
 * on the next command instead of failing until the app is restarted.
 */
class VesperMusicAssistantSocket(
    private val baseUrl: String,
    private val token: String,
    private val deviceName: String = "Vesper",
) : Closeable {
    private val client = HttpClient(OkHttp) {
        install(WebSockets)
    }
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private var session: DefaultClientWebSocketSession? = null

    suspend fun connect() {
        mutex.withLock { connectLocked() }
    }

    suspend fun command(command: String, args: JsonObject = JsonObject(emptyMap())): JsonElement? =
        mutex.withLock {
            // Reconnects transparently if the previous socket dropped.
            val active = connectLocked()
            try {
                withTimeout(COMMAND_TIMEOUT_MS) { sendCommandLocked(active, command, args) }
            } catch (error: MusicAssistantCommandException) {
                throw error
            } catch (error: Throwable) {
                // Timeout or transport failure: discard this socket so the next
                // command starts from a clean connection.
                dropSessionLocked(active)
                throw error
            }
        }

    private suspend fun connectLocked(): DefaultClientWebSocketSession {
        session?.let { existing ->
            if (existing.isActive) return existing
        }
        session = null
        require(token.isNotBlank()) { "Music Assistant token is missing." }

        val wsUrl = websocketUrl(baseUrl)
        val connected = withTimeout(CONNECT_TIMEOUT_MS) {
            client.webSocketSession { url(wsUrl) }
        }

        try {
            withTimeout(CONNECT_TIMEOUT_MS) {
                awaitServerInfo(connected)
                sendCommandLocked(
                    connected,
                    "auth",
                    buildJsonObject {
                        put("token", JsonPrimitive(token))
                        put("device_name", JsonPrimitive(deviceName))
                    },
                )
            }
        } catch (error: Throwable) {
            runCatching { connected.close() }
            throw error
        }

        session = connected
        return connected
    }

    private suspend fun dropSessionLocked(active: DefaultClientWebSocketSession) {
        if (session === active) session = null
        runCatching { active.close() }
    }

    private suspend fun awaitServerInfo(active: DefaultClientWebSocketSession) {
        while (true) {
            val frame = active.incoming.receive()
            if (frame !is KtorFrame.Text) continue
            val payload = json.parseToJsonElement(frame.readText()) as? JsonObject ?: continue
            if (payload["server_id"] != null && payload["server_version"] != null) return
        }
    }

    private suspend fun sendCommandLocked(
        active: DefaultClientWebSocketSession,
        command: String,
        args: JsonObject,
    ): JsonElement? {
        val messageId = UUID.randomUUID().toString()
        val payload = buildJsonObject {
            put("message_id", JsonPrimitive(messageId))
            put("command", JsonPrimitive(command))
            put("args", args)
        }
        active.send(KtorFrame.Text(payload.toString()))

        while (true) {
            val frame = active.incoming.receive()
            if (frame !is KtorFrame.Text) continue
            val response = json.parseToJsonElement(frame.readText()) as? JsonObject ?: continue
            if (response["message_id"]?.jsonPrimitive?.contentOrNull != messageId) continue

            response["error_code"]?.let {
                val details = response["details"]?.jsonPrimitive?.contentOrNull
                    ?: "Music Assistant command failed."
                throw MusicAssistantCommandException(details)
            }
            return response["result"]
        }
    }

    override fun close() {
        session = null
        client.close()
    }

    private fun websocketUrl(raw: String): String {
        val uri = URI(raw.trim().trimEnd('/'))
        val scheme = when (uri.scheme?.lowercase()) {
            "http" -> "ws"
            "https" -> "wss"
            else -> error("Music Assistant URL must use http or https.")
        }
        val path = uri.rawPath?.trimEnd('/').orEmpty()
        val wsPath = if (path.isBlank() || path == "/") "/ws" else "$path/ws"
        return URI(scheme, uri.userInfo, uri.host, uri.port, wsPath, null, null).toString()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val COMMAND_TIMEOUT_MS = 15_000L
    }
}
