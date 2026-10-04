package org.jellyfin.androidtv.ui.mobile

import android.content.SharedPreferences
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.json.JSONObject

internal data class VesperCharacterOption(
    val name: String,
    val actorName: String?,
    val imageUrl: String,
)

internal class VesperCharacterProvider(
    private val preferences: SharedPreferences,
) {
    fun validateCredentials(
        apiKey: String,
        subscriberPin: String?,
    ) {
        require(apiKey.isNotBlank()) { "TheTVDB API key is required." }
        val token = getToken(apiKey, subscriberPin)
        // A token alone proves login, and a lightweight authenticated request proves
        // the saved credentials can actually call the v4 API.
        getJson("/search?query=Toy%20Story&type=movie&limit=1", token)
    }

    fun getCharacters(
        item: BaseItemDto,
        apiKey: String,
        subscriberPin: String?,
    ): List<VesperCharacterOption> {
        require(apiKey.isNotBlank()) { "TheTVDB API key is required." }

        val token = getToken(apiKey, subscriberPin)
        val tvdbId = resolveTvdbId(item, token)

        val endpoint = when (item.type) {
            BaseItemKind.MOVIE -> "/movies/$tvdbId/extended"
            BaseItemKind.SERIES -> "/series/$tvdbId/extended"
            else -> error("Choose an individual movie or series — collections don't have character artwork.")
        }

        val response = getJson(endpoint, token)
        val characters = response
            .optJSONObject("data")
            ?.optJSONArray("characters")
            ?: return emptyList()

        return buildList {
            for (index in 0 until characters.length()) {
                val character = characters.optJSONObject(index) ?: continue
                val peopleType = character.optString("peopleType").trim()
                if (!peopleType.equals("Actor", ignoreCase = true)) continue

                val name = character.optString("name").trim()
                val actorName = character.optString("personName").trim().ifBlank { null }
                val characterImage = normalizeImageUrl(character.optString("image"))
                val actorImage = normalizeImageUrl(character.optString("personImgURL"))

                if (name.isBlank() || characterImage.isBlank()) continue
                if (actorImage.isNotBlank() && sameImage(characterImage, actorImage)) continue

                add(
                    VesperCharacterOption(
                        name = name,
                        actorName = actorName,
                        imageUrl = characterImage,
                    )
                )
            }
        }
            .distinctBy { it.name.lowercase() to it.imageUrl }
            .take(MAX_CHARACTERS)
    }

    private fun resolveTvdbId(item: BaseItemDto, token: String): Long {
        val storedId = item.providerIds
            ?.entries
            ?.firstOrNull { (key, _) -> key.equals("Tvdb", ignoreCase = true) }
            ?.value
            ?.toLongOrNull()

        if (storedId != null) return storedId

        val title = item.name?.trim().orEmpty()
        if (title.isBlank()) error("This Jellyfin item has no title to match.")

        val type = when (item.type) {
            BaseItemKind.MOVIE -> "movie"
            BaseItemKind.SERIES -> "series"
            else -> error("Choose an individual movie or series — collections don't have character artwork.")
        }

        fun search(year: Int?): List<JSONObject> {
            val encodedTitle = URLEncoder.encode(title, Charsets.UTF_8.name())
            val yearPart = year?.let { "&year=$it" }.orEmpty()
            val response = getJson(
                "/search?query=$encodedTitle&type=$type$yearPart&limit=10",
                token,
            )
            val data = response.optJSONArray("data") ?: return emptyList()
            return buildList {
                for (index in 0 until data.length()) {
                    data.optJSONObject(index)?.let(::add)
                }
            }
        }

        val candidates = search(item.productionYear).ifEmpty {
            search(null)
        }

        if (candidates.isEmpty()) {
            error("TheTVDB could not match $title.")
        }

        val normalizedTitle = normalizeTitle(title)
        val best = candidates
            .sortedWith(
                compareByDescending<JSONObject> { candidate ->
                    val candidateName = candidate.optString("name").ifBlank {
                        candidate.optString("title")
                    }
                    normalizeTitle(candidateName) == normalizedTitle
                }.thenByDescending { candidate ->
                    val candidateYear = candidate.optString("year").toIntOrNull()
                    item.productionYear != null && candidateYear == item.productionYear
                }
            )
            .first()

        return best.optString("tvdb_id")
            .ifBlank { best.optString("id") }
            .toLongOrNull()
            ?: error("TheTVDB returned a match without an ID.")
    }

    private fun normalizeTitle(value: String): String =
        value.lowercase()
            .filter { it.isLetterOrDigit() }

    fun downloadImage(url: String): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            setRequestProperty("Accept", "image/*")
            setRequestProperty("User-Agent", USER_AGENT)
        }

        return connection.use {
            val code = responseCode
            if (code !in 200..299) {
                error("Character image request failed with HTTP $code.")
            }
            inputStream.use { stream -> stream.readBytes() }
        }
    }

    private fun getToken(apiKey: String, subscriberPin: String?): String {
        val credentialFingerprint = "$apiKey|${subscriberPin.orEmpty()}".hashCode().toString()
        val cachedFingerprint = preferences.getString(PREF_TOKEN_FINGERPRINT, null)
        val cachedToken = preferences.getString(PREF_TOKEN, null)
        val cachedAt = preferences.getLong(PREF_TOKEN_CREATED_AT, 0L)

        if (
            !cachedToken.isNullOrBlank() &&
            cachedFingerprint == credentialFingerprint &&
            System.currentTimeMillis() - cachedAt < TOKEN_CACHE_MS
        ) {
            return cachedToken
        }

        val payload = JSONObject().apply {
            put("apikey", apiKey)
            if (!subscriberPin.isNullOrBlank()) put("pin", subscriberPin)
        }

        val connection = (URL("$BASE_URL/login").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
        }

        val token = connection.use {
            outputStream.use { output ->
                output.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val code = responseCode
            val body = responseBody()
            if (code !in 200..299) {
                val detail = runCatching {
                    JSONObject(body).optString("message").takeIf { it.isNotBlank() }
                }.getOrNull()
                error(
                    buildString {
                        append("TheTVDB sign-in failed (HTTP $code)")
                        if (!detail.isNullOrBlank()) append(": $detail")
                    }
                )
            }

            JSONObject(body)
                .optJSONObject("data")
                ?.optString("token")
                ?.takeIf { it.isNotBlank() }
                ?: error("TheTVDB did not return an access token.")
        }

        preferences.edit()
            .putString(PREF_TOKEN, token)
            .putString(PREF_TOKEN_FINGERPRINT, credentialFingerprint)
            .putLong(PREF_TOKEN_CREATED_AT, System.currentTimeMillis())
            .apply()

        return token
    }

    private fun getJson(path: String, token: String): JSONObject {
        val connection = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("User-Agent", USER_AGENT)
        }

        return connection.use {
            val code = responseCode
            val body = responseBody()
            if (code !in 200..299) {
                if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    preferences.edit()
                        .remove(PREF_TOKEN)
                        .remove(PREF_TOKEN_FINGERPRINT)
                        .remove(PREF_TOKEN_CREATED_AT)
                        .apply()
                }
                val detail = runCatching {
                    JSONObject(body).optString("message").takeIf { it.isNotBlank() }
                }.getOrNull()
                error(
                    buildString {
                        append("TheTVDB request failed (HTTP $code)")
                        if (!detail.isNullOrBlank()) append(": $detail")
                    }
                )
            }
            JSONObject(body)
        }
    }

    private fun HttpURLConnection.responseBody(): String {
        val stream = if (responseCode in 200..299) inputStream else errorStream
        return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    }

    private fun normalizeImageUrl(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return ""
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return "https://artworks.thetvdb.com/${trimmed.trimStart('/')}"
    }

    private fun sameImage(first: String, second: String): Boolean =
        first.substringAfter("://").trimEnd('/') == second.substringAfter("://").trimEnd('/')

    private inline fun <T> HttpURLConnection.use(block: HttpURLConnection.() -> T): T =
        try {
            block()
        } finally {
            disconnect()
        }

    private companion object {
        const val BASE_URL = "https://api4.thetvdb.com/v4"
        const val USER_AGENT = "Vesper/1.0"
        const val NETWORK_TIMEOUT_MS = 15_000
        const val MAX_CHARACTERS = 36
        const val TOKEN_CACHE_MS = 25L * 24L * 60L * 60L * 1000L

        const val PREF_TOKEN = "tvdb_access_token"
        const val PREF_TOKEN_CREATED_AT = "tvdb_access_token_created_at"
        const val PREF_TOKEN_FINGERPRINT = "tvdb_access_token_fingerprint"
    }
}