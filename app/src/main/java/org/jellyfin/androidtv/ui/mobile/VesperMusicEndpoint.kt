package org.jellyfin.androidtv.ui.mobile

import java.net.URI

internal data class VesperMusicEndpoint(
    val baseUrl: String,
    val apiUrl: String,
    val sendspinUrl: String,
    val remoteReady: Boolean,
) {
    companion object {
        fun from(rawUrl: String): VesperMusicEndpoint {
            val normalized = rawUrl.trim().trimEnd('/')
            require(normalized.isNotBlank()) { "Music Assistant URL is required." }

            val uri = URI(normalized)
            val scheme = uri.scheme?.lowercase()
            require(scheme == "http" || scheme == "https") {
                "Music Assistant URL must start with http:// or https://."
            }
            require(!uri.host.isNullOrBlank()) { "Music Assistant URL needs a valid host." }

            val remoteReady = scheme == "https"
            val websocketScheme = if (remoteReady) "wss" else "ws"
            val basePath = uri.rawPath?.trimEnd('/').orEmpty()
            val sendspinPath = if (basePath.isBlank() || basePath == "/") {
                "/sendspin"
            } else {
                "$basePath/sendspin"
            }
            val sendspinUrl = URI(
                websocketScheme,
                uri.userInfo,
                uri.host,
                uri.port,
                sendspinPath,
                null,
                null,
            ).toString()

            return VesperMusicEndpoint(
                baseUrl = normalized,
                apiUrl = "$normalized/api",
                sendspinUrl = sendspinUrl,
                remoteReady = remoteReady,
            )
        }
    }
}
