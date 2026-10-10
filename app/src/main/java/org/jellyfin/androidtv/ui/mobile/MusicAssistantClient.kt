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
    val editable: Boolean = false,
    val genres: List<String> = emptyList(),
)

internal data class MaPlayer(
    val playerId: String,
    val queueId: String,
    val provider: String,
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
    val queueCurrentItemId: String? = null,
    val queueResumePosition: Int,
    val queueElapsedTime: Int,
    val queueDuration: Int,
    val queueActive: Boolean,
    val queueEnded: Boolean,
    val canPrevious: Boolean,
    val canNext: Boolean,
    val currentTrack: MaMediaItem? = null,
)

internal data class MaQueueItem(
    val queueItemId: String,
    val queueId: String,
    val index: Int,
    val name: String,
    val artist: String?,
    val imageUrl: String?,
    val duration: Int?,
    val mediaType: String,
)

internal data class MaSearchResults(
    val artists: List<MaMediaItem> = emptyList(),
    val albums: List<MaMediaItem> = emptyList(),
    val tracks: List<MaMediaItem> = emptyList(),
) {
    val all: List<MaMediaItem>
        get() = artists + albums + tracks
}

internal data class MaPlaylistTrack(val item: MaMediaItem, val position: Int)
internal data class MaPlaylistDetails(val title: String, val editable: Boolean, val tracks: List<MaPlaylistTrack>)

internal data class MaAlbumTrack(
    val item: MaMediaItem,
    val trackNumber: Int,
    val discNumber: Int,
    val durationSeconds: Int?,
)

