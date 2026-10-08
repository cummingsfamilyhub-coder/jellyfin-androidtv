package org.jellyfin.androidtv

import android.content.SharedPreferences

internal object VesperServiceConfig {
    const val JELLYFIN_BASE_URL = "https://vesper-jellyfin.duckdns.org"
    const val SEERR_BASE_URL = "https://vesper-seerr.duckdns.org"
    const val MUSIC_ASSISTANT_BASE_URL = "https://vesper-music.duckdns.org"
    const val AURRAL_BASE_URL = "https://aurral.vesper-music.duckdns.org"
    const val LAZYLIBRARIAN_BASE_URL = "https://books.vesper-music.duckdns.org"
    const val AUDIOBOOKSHELF_BASE_URL = "https://audiobooks.vesper-music.duckdns.org"

    fun musicAssistantUrl(preferences: SharedPreferences): String =
        canonicalUrl(preferences, "music_assistant_url", "http://192.168.1.34:8095", MUSIC_ASSISTANT_BASE_URL)

    fun aurralUrl(preferences: SharedPreferences): String =
        canonicalUrl(preferences, "music_requests_url", "http://192.168.1.106:3001", AURRAL_BASE_URL)

    fun lazyLibrarianUrl(preferences: SharedPreferences): String =
        canonicalUrl(preferences, "book_requests_url", "http://192.168.1.106:5299", LAZYLIBRARIAN_BASE_URL)

    fun audiobookshelfUrl(preferences: SharedPreferences): String =
        canonicalUrl(preferences, "audiobook_library_url", "http://192.168.1.106:13378", AUDIOBOOKSHELF_BASE_URL)

    private fun canonicalUrl(
        preferences: SharedPreferences,
        key: String,
        legacyUrl: String,
        httpsUrl: String,
    ): String {
        val saved = preferences.getString(key, "").orEmpty().trim()
        val resolved = if (saved.isBlank() || saved.trimEnd('/') == legacyUrl) httpsUrl else saved
        if (resolved != saved) preferences.edit().putString(key, resolved).apply()
        return resolved
    }

    const val CONNECT_TIMEOUT_MS = 5_000
    const val READ_TIMEOUT_MS = 10_000
    const val REQUEST_READ_TIMEOUT_MS = 15_000
}
