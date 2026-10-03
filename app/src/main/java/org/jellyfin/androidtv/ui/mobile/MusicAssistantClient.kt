package org.jellyfin.androidtv.ui.mobile

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

internal data class MaMediaItem(
    val itemId: String,
    val provider: String,
    val name: String,
    val uri: String,
    val mediaType: String,
    val subtitle: String,
    val imageUrl: String?,
    val playable: Boolean = true,
)

internal data class MaPlayer(
    val playerId: String,
    val name: String,
    val type: String,
    val available: Boolean,
    val enabled: Boolean,
    val hidden: Boolean,
    val privatePlayer: Boolean,
    val powered: Boolean?,
    val playbackState: String,
    val volumeLevel: Int?,
    val currentTitle: String?,
    val currentArtist: String?,
    val currentImageUrl: String?,
    val currentMediaType: String?,
    val activeSource: String?,
    val activeGroup: String?,
    val syncedTo: String?,
    val groupMembers: List<String>,
    val canGroupWith: Set<String>,
    val queueItemCount: Int,
    val queueCurrentIndex: Int?,
    val canPrevious: Boolean,
    val canNext: Boolean,
)

internal data class MusicAssistantSnapshot(
    val recentlyPlayed: List<MaMediaItem> = emptyList(),
    val artists: List<MaMediaItem> = emptyList(),
    val albums: List<MaMediaItem> = emptyList(),
    val playlists: List<MaMediaItem> = emptyList(),
    val radios: List<MaMediaItem> = emptyList(),
    val players: List<MaPlayer> = emptyList(),
)

