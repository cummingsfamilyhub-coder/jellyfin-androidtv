package org.jellyfin.androidtv.ui.mobile

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal data class VesperMusicRequestResult(
    val albumMbid: String,
    val albumName: String,
    val artistMbid: String,
    val artistName: String,
    val releaseDate: String?,
    val coverUrl: String?,
    val inLibrary: Boolean,
    val status: String,
)

internal data class VesperBookRequestResult(
    val bookId: String,
    val title: String,
    val author: String,
    val year: String?,
    val coverUrl: String?,
    val ebookStatus: String?,
    val audiobookStatus: String?,
)

internal data class VesperBookItem(
    val itemId: String,
    val libraryId: String,
    val title: String,
    val subtitle: String?,
    val author: String,
    val narrator: String?,
    val description: String?,
    val series: String?,
    val publishedYear: String?,
    val coverUrl: String?,
    val hasAudio: Boolean,
    val hasEbook: Boolean,
    val durationSeconds: Int,
    val addedAt: Long,
    val progress: Double? = null,
) {
    val mediaKind: String
        get() = when {
            hasAudio && hasEbook -> "Audiobook + eBook"
            hasAudio -> "Audiobook"
            hasEbook -> "eBook"
            else -> "Book"
        }
}

internal data class VesperBookPerson(
    val id: String,
    val name: String,
    val count: Int,
)

internal data class VesperBookSeries(
    val id: String,
    val name: String,
    val count: Int,
)

internal data class VesperBookHomeSnapshot(
    val continueListening: List<VesperBookItem> = emptyList(),
    val continueReading: List<VesperBookItem> = emptyList(),
    val recentlyAdded: List<VesperBookItem> = emptyList(),
    val discover: List<VesperBookItem> = emptyList(),
    val audiobooks: List<VesperBookItem> = emptyList(),
    val ebooks: List<VesperBookItem> = emptyList(),
    val authors: List<VesperBookPerson> = emptyList(),
    val series: List<VesperBookSeries> = emptyList(),
)

internal data class VesperBookLibraryResult(
    val itemId: String,
    val title: String,
    val author: String,
    val mediaKind: String,
)

