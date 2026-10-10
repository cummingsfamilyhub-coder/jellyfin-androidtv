package org.jellyfin.androidtv.ui.mobile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Device-local bookmarks, isolated by Jellyfin server and user identity.
 * This intentionally does not change the shared Music Assistant favourites.
 * Source identifiers and title/artist hints allow an eventual explicit NAS migration.
 */
internal class VesperMusicFavoritesStore(context: Context) {
    private val prefs = context.getSharedPreferences("vesper_myv_music_v1", Context.MODE_PRIVATE)

    fun load(profile: String): List<MaMediaItem> {
        if (profile.isBlank()) return emptyList()
        return runCatching {
            val values = JSONArray(prefs.getString("tracks:$profile", "[]") ?: "[]")
            buildList {
                for (index in 0 until values.length()) {
                    val obj = values.optJSONObject(index) ?: continue
                    val uri = obj.optString("uri").trim()
                    val title = obj.optString("name").trim()
                    if (uri.isBlank() || title.isBlank()) continue
                    add(MaMediaItem(
                        itemId = obj.optString("item_id"),
                        provider = obj.optString("provider").ifBlank { "library" },
                        name = title,
                        uri = uri,
                        mediaType = "track",
                        subtitle = obj.optString("artist"),
                        imageUrl = obj.optString("image_url").takeIf { it.isNotBlank() },
                        genres = obj.optJSONArray("genres")?.let { values ->
                            buildList {
                                for (i in 0 until values.length()) {
                                    values.optString(i).trim().takeIf { it.isNotBlank() }?.let(::add)
                                }
                            }
                        }.orEmpty(),
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun toggle(profile: String, track: MaMediaItem): List<MaMediaItem> {
        require(profile.isNotBlank()) { "A Vesper profile is required." }
        require(track.mediaType == "track" && track.uri.isNotBlank()) {
            "Music Assistant did not provide a track identifier."
        }
        val before = load(profile)
        val after = if (before.any { it.uri == track.uri }) {
            before.filterNot { it.uri == track.uri }
        } else {
            listOf(track) + before
        }
        val values = JSONArray()
        after.forEach { item ->
            val hint = listOf(item.name, item.subtitle).joinToString("|")
                .trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
            values.put(JSONObject()
                .put("item_id", item.itemId)
                .put("provider", item.provider)
                .put("uri", item.uri)
                .put("name", item.name)
                .put("artist", item.subtitle)
                .put("image_url", item.imageUrl ?: "")
                .put("genres", JSONArray(item.genres))
                .put("match_hint", hint))
        }
        check(prefs.edit().putString("tracks:$profile", values.toString()).commit()) {
            "Couldn't save this MyV track on the device."
        }
        return after
    }
}
