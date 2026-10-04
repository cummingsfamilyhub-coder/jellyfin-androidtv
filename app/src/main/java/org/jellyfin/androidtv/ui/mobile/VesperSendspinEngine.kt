package org.jellyfin.androidtv.ui.mobile

import io.music_assistant.sendspin.api.AudioCodec
import io.music_assistant.sendspin.api.Endpoint
import io.music_assistant.sendspin.api.LocalPlayerConfig

/**
 * Boundary between Vesper's Music Assistant settings and the official
 * Music Assistant Sendspin player engine.
 *
 * Audio output and lifecycle wiring deliberately live outside this class.
 */
internal object VesperSendspinEngine {
    private const val DEFAULT_BUFFER_BYTES = 15_000_000

    fun createConfig(
        rawMusicAssistantUrl: String,
        token: String,
        deviceName: String = "This Device",
    ): LocalPlayerConfig {
        val endpoint = VesperMusicEndpoint.from(rawMusicAssistantUrl)
        require(endpoint.remoteReady) {
            "This Device playback requires a secure Music Assistant HTTPS endpoint."
        }
        require(token.isNotBlank()) { "Music Assistant token is required for Sendspin." }

        return LocalPlayerConfig(
            endpoint = Endpoint.WebSocket(
                url = endpoint.sendspinUrl,
                authToken = token.trim(),
            ),
            deviceName = deviceName.trim().ifBlank { "This Device" },
            codecPreference = listOf(
                AudioCodec.OPUS,
                AudioCodec.FLAC,
                AudioCodec.PCM,
            ),
            bufferCapacityBytes = DEFAULT_BUFFER_BYTES,
            userDelayMs = 0,
        )
    }
}