internal class AurralRequestClient(
    baseUrl: String,
    private val apiKey: String,
) {
    private val root = baseUrl.trim().trimEnd('/')

    fun validateConnection() {
        request("GET", "/api/health")
    }

    fun searchAlbums(query: String, limit: Int = 18): List<VesperMusicRequestResult> {
        val clean = query.trim()
        if (clean.length < 2) return emptyList()
        val path = buildString {
            append("/api/search?q=")
            append(encode(clean))
            append("&scope=album&limit=")
            append(limit)
        }
        val response = request("GET", path) as? JSONObject ?: return emptyList()
        val items = response.optJSONArray("items") ?: JSONArray()

        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val albumMbid = item.optString("id").trim()
                val albumName = item.optString("title").trim()
                val artistMbid = item.optString("artistMbid").trim()
                val artistName = item.optString("artistName").trim()
                if (albumMbid.isBlank() || albumName.isBlank() || artistMbid.isBlank() || artistName.isBlank()) {
                    continue
                }
                add(
                    VesperMusicRequestResult(
                        albumMbid = albumMbid,
                        albumName = albumName,
                        artistMbid = artistMbid,
                        artistName = artistName,
                        releaseDate = item.optString("releaseDate").takeIf { it.isNotBlank() && it != "null" },
                        coverUrl = normalizeUrl(
                            item.optString("coverUrl").takeIf { it.isNotBlank() && it != "null" }
                        ),
                        inLibrary = item.optBoolean("inLibrary", false),
                        status = item.optString("status").ifBlank {
                            if (item.optBoolean("inLibrary", false)) "available" else "missing"
                        },
                    )
                )
            }
        }
    }

    fun requestAlbum(item: VesperMusicRequestResult): String {
        if (item.inLibrary || item.status == "available") return "Available"
        val body = JSONObject()
            .put("albumMbid", item.albumMbid)
            .put("albumName", item.albumName)
            .put("artistMbid", item.artistMbid)
            .put("artistName", item.artistName)
            .put("triggerSearch", true)

        val response = request("POST", "/api/library/albums/request", body) as? JSONObject
        return when (response?.optString("status")) {
            "available" -> "Available"
            "searching", "queued" -> "Requested"
            else -> "Requested"
        }
    }

    private fun request(method: String, path: String, body: JSONObject? = null): Any? {
        require(root.isNotBlank()) { "Music Requests URL is missing." }
        require(apiKey.isNotBlank()) { "Music Requests API key is missing." }

        val connection = (URL(root + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            doInput = true
            doOutput = body != null
            connectTimeout = 6000
            readTimeout = 20000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Api-Key", apiKey)
            if (body != null) setRequestProperty("Content-Type", "application/json")
        }

        try {
            if (body != null) {
                connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
            }
            val status = connection.responseCode
            val payload = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

            if (status !in 200..299) {
                val detail = runCatching {
                    val json = JSONObject(payload)
                    json.optString("message").ifBlank { json.optString("error") }
                }.getOrNull()?.takeIf { it.isNotBlank() }
                throw IllegalStateException(detail ?: "Music Requests returned HTTP $status")
            }
            if (payload.isBlank()) return null
            return JSONTokener(payload).nextValue()
        } finally {
            connection.disconnect()
        }
    }

    private fun normalizeUrl(value: String?): String? = when {
        value.isNullOrBlank() -> null
        value.startsWith("http://") || value.startsWith("https://") -> value
        value.startsWith("/") -> root + value
        else -> "$root/$value"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}

internal class LazyLibrarianRequestClient(
    baseUrl: String,
    private val apiKey: String,
) {
    private val root = baseUrl.trim().trimEnd('/')

    fun validateConnection() {
        command("getVersion")
    }

    fun searchBooks(query: String): List<VesperBookRequestResult> {
        val clean = query.trim()
        if (clean.length < 2) return emptyList()
        val result = command("findBook", mapOf("name" to clean))
        val array = when (result) {
            is JSONArray -> result
            is JSONObject -> result.optJSONArray("books") ?: result.optJSONArray("items") ?: JSONArray()
            else -> JSONArray()
        }

        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.stringAny("bookid", "BookID", "id", "bookId")
                val title = item.stringAny("bookname", "BookName", "title", "name")
                if (id.isBlank() || title.isBlank()) continue
                add(
                    VesperBookRequestResult(
                        bookId = id,
                        title = title,
                        author = item.stringAny("authorname", "AuthorName", "author", "authorName")
                            .ifBlank { "Unknown author" },
                        year = item.stringAny("bookdate", "BookDate", "year", "date")
                            .takeIf { it.isNotBlank() },
                        coverUrl = normalizeUrl(
                            item.stringAny("bookimg", "BookImg", "cover", "image")
                                .takeIf { it.isNotBlank() }
                        ),
                        ebookStatus = item.stringAny("status", "Status").takeIf { it.isNotBlank() },
                        audiobookStatus = item.stringAny("audiostatus", "AudioStatus").takeIf { it.isNotBlank() },
                    )
                )
            }
        }
    }

    fun requestBook(item: VesperBookRequestResult, audiobook: Boolean): String {
        val type = if (audiobook) "AudioBook" else "eBook"
        command("addBook", mapOf("id" to item.bookId))

        var queued = false
        for (attempt in 0 until 8) {
            val response = command(
                "queueBook",
                mapOf("id" to item.bookId, "type" to type),
            )
            val text = response?.toString().orEmpty()
            if (text.equals("OK", ignoreCase = true) || !text.contains("Invalid id", ignoreCase = true)) {
                queued = true
                break
            }
            if (attempt < 7) Thread.sleep(500)
        }
        if (!queued) {
            throw IllegalStateException("The book was found but could not be added to the request queue yet.")
        }

        runCatching {
            command(
                "searchBook",
                mapOf("id" to item.bookId, "type" to type),
            )
        }

        return if (audiobook) "Audiobook requested" else "Book requested"
    }

    private fun command(name: String, params: Map<String, String> = emptyMap()): Any? {
        require(root.isNotBlank()) { "Book Requests URL is missing." }
        require(apiKey.isNotBlank()) { "Book Requests API key is missing." }

        val query = buildList {
            add("apikey=${encode(apiKey)}")
            add("cmd=${encode(name)}")
            params.forEach { (key, value) -> add("${encode(key)}=${encode(value)}") }
        }.joinToString("&")

        val connection = (URL("$root/api?$query").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6000
            readTimeout = 20000
            setRequestProperty("Accept", "application/json")
        }

        try {
            val status = connection.responseCode
            val payload = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
                .trim()

            if (status !in 200..299) {
                throw IllegalStateException("Book Requests returned HTTP $status")
            }
            if (payload.isBlank()) return null
            return runCatching { JSONTokener(payload).nextValue() }.getOrElse { payload }
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.stringAny(vararg keys: String): String {
        keys.forEach { key ->
            val value = optString(key).trim()
            if (value.isNotBlank() && value != "null") return value
        }
        return ""
    }

    private fun normalizeUrl(value: String?): String? = when {
        value.isNullOrBlank() -> null
        value.startsWith("http://") || value.startsWith("https://") -> value
        value.startsWith("/") -> root + value
        else -> null
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}

internal class AudiobookshelfLibraryClient(
    baseUrl: String,
    private val token: String,
) {
    private val root = baseUrl.trim().trimEnd('/')

    fun validateConnection() {
        request("/api/libraries")
    }

    fun loadHome(limitPerShelf: Int = 14): VesperBookHomeSnapshot {
        val libraries = request("/api/libraries").optJSONArray("libraries") ?: JSONArray()
        val bookLibraries = buildList {
            for (index in 0 until libraries.length()) {
                val library = libraries.optJSONObject(index) ?: continue
                if (library.optString("mediaType") == "book") {
                    library.optString("id").takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
        if (bookLibraries.isEmpty()) return VesperBookHomeSnapshot()

        val continueListening = mutableListOf<VesperBookItem>()
        val continueReading = mutableListOf<VesperBookItem>()
        val recentlyAdded = mutableListOf<VesperBookItem>()
        val discover = mutableListOf<VesperBookItem>()
        val allItems = mutableListOf<VesperBookItem>()
        val authors = mutableListOf<VesperBookPerson>()
        val series = mutableListOf<VesperBookSeries>()

        for (libraryId in bookLibraries) {
            val shelves = request(
                "/api/libraries/${encodePath(libraryId)}/personalized?limit=$limitPerShelf"
            ).optJSONArray("items") ?: run {
                val raw = requestAny(
                    "/api/libraries/${encodePath(libraryId)}/personalized?limit=$limitPerShelf"
                )
                raw as? JSONArray ?: JSONArray()
            }

            for (index in 0 until shelves.length()) {
                val shelf = shelves.optJSONObject(index) ?: continue
                val id = shelf.optString("id")
                val entities = shelf.optJSONArray("entities") ?: JSONArray()
                val parsed = buildList {
                    for (entityIndex in 0 until entities.length()) {
                        entities.optJSONObject(entityIndex)?.let { parseBookItem(it) }?.let(::add)
                    }
                }
                when (id) {
                    "continue-listening" -> continueListening += parsed
                    "continue-reading" -> continueReading += parsed
                    "recently-added" -> recentlyAdded += parsed
                    "discover" -> discover += parsed
                }
            }

            val itemsPayload = request(
                "/api/libraries/${encodePath(libraryId)}/items?limit=80&page=0&sort=addedAt&desc=1"
            )
            val results = itemsPayload.optJSONArray("results") ?: JSONArray()
            for (index in 0 until results.length()) {
                results.optJSONObject(index)?.let { parseBookItem(it) }?.let(allItems::add)
            }

            val authorsPayload = request(
                "/api/libraries/${encodePath(libraryId)}/authors?limit=20&page=0"
            )
            val authorResults = authorsPayload.optJSONArray("results") ?: JSONArray()
            for (index in 0 until authorResults.length()) {
                val item = authorResults.optJSONObject(index) ?: continue
                val name = item.optString("name").trim()
                val id = item.optString("id").trim()
                if (id.isNotBlank() && name.isNotBlank()) {
                    authors += VesperBookPerson(
                        id = id,
                        name = name,
                        count = item.optInt("numBooks", item.optInt("numItems", 0)),
                    )
                }
            }

            val seriesPayload = request(
                "/api/libraries/${encodePath(libraryId)}/series?limit=20&page=0&sort=name&desc=0"
            )
            val seriesResults = seriesPayload.optJSONArray("results") ?: JSONArray()
            for (index in 0 until seriesResults.length()) {
                val item = seriesResults.optJSONObject(index) ?: continue
                val name = item.optString("name").trim()
                val id = item.optString("id").trim()
                if (id.isNotBlank() && name.isNotBlank()) {
                    series += VesperBookSeries(
                        id = id,
                        name = name,
                        count = item.optInt("numBooks", item.optInt("bookCount", 0)),
                    )
                }
            }
        }

        val deduped = allItems.distinctBy { it.itemId }
        return VesperBookHomeSnapshot(
            continueListening = continueListening.distinctBy { it.itemId },
            continueReading = continueReading.distinctBy { it.itemId },
            recentlyAdded = recentlyAdded.distinctBy { it.itemId },
            discover = discover.distinctBy { it.itemId },
            audiobooks = deduped.filter { it.hasAudio }.take(24),
            ebooks = deduped.filter { it.hasEbook }.take(24),
            authors = authors.distinctBy { it.id },
            series = series.distinctBy { it.id },
        )
    }

    fun loadItem(itemId: String): VesperBookItem {
        val response = request("/api/items/${encodePath(itemId)}?expanded=1&include=progress")
        return parseBookItem(response)
            ?: throw IllegalStateException("Book details are unavailable.")
    }

    fun search(query: String, limit: Int = 12): List<VesperBookLibraryResult> {
        val clean = query.trim()
        if (clean.length < 2) return emptyList()

        val libraries = request("/api/libraries").optJSONArray("libraries") ?: JSONArray()
        val bookLibraries = buildList {
            for (index in 0 until libraries.length()) {
                val library = libraries.optJSONObject(index) ?: continue
                if (library.optString("mediaType") == "book") {
                    library.optString("id").takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }

        val results = mutableListOf<VesperBookLibraryResult>()
        for (libraryId in bookLibraries) {
            if (results.size >= limit) break
            val response = request(
                "/api/libraries/${encodePath(libraryId)}/search?q=${encode(clean)}&limit=${limit - results.size}"
            )
            val books = response.optJSONArray("book") ?: JSONArray()
            for (index in 0 until books.length()) {
                val hit = books.optJSONObject(index) ?: continue
                val libraryItem = hit.optJSONObject("libraryItem") ?: continue
                val media = libraryItem.optJSONObject("media") ?: JSONObject()
                val metadata = media.optJSONObject("metadata") ?: JSONObject()
                val title = metadata.optString("title").trim()
                if (title.isBlank()) continue

                val author = metadata.optString("authorName").trim()
                    .ifBlank {
                        val authors = metadata.optJSONArray("authors") ?: JSONArray()
                        buildList {
                            for (authorIndex in 0 until authors.length()) {
                                authors.optJSONObject(authorIndex)
                                    ?.optString("name")
                                    ?.trim()
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let(::add)
                            }
                        }.joinToString(", ")
                    }
                    .ifBlank { "Unknown author" }

                val audioFiles = media.optInt("numAudioFiles", 0)
                val ebookFormat = media.optString("ebookFileFormat").trim()
                val kind = when {
                    audioFiles > 0 && ebookFormat.isNotBlank() -> "Audiobook + eBook"
                    audioFiles > 0 -> "Audiobook"
                    ebookFormat.isNotBlank() -> "eBook"
                    else -> "Book"
                }

                results += VesperBookLibraryResult(
                    itemId = libraryItem.optString("id"),
                    title = title,
                    author = author,
                    mediaKind = kind,
                )
                if (results.size >= limit) break
            }
        }
        return results
    }

    private fun parseBookItem(libraryItem: JSONObject): VesperBookItem? {
        val media = libraryItem.optJSONObject("media") ?: return null
        val metadata = media.optJSONObject("metadata") ?: JSONObject()
        val itemId = libraryItem.optString("id").trim()
        val title = metadata.optString("title").trim()
        if (itemId.isBlank() || title.isBlank()) return null

        val author = metadata.optString("authorName").trim()
            .ifBlank {
                val arr = metadata.optJSONArray("authors") ?: JSONArray()
                buildList {
                    for (index in 0 until arr.length()) {
                        arr.optJSONObject(index)?.optString("name")?.trim()
                            ?.takeIf { it.isNotBlank() }?.let(::add)
                    }
                }.joinToString(", ")
            }
            .ifBlank { "Unknown author" }

        val progressObject = libraryItem.optJSONObject("userMediaProgress")
        val progress = progressObject?.let {
            when {
                it.optDouble("ebookProgress", 0.0) > 0 -> it.optDouble("ebookProgress", 0.0)
                it.optDouble("progress", 0.0) > 0 -> it.optDouble("progress", 0.0)
                it.optDouble("duration", 0.0) > 0 ->
                    (it.optDouble("currentTime", 0.0) / it.optDouble("duration", 1.0)).coerceIn(0.0, 1.0)
                else -> null
            }
        }

        val coverUrl = "$root/api/items/${encodePath(itemId)}/cover?token=${encode(token)}"
        return VesperBookItem(
            itemId = itemId,
            libraryId = libraryItem.optString("libraryId"),
            title = title,
            subtitle = metadata.optString("subtitle").takeIf { it.isNotBlank() && it != "null" },
            author = author,
            narrator = metadata.optString("narratorName").takeIf { it.isNotBlank() && it != "null" },
            description = metadata.optString("descriptionPlain")
                .takeIf { it.isNotBlank() && it != "null" }
                ?: metadata.optString("description").takeIf { it.isNotBlank() && it != "null" },
            series = metadata.optString("seriesName").takeIf { it.isNotBlank() && it != "null" },
            publishedYear = metadata.optString("publishedYear").takeIf { it.isNotBlank() && it != "null" },
            coverUrl = coverUrl,
            hasAudio = media.optInt("numTracks", media.optInt("numAudioFiles", 0)) > 0,
            hasEbook = media.optString("ebookFormat").isNotBlank(),
            durationSeconds = media.optDouble("duration", 0.0).toInt(),
            addedAt = libraryItem.optLong("addedAt", 0L),
            progress = progress,
        )
    }

    private fun request(path: String): JSONObject =
        requestAny(path) as? JSONObject ?: JSONObject()

    private fun requestAny(path: String): Any? {
        require(root.isNotBlank()) { "Audiobook Library URL is missing." }
        require(token.isNotBlank()) { "Audiobook Library token is missing." }

        val connection = (URL(root + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
        }

        try {
            val status = connection.responseCode
            val payload = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException("Audiobook Library returned HTTP $status")
            }
            if (payload.isBlank()) return JSONObject()
            return JSONTokener(payload).nextValue()
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun encodePath(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}
