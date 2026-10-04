package org.jellyfin.androidtv.ui.mobile

import android.content.SharedPreferences
import java.net.HttpURLConnection
import java.net.URL
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
    fun getCharacters(
        item: BaseItemDto,
        apiKey: String,
        subscriberPin: String?,
    ): List<VesperCharacterOption> {
        require(apiKey.isNotBlank()) { "TheTVDB API key is required." }

        val tvdbId = item.providerIds
            ?.entries
            ?.firstOrNull { (key, _) -> key.equals("Tvdb", ignoreCase = true) }
            ?.value
            ?.toLongOrNull()
            ?: error("This item does not have a TheTVDB ID in Jellyfin.")

        val endpoint = when (item.type) {
            BaseItemKind.MOVIE -> "/movies/$tvdbId/extended"
            BaseItemKind.SERIES -> "/series/$tvdbId/extended"
            else -> error("Character artwork currently supports movies and series.")
        }

        val token = getToken(apiKey, subscriberPin)
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
                error("TheTVDB sign-in failed with HTTP $code.")
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
                error("TheTVDB request failed with HTTP $code.")
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