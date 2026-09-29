package org.jellyfin.androidtv

internal object VesperServiceConfig {
    const val JELLYFIN_BASE_URL = "https://vesper-jellyfin.duckdns.org"
    const val SEERR_BASE_URL = "https://vesper-seerr.duckdns.org"

    const val CONNECT_TIMEOUT_MS = 5_000
    const val READ_TIMEOUT_MS = 10_000
    const val REQUEST_READ_TIMEOUT_MS = 15_000
}