internal data class MaAlbumDetails(
    val releaseYear: Int?,
    val tracks: List<MaAlbumTrack>,
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

    fun validateConnection() {
        commandArray(
            "players/all",
            JSONObject()
                .put("return_unavailable", false)
                .put("return_disabled", false)
        )
    }

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

        val players = loadPlayers()

        return MusicAssistantSnapshot(
            recentlyPlayed = recent,
            artists = artists,
            albums = albums,
            playlists = playlists,
            radios = radios,
            players = players,
        )
    }


    /** Full category pages are paginated; home snapshot only shows first few items. */
    fun loadCategoryItems(category: String): List<MaMediaItem> {
        val path = when (category) {
            "Artists" -> "music/artists/library_items"
            "Playlists" -> "music/playlists/library_items"
            "Radio" -> "music/radios/library_items"
            "Recently Played" -> "music/recently_played_items"
            else -> throw IllegalArgumentException("Unsupported music category.")
        }
        if (category == "Recently Played") {
            return commandArray(path, JSONObject()
                .put("limit", 200)
                .put("media_types", JSONArray(listOf("track", "album", "playlist", "radio"))))
                .mapNotNull(::parseMediaItem).distinctBy { it.uri }
        }

        val results = mutableListOf<MaMediaItem>()
        val pageSize = 100
        for (page in 0 until 10) {
            val args = JSONObject()
                .put("limit", pageSize).put("offset", page * pageSize)
                .put("order_by", "name")
            if (category == "Artists") args.put("album_artists_only", true)
            val batch = commandArray(path, args)
            results += batch.mapNotNull(::parseMediaItem)
            if (batch.size < pageSize) break
        }
        return results.distinctBy { it.uri }
    }

    fun loadArtistAlbums(artist: MaMediaItem): List<MaMediaItem> {
        require(artist.mediaType == "artist" && artist.itemId.isNotBlank()) {
            "Artist details are unavailable."
        }
        return commandArray(
            "music/artists/artist_albums",
            JSONObject()
                .put("item_id", artist.itemId)
                .put("provider_instance_id_or_domain", artist.provider),
        ).mapNotNull(::parseMediaItem)
    }

    fun loadAlbumDetails(album: MaMediaItem): MaAlbumDetails {
        require(album.mediaType == "album" && album.itemId.isNotBlank()) {
            "Album details are unavailable."
        }
        val albumArgs = JSONObject()
            .put("item_id", album.itemId)
            .put("provider_instance_id_or_domain", album.provider)
        val metadata = command("music/albums/get_album", albumArgs) as? JSONObject
            ?: throw IllegalStateException("Couldn't load album details.")
        val tracks = commandArray(
            "music/albums/album_tracks",
            JSONObject()
                .put("item_id", album.itemId)
                .put("provider_instance_id_or_domain", album.provider)
                .put("in_library_only", false),
        ).mapNotNull { track ->
            val media = parseMediaItem(track) ?: return@mapNotNull null
            MaAlbumTrack(
                item = media,
                trackNumber = track.optInt("track_number", 0),
                discNumber = track.optInt("disc_number", 1).coerceAtLeast(1),
                durationSeconds = track.optInt("duration", 0).takeIf { it > 0 },
            )
        }
        return MaAlbumDetails(
            releaseYear = metadata.optInt("year", 0).takeIf { it > 0 },
            tracks = tracks,
        )
    }

    fun loadTrackGenres(track: MaMediaItem): List<String> {
        if (track.mediaType != "track" || track.itemId.isBlank()) return emptyList()
        val metadata = command(
            "music/tracks/get_track",
            JSONObject()
                .put("item_id", track.itemId)
                .put("provider_instance_id_or_domain", track.provider),
        ) as? JSONObject ?: return emptyList()
        return mediaGenres(metadata)
    }

    private fun mediaGenres(item: JSONObject): List<String> {
        val genres = item.optJSONObject("metadata")?.optJSONArray("genres")
            ?: item.optJSONArray("genres")
            ?: return emptyList()
        return buildList {
            for (index in 0 until genres.length()) {
                val name = when (val raw = genres.opt(index)) {
                    is String -> raw.trim()
                    is JSONObject -> raw.optString("name").trim()
                    else -> ""
                }
                if (name.isNotBlank() && !name.equals("null", ignoreCase = true)) add(name)
            }
        }.distinct()
    }

    fun loadPlaylistDetails(playlist: MaMediaItem): MaPlaylistDetails {
        require(playlist.mediaType == "playlist" && playlist.itemId.isNotBlank()) { "Playlist details unavailable." }
        val info = command("music/playlists/get_playlist", JSONObject()
            .put("item_id", playlist.itemId)
            .put("provider_instance_id_or_domain", playlist.provider)) as? JSONObject
            ?: error("Couldn't load playlist.")
        val tracks = commandArray("music/playlists/playlist_tracks", JSONObject()
            .put("item_id", playlist.itemId)
            .put("provider_instance_id_or_domain", playlist.provider))
            .mapIndexedNotNull { index, obj ->
                parseMediaItem(obj)?.let { MaPlaylistTrack(it, index + 1) }
            }
        return MaPlaylistDetails(
            title = info.optString("name").takeIf { it.isNotBlank() } ?: playlist.name,
            editable = info.optBoolean("is_editable", false) &&
                playlist.provider == "library" && playlist.itemId.toIntOrNull() != null,
            tracks = tracks,
        )
    }

    fun createPlaylist(name: String) {
        require(name.isNotBlank()) { "Enter a name." }
        command("music/playlists/create_playlist", JSONObject()
            .put("name", name.trim())
            .put("provider_instance_or_domain", "builtin"))
    }

    private fun editablePlaylistId(playlist: MaMediaItem): Int {
        require(playlist.mediaType == "playlist" && playlist.provider == "library" &&
            playlist.editable) { "This playlist is not editable." }
        return playlist.itemId.toIntOrNull() ?: error("Playlist isn't in the Music Assistant library.")
    }

    fun renamePlaylist(playlist: MaMediaItem, name: String) {
        require(name.isNotBlank()) { "Enter a name." }
        command("music/playlists/update", JSONObject()
            .put("item_id", editablePlaylistId(playlist))
            .put("update", JSONObject().put("name", name.trim()))
            .put("overwrite", false))
    }

    fun addPlaylistTrack(playlist: MaMediaItem, track: MaMediaItem) {
        require(track.mediaType == "track" && track.uri.isNotBlank()) { "Choose a track." }
        command("music/playlists/add_playlist_tracks", JSONObject()
            .put("db_playlist_id", editablePlaylistId(playlist))
            .put("uris", JSONArray(listOf(track.uri))))
    }

    fun removePlaylistTrack(playlist: MaMediaItem, position: Int) {
        require(position > 0) { "Invalid track position." }
        command("music/playlists/remove_playlist_tracks", JSONObject()
            .put("db_playlist_id", editablePlaylistId(playlist))
            .put("positions_to_remove", JSONArray(listOf(position))))
    }

    fun searchLibrary(query: String, limit: Int = 12): MaSearchResults {
        val clean = query.trim()
        if (clean.isBlank()) return MaSearchResults()

        val result = command(
            "music/search",
            JSONObject()
                .put("search_query", clean)
                .put("media_types", JSONArray(listOf("artist", "album", "track")))
                .put("limit", limit)
                .put("providers", JSONArray(listOf("library")))
        ) as? JSONObject ?: return MaSearchResults()

        fun items(key: String): List<MaMediaItem> {
            val array = result.optJSONArray(key) ?: return emptyList()
            return buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.let(::parseMediaItem)?.let(::add)
                }
            }
        }

        return MaSearchResults(
            artists = items("artists"),
            albums = items("albums"),
            tracks = items("tracks"),
        )
    }

    fun loadQueue(queueId: String): List<MaQueueItem> {
        val result = mutableListOf<MaQueueItem>()
        var offset = 0
        while (true) {
            val batch = commandArray(
                "player_queues/items",
                JSONObject().put("queue_id", queueId).put("limit", 250).put("offset", offset)
            ).mapNotNull(::parseQueueItem)
            result.addAll(batch)
            if (batch.size < 250 || result.size >= 5000) break
            offset += batch.size
        }
        return result
    }

    fun playQueueItem(queueId: String, queueItemId: String) {
        command(
            "player_queues/play_index",
            JSONObject()
                .put("queue_id", queueId)
                .put("index", queueItemId)
                .put("seek_position", 0)
        )
    }

    fun moveQueueItem(queueId: String, queueItemId: String, shift: Int) {
        require(shift != 0)
        command(
            "player_queues/move_item",
            JSONObject().put("queue_id", queueId)
                .put("queue_item_id", queueItemId)
                .put("pos_shift", shift)
        )
    }

    fun enqueue(item: MaMediaItem, queueId: String, next: Boolean) {
        command(
            "player_queues/play_media",
            JSONObject()
                .put("queue_id", queueId)
                .put("media", item.uri)
                .put("option", if (next) "next" else "add")
        )
    }

    fun clearUpcoming(queueId: String, currentItemId: String, currentIndex: Int?) {
        val allItems = loadQueue(queueId)
        val currentPosition = allItems.indexOfFirst { it.queueItemId == currentItemId }
            .takeIf { it >= 0 }
            ?: currentIndex?.takeIf { it in allItems.indices }
            ?: throw IllegalStateException("Can't identify the playing song. Queue unchanged.")
        // Delete backwards so position shifts cannot affect the remaining queue.
        for (item in allItems.drop(currentPosition + 1).asReversed()) {
            removeQueueItem(queueId, item.queueItemId)
        }
    }

    fun clearQueue(queueId: String) {
        command("player_queues/clear", JSONObject().put("queue_id", queueId))
    }

    fun removeQueueItem(queueId: String, queueItemId: String) {
        command(
            "player_queues/delete_item",
            JSONObject().put("queue_id", queueId)
                .put("item_id_or_index", queueItemId)
        )
    }

    fun loadPlayers(): List<MaPlayer> {
        val queues = commandArray("player_queues/all", JSONObject())
            .associateBy { it.optString("queue_id") }

        return commandArray("players/all", JSONObject())
            .mapNotNull { playerJson ->
                val playerId = playerJson.optString("player_id")
                val activeSource = playerJson.optString("active_source").takeIf { it.isNotBlank() }
                // Music Assistant exposes the active queue through active_source.
                // For a grouped/synced player it can instead belong to the group
                // or sync leader, rather than the individual player's parked queue.
                val activeGroup = playerJson.optString("active_group").takeIf { it.isNotBlank() }
                val syncedTo = playerJson.optString("synced_to").takeIf { it.isNotBlank() }
                val currentTitle = playerJson.optJSONObject("current_media")
                    ?.optString("title")?.trim()?.takeIf { it.isNotBlank() }
                fun queueTitle(queue: JSONObject): String? {
                    val item = queue.optJSONObject("current_item") ?: return null
                    return (item.optJSONObject("media_item")
                        ?.optString("name")?.takeIf { it.isNotBlank() }
                        ?: item.optString("name").takeIf { it.isNotBlank() })?.trim()
                }
                fun matchesCurrentTrack(queue: JSONObject): Boolean =
                    currentTitle == null || queueTitle(queue)?.equals(currentTitle, ignoreCase = true) == true

                // Prioritise the actual playback source, then group/leader.
                // Only use a player's own parked queue if it matches playback.
                val candidates = listOfNotNull(
                    activeSource?.let(queues::get),
                    activeGroup?.let(queues::get),
                    syncedTo?.let(queues::get),
                    queues[playerId],
                ).distinctBy { it.optString("queue_id") }
                val directMatch = candidates.firstOrNull(::matchesCurrentTrack)
                // Some group players report a source that isn't a queue id.
                // In that case locate the *unique* queue with this live track,
                // rather than showing a stale unrelated queue or an empty view.
                val matchingQueues = if (directMatch == null && currentTitle != null) {
                    queues.values.filter(::matchesCurrentTrack)
                } else emptyList()
                val queue = directMatch ?: matchingQueues.singleOrNull()
                parsePlayer(playerJson, queue)
            }
            .filter {
                it.available &&
                    it.enabled &&
                    (
                        it.name.equals("This Device", ignoreCase = true) ||
                            (!it.hidden && !it.privatePlayer)
                    )
            }
            .filter { it.type !in setOf("protocol", "source", "visualizer", "light") }
            .sortedWith(
                compareBy<MaPlayer> { playerPriority(it.name) }
                    .thenBy { it.name.lowercase() }
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

    fun approveSendspinPairing(pairingToken: String) {
        require(pairingToken.isNotBlank()) { "Sendspin pairing token is missing." }
        command(
            "sendspin/pair_web_player",
            JSONObject().put("pairing_token", pairingToken),
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

    fun resume(queueId: String) {
        command("player_queues/resume", JSONObject().put("queue_id", queueId))
    }

    fun pause(queueId: String) {
        command("player_queues/pause", JSONObject().put("queue_id", queueId))
    }

    fun playPause(queueId: String) {
        command("player_queues/play_pause", JSONObject().put("queue_id", queueId))
    }

    fun next(queueId: String) {
        command("player_queues/next", JSONObject().put("queue_id", queueId))
    }

    fun previous(queueId: String) {
        command("player_queues/previous", JSONObject().put("queue_id", queueId))
    }

    fun seek(queueId: String, positionSeconds: Int) {
        command(
            "player_queues/seek",
            JSONObject()
                .put("queue_id", queueId)
                .put("position", positionSeconds.coerceAtLeast(0))
        )
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
            editable = json.optBoolean("is_editable", false),
            genres = mediaGenres(json),
        )
    }

    private fun parseQueueItem(json: JSONObject): MaQueueItem? {
        val queueItemId = json.optString("queue_item_id").trim()
        val queueId = json.optString("queue_id").trim()
        if (queueItemId.isBlank() || queueId.isBlank()) return null

        val media = json.optJSONObject("media_item")
        val artists = media?.let(::artistNames).orEmpty()
        val displayName = media?.optString("name")?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: json.optString("name").trim().ifBlank { "Unknown track" }
        val duration = if (json.isNull("duration")) null else json.optInt("duration")
        val mediaType = media?.optString("media_type")
            ?.takeIf { it.isNotBlank() }
            ?: "track"
        val image = json.optJSONObject("image")?.let(::imageUrl)
            ?: media?.let(::mediaImageUrl)

        return MaQueueItem(
            queueItemId = queueItemId,
            queueId = queueId,
            index = if (json.has("index") && !json.isNull("index")) json.optInt("index") else -1,
            name = displayName,
            artist = artists.takeIf { it.isNotBlank() },
            imageUrl = image,
            duration = duration,
            mediaType = mediaType,
        )
    }

    private fun parsePlayer(
        json: JSONObject,
        queue: JSONObject?,
    ): MaPlayer? {
        val id = json.optString("player_id").trim()
        val name = json.optString("name").trim()
        if (id.isBlank() || name.isBlank()) return null

        val playerCurrent = json.optJSONObject("current_media")
        val queueCurrent = queue?.optJSONObject("current_item")
        val queueMedia = queueCurrent?.optJSONObject("media_item")
        val mediaType = playerCurrent?.optString("media_type")?.takeIf { it.isNotBlank() }
            ?: queueMedia?.optString("media_type")?.takeIf { it.isNotBlank() }
        val queueItems = queue?.optInt("items", 0) ?: 0
        val queueIndex = queue?.let {
            if (it.isNull("current_index")) null else it.optInt("current_index")
        }
        val activeSource = json.optString("active_source").takeIf { it.isNotBlank() }
        val queueId = queue?.optString("queue_id")?.takeIf { it.isNotBlank() }
            ?: activeSource
            ?: id
        val playerState = json.optString("playback_state").ifBlank { "idle" }
        val queueState = queue?.optString("state")?.takeIf { it.isNotBlank() }
        val resumePos = queue?.optInt("resume_pos", 0) ?: 0
        val elapsedTime = queue?.optDouble("elapsed_time", 0.0)?.toInt() ?: 0
        val duration = queueCurrent?.optDouble("duration", 0.0)?.toInt()
            ?.takeIf { it > 0 }
            ?: queueMedia?.optDouble("duration", 0.0)?.toInt()
                ?.takeIf { it > 0 }
            ?: playerCurrent?.optDouble("duration", 0.0)?.toInt()
                ?.takeIf { it > 0 }
            ?: 0
        val queueActive = queue?.optBoolean("active", false) ?: false
        val queueEnded = queue?.optBoolean("ended", false) ?: false
        val queueHasParkedItem =
            !queueEnded &&
                queueCurrent != null &&
                queueIndex != null &&
                queueItems > 0 &&
                (resumePos > 0 || elapsedTime > 0 || queueState == "paused")
        val effectiveState = when {
            playerState == "playing" || queueState == "playing" -> "playing"
            playerState == "paused" || queueState == "paused" -> "paused"
            queueHasParkedItem -> "paused"
            else -> playerState
        }
        val currentTitle = playerCurrent?.optString("title")?.takeIf { it.isNotBlank() }
            ?: queueMedia?.optString("name")?.takeIf { it.isNotBlank() }
            ?: queueCurrent?.optString("name")?.takeIf { it.isNotBlank() }
        val currentArtist = playerCurrent?.optString("artist")
            ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            ?: queueMedia?.let(::artistNames)?.takeIf { it.isNotBlank() }
        val currentImageUrl = playerCurrent?.optString("image_url")
            ?.takeIf { it.isNotBlank() }
            ?.let(::normalizeImageUrl)
            ?: queueCurrent?.optJSONObject("image")?.let(::imageUrl)
            ?: queueMedia?.let(::mediaImageUrl)
        // A parked Music Assistant queue is not necessarily the active Cast song.
        val currentTrack = listOfNotNull(queueMedia, playerCurrent)
            .mapNotNull(::parseMediaItem)
            .firstOrNull { media ->
                media.mediaType == "track" && currentTitle != null &&
                    media.name.equals(currentTitle, ignoreCase = true)
            }
        val isLive = mediaType in setOf("radio", "audio_source")
        val hasPrevious = !isLive && queueIndex != null && queueIndex > 0
        val hasNext = !isLive && queue?.optJSONObject("next_item") != null

        return MaPlayer(
            playerId = id,
            queueId = queueId,
            provider = json.optString("provider"),
            name = name,
            type = json.optString("type").ifBlank { "player" },
            available = json.optBoolean("available", true),
            enabled = json.optBoolean("enabled", true),
            hidden = json.optBoolean("hide_in_ui", false),
            privatePlayer = json.optBoolean("private", false),
            powered = if (json.isNull("powered")) null else json.optBoolean("powered"),
            playbackState = effectiveState,
            volumeLevel = if (json.isNull("volume_level")) null else json.optInt("volume_level"),
            currentTitle = currentTitle,
            currentArtist = currentArtist,
            currentImageUrl = currentImageUrl,
            currentMediaType = mediaType,
            activeSource = activeSource,
            activeGroup = json.optString("active_group").takeIf { it.isNotBlank() },
            syncedTo = json.optString("synced_to").takeIf { it.isNotBlank() },
            groupMembers = json.stringList("group_members"),
            canGroupWith = json.stringList("can_group_with").toSet(),
            queueItemCount = queueItems,
            queueCurrentIndex = queueIndex,
            queueCurrentItemId = queueCurrent?.optString("queue_item_id")?.takeIf { it.isNotBlank() }
                ?: playerCurrent?.optString("queue_item_id")?.takeIf { it.isNotBlank() },
            queueResumePosition = resumePos,
            queueElapsedTime = elapsedTime,
            queueDuration = duration,
            queueActive = queueActive,
            queueEnded = queueEnded,
            canPrevious = hasPrevious,
            canNext = hasNext,
            currentTrack = currentTrack,
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
            n == "this device" -> -1
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