internal class MusicAssistantClient(
    private val baseUrl: String,
    private val token: String,
) {
    private val root = baseUrl.trim().trimEnd('/')

    fun loadSnapshot(): MusicAssistantSnapshot {
        val recent = commandArray(
            "music/recently_played_items",
            JSONObject()
                .put("limit", 18)
                .put("media_types", JSONArray(listOf("track", "album", "playlist", "radio")))
        ).mapNotNull(::parseMediaItem)

        val artists = commandArray(
            "music/artists/library_items",
            JSONObject()
                .put("limit", 30)
                .put("offset", 0)
                .put("order_by", "name")
                .put("album_artists_only", true)
        ).mapNotNull(::parseMediaItem)

        val albums = commandArray(
            "music/albums/library_items",
            JSONObject()
                .put("limit", 30)
                .put("offset", 0)
                .put("order_by", "timestamp_added_desc")
        ).mapNotNull(::parseMediaItem)

        val playlists = commandArray(
            "music/playlists/library_items",
            JSONObject()
                .put("limit", 24)
                .put("offset", 0)
                .put("order_by", "name")
        ).mapNotNull(::parseMediaItem)

        val radios = commandArray(
            "music/radios/library_items",
            JSONObject()
                .put("limit", 24)
                .put("offset", 0)
                .put("order_by", "name")
        ).mapNotNull(::parseMediaItem)

        val queues = commandArray("player_queues/all", JSONObject())
            .associateBy { it.optString("queue_id") }

        val players = commandArray("players/all", JSONObject())
            .mapNotNull { playerJson ->
                val playerId = playerJson.optString("player_id")
                val activeSource = playerJson.optString("active_source").takeIf { it.isNotBlank() }
                val queue = activeSource?.let(queues::get) ?: queues[playerId]
                parsePlayer(playerJson, queue)
            }
            .filter { it.available && it.enabled && !it.hidden && !it.privatePlayer }
            .filter { it.type !in setOf("protocol", "source", "visualizer", "light") }
            .sortedWith(
                compareBy<MaPlayer> { playerPriority(it.name) }
                    .thenBy { it.name.lowercase() }
            )

        return MusicAssistantSnapshot(
            recentlyPlayed = recent,
            artists = artists,
            albums = albums,
            playlists = playlists,
            radios = radios,
            players = players,
        )
    }

    fun play(item: MaMediaItem, playerId: String) {
        command(
            "player_queues/play_media",
            JSONObject()
                .put("queue_id", playerId)
                .put("media", item.uri)
                .put("option", "replace")
                .put("start_from_beginning", false)
        )
    }


    fun groupAndPlay(item: MaMediaItem, playerIds: List<String>) {
        val selected = playerIds.distinct()
        require(selected.isNotEmpty()) { "Choose at least one room." }

        if (selected.size == 1) {
            play(item, selected.first())
            return
        }

        val target = selected.first()
        val children = selected.drop(1)

        // Clear stale dynamic membership before creating the new room combination.
        command(
            "players/cmd/ungroup_many",
            JSONObject().put("player_ids", JSONArray(selected))
        )
        command(
            "players/cmd/set_members",
            JSONObject()
                .put("target_player", target)
                .put("player_ids_to_add", JSONArray(children))
                .put("player_ids_to_remove", JSONArray())
        )
        play(item, target)
    }

    fun updateGroup(
        targetPlayerId: String,
        addPlayerIds: List<String>,
        removePlayerIds: List<String>,
    ) {
        if (addPlayerIds.isEmpty() && removePlayerIds.isEmpty()) return

        command(
            "players/cmd/set_members",
            JSONObject()
                .put("target_player", targetPlayerId)
                .put("player_ids_to_add", JSONArray(addPlayerIds.distinct()))
                .put("player_ids_to_remove", JSONArray(removePlayerIds.distinct()))
        )
    }

    fun playPause(playerId: String) {
        command("players/cmd/play_pause", JSONObject().put("player_id", playerId))
    }

    fun next(playerId: String) {
        command("players/cmd/next", JSONObject().put("player_id", playerId))
    }

    fun previous(playerId: String) {
        command("players/cmd/previous", JSONObject().put("player_id", playerId))
    }

    fun setVolume(playerId: String, volume: Int) {
        command(
            "players/cmd/volume_set",
            JSONObject()
                .put("player_id", playerId)
                .put("volume_level", volume.coerceIn(0, 100))
        )
    }

    private fun commandArray(commandName: String, args: JSONObject): List<JSONObject> {
        val result = command(commandName, args)
        val array = when (result) {
            is JSONArray -> result
            is JSONObject -> result.optJSONArray("items") ?: JSONArray()
            else -> JSONArray()
        }
        return buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let(::add)
            }
        }
    }

    private fun command(command: String, args: JSONObject): Any? {
        require(root.isNotBlank()) { "Music Assistant URL is missing." }
        require(token.isNotBlank()) { "Music Assistant token is missing." }

        val connection = (URL("$root/api").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 6000
            readTimeout = 15000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
        }

        val payload = JSONObject()
            .put("message_id", UUID.randomUUID().toString())
            .put("command", command)
            .put("args", args)

        try {
            connection.outputStream.bufferedWriter().use { it.write(payload.toString()) }
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

            if (status == 401 || status == 403) {
                throw IllegalStateException("Music Assistant authentication failed. Check the Vesper token.")
            }
            if (status !in 200..299) {
                throw IllegalStateException(
                    "Music Assistant returned HTTP $status${body.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}"
                )
            }

            if (body.isBlank()) return null

            // Music Assistant's HTTP /api endpoint returns the command result directly.
            // Library commands therefore return a top-level JSON array, while some
            // commands return an object, primitive or null. Keep compatibility with
            // wrapped JSON-RPC-style responses as well.
            val response = JSONTokener(body).nextValue()
            if (response is JSONObject && response.has("error_code")) {
                val details = response.optString("details").ifBlank {
                    "Music Assistant command failed."
                }
                throw IllegalStateException(details)
            }

            return if (response is JSONObject && response.has("result")) {
                response.opt("result")
            } else {
                response
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseMediaItem(json: JSONObject): MaMediaItem? {
        val name = json.optString("name").trim()
        val uri = json.optString("uri").trim()
        if (name.isBlank() || uri.isBlank()) return null

        val mediaType = json.optString("media_type").ifBlank { "track" }
        val provider = json.optString("provider").ifBlank { "library" }
        val subtitle = when (mediaType) {
            "track", "album" -> artistNames(json).ifBlank {
                json.optInt("year", 0).takeIf { it > 0 }?.toString().orEmpty()
            }
            "playlist" -> json.optString("owner").takeIf { it.isNotBlank() }.orEmpty()
            "radio" -> "Radio"
            "artist" -> "Artist"
            else -> mediaType.replaceFirstChar { it.uppercase() }
        }

        return MaMediaItem(
            itemId = json.optString("item_id"),
            provider = provider,
            name = name,
            uri = uri,
            mediaType = mediaType,
            subtitle = subtitle,
            imageUrl = mediaImageUrl(json),
            playable = json.optBoolean("is_playable", mediaType != "artist"),
        )
    }

    private fun parsePlayer(
        json: JSONObject,
        queue: JSONObject?,
    ): MaPlayer? {
        val id = json.optString("player_id").trim()
        val name = json.optString("name").trim()
        if (id.isBlank() || name.isBlank()) return null

        val current = json.optJSONObject("current_media")
        val mediaType = current?.optString("media_type")?.takeIf { it.isNotBlank() }
        val queueItems = queue?.optInt("items", 0) ?: 0
        val queueIndex = queue?.let {
            if (it.isNull("current_index")) null else it.optInt("current_index")
        }
        val isLive = mediaType in setOf("radio", "audio_source")
        val hasPrevious = !isLive && queueIndex != null && queueIndex > 0
        val hasNext = !isLive && queue?.optJSONObject("next_item") != null

        return MaPlayer(
            playerId = id,
            name = name,
            type = json.optString("type").ifBlank { "player" },
            available = json.optBoolean("available", true),
            enabled = json.optBoolean("enabled", true),
            hidden = json.optBoolean("hide_in_ui", false),
            privatePlayer = json.optBoolean("private", false),
            powered = if (json.isNull("powered")) null else json.optBoolean("powered"),
            playbackState = json.optString("playback_state").ifBlank { "idle" },
            volumeLevel = if (json.isNull("volume_level")) null else json.optInt("volume_level"),
            currentTitle = current?.optString("title")?.takeIf { it.isNotBlank() },
            currentArtist = current?.optString("artist")?.takeIf { it.isNotBlank() },
            currentImageUrl = current?.optString("image_url")
                ?.takeIf { it.isNotBlank() }
                ?.let(::normalizeImageUrl),
            currentMediaType = mediaType,
            activeSource = json.optString("active_source").takeIf { it.isNotBlank() },
            activeGroup = json.optString("active_group").takeIf { it.isNotBlank() },
            syncedTo = json.optString("synced_to").takeIf { it.isNotBlank() },
            groupMembers = json.stringList("group_members"),
            canGroupWith = json.stringList("can_group_with").toSet(),
            queueItemCount = queueItems,
            queueCurrentIndex = queueIndex,
            canPrevious = hasPrevious,
            canNext = hasNext,
        )
    }

    private fun JSONObject.stringList(key: String): List<String> {
        val values = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (index in 0 until values.length()) {
                values.optString(index)
                    .trim()
                    .takeIf { it.isNotBlank() }
                    ?.let(::add)
            }
        }
    }

    private fun artistNames(json: JSONObject): String {
        val artists = json.optJSONArray("artists") ?: return ""
        return buildList {
            for (index in 0 until artists.length()) {
                artists.optJSONObject(index)
                    ?.optString("name")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::add)
            }
        }.joinToString(", ")
    }

    private fun mediaImageUrl(json: JSONObject): String? {
        json.optJSONObject("image")?.let { image ->
            imageUrl(image)?.let { return it }
        }

        val album = json.optJSONObject("album")
        album?.optJSONObject("image")?.let { image ->
            imageUrl(image)?.let { return it }
        }
        album?.optJSONObject("metadata")?.optJSONArray("images")?.let { images ->
            bestImage(images)?.let { return imageUrl(it) }
        }

        json.optJSONObject("metadata")?.optJSONArray("images")?.let { images ->
            bestImage(images)?.let { return imageUrl(it) }
        }

        val artists = json.optJSONArray("artists")
        if (artists != null) {
            for (index in 0 until artists.length()) {
                artists.optJSONObject(index)
                    ?.optJSONObject("image")
                    ?.let { image -> imageUrl(image)?.let { return it } }
            }
        }

        return null
    }

    private fun bestImage(images: JSONArray): JSONObject? {
        var fallback: JSONObject? = null
        for (index in 0 until images.length()) {
            val image = images.optJSONObject(index) ?: continue
            if (fallback == null) fallback = image
            if (image.optString("type") == "thumb") return image
        }
        return fallback
    }

    private fun imageUrl(image: JSONObject): String? {
        val proxyId = image.optString("proxy_id").trim()
        if (proxyId.isNotBlank()) return "$root/imageproxy/$proxyId?size=512"

        val path = image.optString("path").trim()
        if (path.isBlank()) return null
        if (path.startsWith("data:image")) return path

        val remotelyAccessible = image.optBoolean("remotely_accessible", false)
        if (remotelyAccessible && (path.startsWith("http://") || path.startsWith("https://"))) {
            return path
        }

        val provider = image.optString("provider").ifBlank { "builtin" }
        val once = URLEncoder.encode(path, StandardCharsets.UTF_8.name())
        val twice = URLEncoder.encode(once, StandardCharsets.UTF_8.name())
        return "$root/imageproxy?path=$twice&provider=${URLEncoder.encode(provider, StandardCharsets.UTF_8.name())}&size=512"
    }

    private fun normalizeImageUrl(url: String): String {
        if (url.startsWith("/")) return root + url
        if (url.contains("/imageproxy")) {
            return runCatching {
                val parsed = URL(url)
                root + parsed.file
            }.getOrDefault(url)
        }
        return url
    }

    private fun playerPriority(name: String): Int {
        val n = name.lowercase()
        return when {
            "living room" in n -> 0
            "den" in n -> 1
            "kitchen" in n -> 2
            "main bedroom" in n -> 3
            "nursery" in n -> 4
            "beth" in n || "jess" in n -> 5
            "rosa" in n -> 6
            else -> 20
        }
    }
}
