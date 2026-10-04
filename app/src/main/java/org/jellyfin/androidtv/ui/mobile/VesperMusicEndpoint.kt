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
            val sendspinUrl = if (remoteReady) {
                URI(
                    "wss",
                    uri.userInfo,
                    uri.host,
                    uri.port,
                    "/sendspin",
                    null,
                    null,
                ).toString()
            } else {
                URI(
                    "ws",
                    null,
                    uri.host,
                    SENDSPIN_LOCAL_PORT,
                    "/sendspin",
                    null,
                    null,
                ).toString()
            }

            return VesperMusicEndpoint(
                baseUrl = normalized,
                apiUrl = "$normalized/api",
                sendspinUrl = sendspinUrl,
                remoteReady = remoteReady,
            )
        }

        private const val SENDSPIN_LOCAL_PORT = 8927
    }
}
