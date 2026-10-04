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
            val host = uri.host
            require(!host.isNullOrBlank()) { "Music Assistant URL needs a valid host." }
            if (scheme == "http") {
                require(isLocalCleartextHost(host)) {
                    "Remote Music Assistant access must use HTTPS. HTTP is allowed only for private/local network addresses."
                }
            }

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
                host,
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

        private fun isLocalCleartextHost(host: String): Boolean {
            val normalized = host.lowercase().removePrefix("[").removeSuffix("]")

            if (
                normalized == "localhost" ||
                normalized.endsWith(".local") ||
                normalized == "::1" ||
                normalized.startsWith("fe80:") ||
                normalized.startsWith("fc") ||
                normalized.startsWith("fd")
            ) {
                return true
            }

            val octets = normalized.split('.').mapNotNull { it.toIntOrNull() }
            if (octets.size != 4 || octets.any { it !in 0..255 }) return false

            return when {
                octets[0] == 10 -> true
                octets[0] == 127 -> true
                octets[0] == 169 && octets[1] == 254 -> true
                octets[0] == 172 && octets[1] in 16..31 -> true
                octets[0] == 192 && octets[1] == 168 -> true
                else -> false
            }
        }
    }
}
