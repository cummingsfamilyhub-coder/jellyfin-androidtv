@file:Suppress("MagicNumber")

package org.jellyfin.androidtv.ui.mobile

import android.content.Context
import io.music_assistant.sendspin.api.AudioCodec
import io.music_assistant.sendspin.api.Endpoint
import io.music_assistant.sendspin.api.LocalPlayerConfig
import io.music_assistant.sendspin.api.PlayerEvent
import io.music_assistant.sendspin.api.PlayerState
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.jellyfin.vesper.sendspin.VesperMusicAssistantSocket
import org.jellyfin.vesper.sendspin.VesperSendspinEngine

/**
 * Vesper's app-facing "This Device" Sendspin runtime.
 *
 * The MA websocket control session is authenticated before Sendspin connects.
 * Music Assistant can then bind this private app player to the same API session
 * that owns it, matching the official web/mobile player lifecycle.
 */
internal class VesperSendspinRuntime(
    context: Context,
    musicAssistantBaseUrl: String,
    token: String,
    scope: CoroutineScope,
    private val onPlayerRefresh: () -> Unit,
) : Closeable {
    private val endpoint = VesperMusicEndpoint.from(musicAssistantBaseUrl)
    private val networkMonitor = VesperNetworkMonitor(context)
    private val controlSocket = VesperMusicAssistantSocket(endpoint.baseUrl, token)
    private val playerConfig = LocalPlayerConfig(
        endpoint = Endpoint.WebSocket(
            url = endpoint.sendspinUrl,
            authToken = token,
        ),
        deviceName = DEVICE_NAME,
        codecPreference = listOf(
            AudioCodec.FLAC,
            AudioCodec.OPUS,
            AudioCodec.PCM,
        ),
        bufferCapacityBytes = BUFFER_CAPACITY_BYTES,
        userDelayMs = 0,
    )
    private val config = MutableStateFlow<LocalPlayerConfig?>(null)
    private val engine = VesperSendspinEngine(
        context = context,
        config = config,
        keyStore = VesperSendspinKeyStore(context),
        online = networkMonitor.online,
        approvePairing = { pairingToken ->
            controlSocket.command(
                "sendspin/pair_web_player",
                buildJsonObject {
                    put("pairing_token", JsonPrimitive(pairingToken))
                },
            )
        },
        scope = scope,
    )

    val state: StateFlow<PlayerState> = engine.player.state
    val events: Flow<PlayerEvent> = engine.player.events

    init {
        scope.launch {
            controlSocket.connect()
            // Only expose the Sendspin player after its owning MA API websocket
            // exists. The Sendspin proxy binds this player id to that session.
            config.value = playerConfig
        }
        scope.launch {
            engine.player.events.collect { event ->
                if (event == PlayerEvent.ServerRefreshNeeded) {
                    delay(PLAYER_REFRESH_DELAY_MS)
                    onPlayerRefresh()
                }
            }
        }
    }

    suspend fun playMedia(itemUri: String, playerId: String) {
        controlSocket.command(
            "player_queues/play_media",
            buildJsonObject {
                put("queue_id", JsonPrimitive(playerId))
                put("media", JsonPrimitive(itemUri))
                put("option", JsonPrimitive("replace"))
                put("start_from_beginning", JsonPrimitive(false))
            },
        )
    }

    suspend fun resume(playerId: String) = playerCommand("play", playerId)
    suspend fun pause(playerId: String) = playerCommand("pause", playerId)
    suspend fun next(playerId: String) = playerCommand("next", playerId)
    suspend fun previous(playerId: String) = playerCommand("previous", playerId)

    private suspend fun playerCommand(command: String, playerId: String) {
        controlSocket.command(
            "players/cmd/$command",
            buildJsonObject {
                put("player_id", JsonPrimitive(playerId))
            },
        )
    }

    override fun close() {
        config.value = null
        controlSocket.close()
        engine.close()
        networkMonitor.close()
    }

    private companion object {
        const val DEVICE_NAME = "This Device"
        const val BUFFER_CAPACITY_BYTES = 15 * 1024 * 1024
        const val PLAYER_REFRESH_DELAY_MS = 350L
    }
}
