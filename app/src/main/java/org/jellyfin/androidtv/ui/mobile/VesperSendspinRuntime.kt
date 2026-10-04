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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.vesper.sendspin.VesperSendspinEngine

/**
 * Vesper's app-facing "This Device" Sendspin runtime.
 *
 * The MA server remains the control plane. This class only makes the Android
 * device a real MA output target and asks the existing UI to refresh when MA
 * assigns or changes the player record.
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
    private val musicAssistantClient = MusicAssistantClient(endpoint.baseUrl, token)
    private val config = MutableStateFlow<LocalPlayerConfig?>(
        LocalPlayerConfig(
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
        ),
    )
    private val engine = VesperSendspinEngine(
        context = context,
        config = config,
        keyStore = VesperSendspinKeyStore(context),
        online = networkMonitor.online,
        approvePairing = { pairingToken ->
            withContext(Dispatchers.IO) {
                musicAssistantClient.approveSendspinPairing(pairingToken)
            }
        },
        scope = scope,
    )

    val state: StateFlow<PlayerState> = engine.player.state

    init {
        scope.launch {
            engine.player.events.collect { event ->
                if (event == PlayerEvent.ServerRefreshNeeded) {
                    delay(PLAYER_REFRESH_DELAY_MS)
                    onPlayerRefresh()
                }
            }
        }
    }

    override fun close() {
        config.value = null
        engine.close()
        networkMonitor.close()
    }

    private companion object {
        const val DEVICE_NAME = "This Device"
        const val BUFFER_CAPACITY_BYTES = 15 * 1024 * 1024
        const val PLAYER_REFRESH_DELAY_MS = 350L
    }
}
