package org.jellyfin.androidtv.ui.mobile

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.EditText
import android.text.InputType
import android.app.AlertDialog
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.RadioGroup
import android.widget.RadioButton
import android.widget.LinearLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.VesperServiceConfig
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import org.jellyfin.androidtv.ui.preference.PreferencesActivity
import org.jellyfin.androidtv.data.repository.ItemMutationRepository
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Popup
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
import org.koin.android.ext.android.inject
import java.nio.charset.StandardCharsets
import java.net.URLEncoder
import java.net.URL
import java.net.HttpURLConnection
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.json.JSONArray
import org.json.JSONObject

class MobileMainActivity : FragmentActivity() {
    private val api by inject<ApiClient>()
    private val sessionRepository by inject<SessionRepository>()
    private val serverRepository by inject<ServerRepository>()
    private val userRepository by inject<UserRepository>()
    private val itemMutationRepository by inject<ItemMutationRepository>()

    private var state by mutableStateOf(MobileHomeState())
    private var popularity by mutableStateOf(PopularityState())
    private var selected by mutableStateOf<BaseItemDto?>(null)
    private var tmdbApiKey by mutableStateOf("")
    private var seerrApiKey by mutableStateOf("")
    private var popularityScope by mutableStateOf(PopularityScope.GLOBAL)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (sessionRepository.currentSession.value == null || userRepository.currentUser.value == null) {
            startActivity(Intent(this, MobileStartupActivity::class.java))
            finish()
            return
        }

        val vesperPreferences = getSharedPreferences("vesper", MODE_PRIVATE)
        tmdbApiKey = vesperPreferences
            .getString("tmdb_api_key", "")
            .orEmpty()
        seerrApiKey = vesperPreferences
            .getString("seerr_api_key", "")
            .orEmpty()
        if (vesperPreferences.contains("seerr_url")) {
            vesperPreferences.edit().remove("seerr_url").apply()
        }
        popularityScope = runCatching {
            PopularityScope.valueOf(
                vesperPreferences.getString("popularity_scope", PopularityScope.GLOBAL.name)
                    ?: PopularityScope.GLOBAL.name
            )
        }.getOrDefault(PopularityScope.GLOBAL)

        setContent {
            VesperMobile(
                state = state,
                popularity = popularity,
                popularityScope = popularityScope,
                tmdbConfigured = tmdbApiKey.isNotBlank(),
                selected = selected,
                userName = userRepository.currentUser.value?.name ?: "Vesper",
                api = api,
                seerrConfigured = seerrApiKey.isNotBlank(),
                onSeerrSearch = ::searchSeerr,
                onSeerrRequest = ::requestSeerr,
                onSelect = { selected = it },
                onBack = { selected = null },
                onRetry = ::loadHome,
                onPlay = ::playItem,
                onToggleFavorite = ::toggleFavorite,
                onSwitchProfile = ::switchProfile,
                onSettings = ::openSettings,
            )
        }

        loadHome()
    }

    private fun loadHome() {
        state = MobileHomeState(loading = true)
        lifecycleScope.launch {
            state = runCatching {
                withContext(Dispatchers.IO) {
                    val resume = async {
                        api.itemsApi.getResumeItems(
                            fields = ItemRepository.browseFields,
                            imageTypeLimit = 1,
                            limit = 20,
                            mediaTypes = listOf(MediaType.VIDEO),
                            includeItemTypes = listOf(BaseItemKind.EPISODE, BaseItemKind.MOVIE),
                            excludeActiveSessions = true,
                        ).content.items
                    }
                    val favorites = async {
                        api.itemsApi.getItems(
                            fields = ItemRepository.browseFields,
                            includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                            recursive = true,
                            filters = setOf(ItemFilter.IS_FAVORITE),
                            imageTypeLimit = 1,
                            limit = 500,
                            sortBy = setOf(ItemSortBy.PLAY_COUNT),
                            sortOrder = setOf(SortOrder.DESCENDING),
                        ).content.items
                    }
                    val movies = async {
                        api.itemsApi.getItems(
                            fields = ItemRepository.browseFields,
                            includeItemTypes = setOf(BaseItemKind.MOVIE),
                            recursive = true,
                            imageTypeLimit = 1,
                            limit = 500,
                            sortBy = setOf(ItemSortBy.PLAY_COUNT),
                            sortOrder = setOf(SortOrder.DESCENDING),
                        ).content.items
                    }
                    val shows = async {
                        api.itemsApi.getItems(
                            fields = ItemRepository.browseFields,
                            includeItemTypes = setOf(BaseItemKind.SERIES),
                            recursive = true,
                            imageTypeLimit = 1,
                            limit = 500,
                            sortBy = setOf(ItemSortBy.PLAY_COUNT),
                            sortOrder = setOf(SortOrder.DESCENDING),
                        ).content.items
                    }
                    val boxSets = async {
                        api.itemsApi.getItems(
                            fields = ItemRepository.browseFields,
                            includeItemTypes = setOf(BaseItemKind.BOX_SET),
                            recursive = true,
                            imageTypeLimit = 1,
                            limit = 100,
                            sortBy = setOf(ItemSortBy.SORT_NAME),
                        ).content.items
                    }

                    val allCollections = boxSets.await()
                    val favoriteItems = favorites.await().filterNot(::isServiceArtifact)
                    val movieItems = movies.await().filterNot(::isServiceArtifact)
                    val showItems = shows.await().filterNot(::isServiceArtifact)

                    MobileHomeState(
                        continueWatching = resume.await().filterNot(::isServiceArtifact),
                        myV = favoriteItems,
                        movies = movieItems,
                        shows = showItems,
                        services = allCollections.filter(::isServiceCollection),
                        collections = allCollections.filterNot(::isServiceCollection),
                    )
                }
            }.getOrElse { error ->
                MobileHomeState(error = friendlyServiceError("Jellyfin", error))
            }
            if (state.error == null) loadPopularity()
        }
    }

    private fun loadPopularity() {
        val session = sessionRepository.currentSession.value
        val server = serverRepository.currentServer.value
        val key = tmdbApiKey.trim()

        lifecycleScope.launch {
            popularity = withContext(Dispatchers.IO) {
                val localMovies = if (session != null && server != null) {
                    fetchPlaybackPopularity(
                        server.address,
                        session.accessToken,
                        "MoviesReport",
                    )
                } else emptyMap()
                val localShows = if (session != null && server != null) {
                    fetchPlaybackPopularity(
                        server.address,
                        session.accessToken,
                        "GetTvShowsReport",
                    )
                } else emptyMap()

                val globalMovies = if (key.isNotBlank()) fetchTmdbTrending("movie", key) else emptyMap()
                val globalShows = if (key.isNotBlank()) fetchTmdbTrending("tv", key) else emptyMap()

                PopularityState(
                    localMovies = localMovies,
                    localShows = localShows,
                    householdLocalAvailable = localMovies.isNotEmpty() || localShows.isNotEmpty(),
                    globalMovies = globalMovies,
                    globalShows = globalShows,
                    globalAvailable = globalMovies.isNotEmpty() || globalShows.isNotEmpty(),
                )
            }
        }
    }

    private fun fetchPlaybackPopularity(
        serverAddress: String,
        token: String,
        endpoint: String,
    ): Map<String, Int> = runCatching {
        val url = URL("${serverAddress.trimEnd('/')}/user_usage_stats/$endpoint?days=30")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = VesperServiceConfig.CONNECT_TIMEOUT_MS
            readTimeout = VesperServiceConfig.READ_TIMEOUT_MS
            setRequestProperty("X-Emby-Token", token)
            setRequestProperty("Accept", "application/json")
        }
        if (connection.responseCode !in 200..299) {
            connection.disconnect()
            return@runCatching emptyMap()
        }

        val body = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        val array = JSONArray(body)
        buildMap {
            for (index in 0 until array.length()) {
                val row = array.optJSONObject(index) ?: continue
                val label = row.optString("label").trim()
                val count = row.optInt("count", 0)
                if (label.isNotBlank()) put(label.lowercase(), count)
            }
        }
    }.getOrDefault(emptyMap())

    private fun fetchTmdbTrending(
        mediaType: String,
        apiKey: String,
    ): Map<Int, Int> = runCatching {
        val encoded = URLEncoder.encode(apiKey, StandardCharsets.UTF_8.name())
        buildMap {
            var rank = 0
            for (page in 1..5) {
                val url = URL(
                    "https://api.themoviedb.org/3/trending/$mediaType/week?api_key=$encoded&page=$page"
                )
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 10000
                    setRequestProperty("Accept", "application/json")
                }
                if (connection.responseCode !in 200..299) {
                    connection.disconnect()
                    break
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val results = org.json.JSONObject(body).optJSONArray("results") ?: JSONArray()
                for (index in 0 until results.length()) {
                    val id = results.optJSONObject(index)?.optInt("id", -1) ?: -1
                    if (id > 0 && !containsKey(id)) put(id, rank++)
                }
            }
        }
    }.getOrDefault(emptyMap())

    private fun toggleFavorite(item: BaseItemDto) {
        lifecycleScope.launch {
            val favorite = item.userData?.isFavorite == true
            val data = runCatching {
                itemMutationRepository.setFavorite(item.id, !favorite)
            }.getOrNull() ?: return@launch

            fun updated(list: List<BaseItemDto>) = list.map { existing ->
                if (existing.id == item.id) existing.copy(userData = data) else existing
            }

            val updatedItem = item.copy(userData = data)
            state = state.copy(
                continueWatching = updated(state.continueWatching),
                myV = if (data.isFavorite == true) {
                    (updated(state.myV) + updatedItem).distinctBy { it.id }
                } else {
                    state.myV.filterNot { it.id == item.id }
                },
                movies = updated(state.movies),
                shows = updated(state.shows),
                services = updated(state.services),
                collections = updated(state.collections),
            )
            if (selected?.id == item.id) selected = updatedItem
        }
    }

    private fun switchProfile() {
        val serverId = sessionRepository.currentSession.value?.serverId ?: return
        sessionRepository.destroyCurrentSession()
        startActivity(
            Intent(this, MobileStartupActivity::class.java)
                .putExtra(MobileStartupActivity.EXTRA_SWITCH_SERVER_ID, serverId.toString())
        )
        finish()
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun friendlyServiceError(service: String, error: Throwable): String {
        if (!isNetworkAvailable()) return "No network connection. Check Wi-Fi or mobile data and try again."

        val causes = generateSequence<Throwable>(error) { it.cause }.toList()
        val details = causes
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase()

        return when {
            causes.any { it is SocketTimeoutException } || details.contains("timeout") ->
                "$service timed out. Try again."
            details.contains("401") || details.contains("unauthorized") ||
                (details.contains("authentication") && details.contains("expired")) ->
                "$service authentication has expired. Sign in again."
            causes.any { it is UnknownHostException || it is ConnectException || it is SSLException } ->
                "$service is unreachable. Check your connection and try again."
            else -> "$service is unavailable right now. Try again."
        }
    }

    private fun seerrHttpError(status: Int): String = when (status) {
        401, 403 -> "Seerr authentication failed. Check the API key in Settings."
        408 -> "Seerr timed out. Try again."
        in 500..599 -> "Seerr is unavailable right now. Try again."
        else -> "Seerr request failed. Try again."
    }

    private fun encodeSeerrQueryValue(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
            .replace("*", "%2A")

    private suspend fun searchSeerr(query: String): List<SeerrSearchResult> =
        withContext(Dispatchers.IO) {
            val baseUrl = VesperServiceConfig.SEERR_BASE_URL
            val apiKey = seerrApiKey.trim()
            val cleanQuery = query.trim()
            if (apiKey.isBlank() || cleanQuery.length < 2) {
                return@withContext emptyList()
            }

            val encodedQuery = encodeSeerrQueryValue(cleanQuery)
            val url = URL("${baseUrl}/api/v1/search?query=${encodedQuery}&page=1")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = VesperServiceConfig.CONNECT_TIMEOUT_MS
                readTimeout = VesperServiceConfig.READ_TIMEOUT_MS
                setRequestProperty("X-Api-Key", apiKey)
                setRequestProperty("Accept", "application/json")
            }

            try {
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException(seerrHttpError(connection.responseCode))
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val results = JSONObject(body).optJSONArray("results") ?: JSONArray()

                buildList {
                    for (index in 0 until results.length()) {
                        val item = results.optJSONObject(index) ?: continue
                        val mediaType = item.optString("mediaType")
                        if (mediaType != "movie" && mediaType != "tv") continue

                        val title = if (mediaType == "movie") {
                            item.optString("title")
                        } else {
                            item.optString("name")
                        }
                        if (title.isBlank()) continue

                        val date = if (mediaType == "movie") {
                            item.optString("releaseDate")
                        } else {
                            item.optString("firstAirDate")
                        }
                        val year = date.take(4).toIntOrNull()
                        val posterPath = item.optString("posterPath")
                            .takeIf { it.isNotBlank() && it != "null" }
                        val mediaInfo = item.optJSONObject("mediaInfo")
                        val mediaStatus = mediaInfo?.optInt("status", SEERR_STATUS_UNKNOWN)
                            ?: SEERR_STATUS_UNKNOWN

                        add(
                            SeerrSearchResult(
                                tmdbId = item.optInt("id"),
                                mediaType = mediaType,
                                title = title,
                                year = year,
                                overview = item.optString("overview"),
                                posterUrl = posterPath?.let {
                                    "https://image.tmdb.org/t/p/w500${it}"
                                },
                                mediaStatus = mediaStatus,
                            )
                        )
                    }
                }
            } catch (error: Exception) {
                if (error is IllegalStateException && error.message?.startsWith("Seerr ") == true) {
                    throw error
                }
                throw IllegalStateException(friendlyServiceError("Seerr", error), error)
            } finally {
                connection.disconnect()
            }
        }

    private suspend fun requestSeerr(item: SeerrSearchResult): String? =
        withContext(Dispatchers.IO) {
            val baseUrl = VesperServiceConfig.SEERR_BASE_URL
            val apiKey = seerrApiKey.trim()
            if (apiKey.isBlank()) {
                return@withContext "Add the Seerr API key in Vesper settings first."
            }

            val connection = (URL("${baseUrl}/api/v1/request").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = VesperServiceConfig.CONNECT_TIMEOUT_MS
                readTimeout = VesperServiceConfig.REQUEST_READ_TIMEOUT_MS
                setRequestProperty("X-Api-Key", apiKey)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json")
            }

            try {
                val payload = JSONObject()
                    .put("mediaType", item.mediaType)
                    .put("mediaId", item.tmdbId)
                if (item.mediaType == "tv") {
                    payload.put("seasons", "all")
                }

                connection.outputStream.bufferedWriter().use {
                    it.write(payload.toString())
                }

                when (connection.responseCode) {
                    200, 201 -> null
                    202 -> "No new seasons are available to request."
                    409 -> "Already requested."
                    else -> seerrHttpError(connection.responseCode)
                }
            } catch (error: Exception) {
                friendlyServiceError("Seerr", error)
            } finally {
                connection.disconnect()
            }
        }

    private fun openSettings() {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), 0)
        }

        container.addView(TextView(this).apply {
            text = "Popularity source"
            textSize = 15f
            setPadding(0, dp(8), 0, dp(4))
        })

        val popularityGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
        }
        val globalRadio = RadioButton(this).apply {
            text = "Global"
            isChecked = popularityScope == PopularityScope.GLOBAL
        }
        val localRadio = RadioButton(this).apply {
            text = "Local"
            isChecked = popularityScope == PopularityScope.LOCAL
        }
        popularityGroup.addView(globalRadio)
        popularityGroup.addView(localRadio)
        container.addView(popularityGroup)

        container.addView(TextView(this).apply {
            text = "Global uses TMDb weekly popularity. Local uses Vesper/Jellyfin viewing activity."
            textSize = 12f
            alpha = 0.72f
            setPadding(0, 0, 0, dp(14))
        })

        container.addView(TextView(this).apply {
            text = "TMDb v3 API key"
            textSize = 15f
            setPadding(0, 0, 0, dp(4))
        })

        val input = EditText(this).apply {
            setText(tmdbApiKey)
            hint = "TMDb v3 API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSelectAllOnFocus(false)
            setSingleLine(true)
        }
        container.addView(input)

        container.addView(TextView(this).apply {
            text = "Seerr service"
            textSize = 15f
            setPadding(0, dp(14), 0, dp(4))
        })

        container.addView(TextView(this).apply {
            text = VesperServiceConfig.SEERR_BASE_URL
            textSize = 13f
            alpha = 0.72f
            setPadding(0, 0, 0, dp(6))
        })

        container.addView(TextView(this).apply {
            text = "Seerr API key"
            textSize = 15f
            setPadding(0, dp(12), 0, dp(4))
        })

        val seerrKeyInput = EditText(this).apply {
            setText(seerrApiKey)
            hint = "Seerr API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSelectAllOnFocus(false)
            setSingleLine(true)
        }
        container.addView(seerrKeyInput)

        container.addView(TextView(this).apply {
            text = "Used for Search and one-tap media requests."
            textSize = 12f
            alpha = 0.72f
            setPadding(0, 0, 0, dp(8))
        })

        AlertDialog.Builder(this)
            .setTitle("Vesper settings")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                tmdbApiKey = input.text?.toString()?.trim().orEmpty()
                seerrApiKey = seerrKeyInput.text?.toString()?.trim().orEmpty()
                popularityScope = if (localRadio.isChecked) {
                    PopularityScope.LOCAL
                } else {
                    PopularityScope.GLOBAL
                }

                getSharedPreferences("vesper", MODE_PRIVATE)
                    .edit()
                    .putString("tmdb_api_key", tmdbApiKey)
                    .remove("seerr_url")
                    .putString("seerr_api_key", seerrApiKey)
                    .putString("popularity_scope", popularityScope.name)
                    .apply()

                loadPopularity()
            }
            .setNeutralButton("Jellyfin settings") { _, _ ->
                startActivity(Intent(this, PreferencesActivity::class.java))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun playItem(item: BaseItemDto) {
        startActivity(
            Intent(this, MobilePlayerActivity::class.java)
                .putExtra(MobilePlayerActivity.EXTRA_ITEM_ID, item.id.toString())
        )
    }
}

private data class PopularityState(
    val localMovies: Map<String, Int> = emptyMap(),
    val localShows: Map<String, Int> = emptyMap(),
    val householdLocalAvailable: Boolean = false,
    val globalMovies: Map<Int, Int> = emptyMap(),
    val globalShows: Map<Int, Int> = emptyMap(),
    val globalAvailable: Boolean = false,
)

private enum class PopularityScope {
    GLOBAL,
    LOCAL,
}

private const val SEERR_STATUS_UNKNOWN = 1
private const val SEERR_STATUS_PENDING = 2
private const val SEERR_STATUS_PROCESSING = 3
private const val SEERR_STATUS_PARTIALLY_AVAILABLE = 4
private const val SEERR_STATUS_AVAILABLE = 5
private const val SEERR_STATUS_BLOCKLISTED = 6
private const val SEERR_STATUS_DELETED = 7

private data class SeerrSearchResult(
    val tmdbId: Int,
    val mediaType: String,
    val title: String,
    val year: Int?,
    val overview: String,
    val posterUrl: String?,
    val mediaStatus: Int,
)

private enum class LibrarySort(val label: String) {
    POPULARITY("Popularity"),
    NEWEST("Newest"),
    AZ("A–Z"),
    YEAR("Year"),
}

private fun BaseItemDto.tmdbId(): Int? =
    providerIds
        ?.entries
        ?.firstOrNull { it.key.equals("Tmdb", ignoreCase = true) }
        ?.value
        ?.toIntOrNull()

private fun localPopularityScore(
    item: BaseItemDto,
    popularity: PopularityState,
): Int {
    val name = item.name?.trim()?.lowercase().orEmpty()
    return when (item.type) {
        BaseItemKind.MOVIE -> popularity.localMovies[name] ?: -1
        BaseItemKind.SERIES -> popularity.localShows[name] ?: -1
        else -> -1
    }
}

private fun globalPopularityRank(
    item: BaseItemDto,
    popularity: PopularityState,
): Int {
    val id = item.tmdbId() ?: return Int.MAX_VALUE
    return when (item.type) {
        BaseItemKind.MOVIE -> popularity.globalMovies[id] ?: Int.MAX_VALUE
        BaseItemKind.SERIES -> popularity.globalShows[id] ?: Int.MAX_VALUE
        else -> Int.MAX_VALUE
    }
}

private fun sortByPopularity(
    media: List<BaseItemDto>,
    popularity: PopularityState,
    scope: PopularityScope,
): List<BaseItemDto> = when (scope) {
    PopularityScope.GLOBAL -> media.sortedWith(
        compareBy<BaseItemDto> { globalPopularityRank(it, popularity) }
            .thenByDescending { it.communityRating ?: -1f }
            .thenBy { it.name.orEmpty().lowercase() }
    )
    PopularityScope.LOCAL -> {
        if (popularity.householdLocalAvailable) {
            media.sortedWith(
                compareByDescending<BaseItemDto> { localPopularityScore(it, popularity) }
                    .thenBy { it.name.orEmpty().lowercase() }
            )
        } else media
    }
}

private fun sortLibrary(
    media: List<BaseItemDto>,
    sort: LibrarySort,
    popularity: PopularityState,
    scope: PopularityScope,
): List<BaseItemDto> = when (sort) {
    LibrarySort.POPULARITY -> sortByPopularity(media, popularity, scope)
    LibrarySort.NEWEST -> media.sortedWith(
        compareByDescending<BaseItemDto> { it.productionYear ?: Int.MIN_VALUE }
            .thenBy { it.name.orEmpty().lowercase() }
    )
    LibrarySort.AZ -> media.sortedBy { it.name.orEmpty().lowercase() }
    LibrarySort.YEAR -> media.sortedWith(
        compareByDescending<BaseItemDto> { it.productionYear ?: Int.MIN_VALUE }
            .thenBy { it.name.orEmpty().lowercase() }
    )
}

private data class MobileHomeState(
    val loading: Boolean = false,
    val error: String? = null,
    val continueWatching: List<BaseItemDto> = emptyList(),
    val myV: List<BaseItemDto> = emptyList(),
    val movies: List<BaseItemDto> = emptyList(),
    val shows: List<BaseItemDto> = emptyList(),
    val services: List<BaseItemDto> = emptyList(),
    val collections: List<BaseItemDto> = emptyList(),
)

private val serviceCollectionAliases = setOf(
    "netflix",
    "disney+",
    "disney plus",
    "prime video",
    "amazon prime",
    "amazon prime video",
    "max",
    "hbo max",
    "apple tv",
    "apple tv+",
    "apple tv plus",
    "paramount+",
    "paramount plus",
    "peacock",
    "hulu",
    "bbc iplayer",
    "itvx",
    "now",
    "now tv",
)

private fun isServiceCollection(item: BaseItemDto): Boolean {
    val name = item.name?.trim()?.lowercase() ?: return false
    return name.startsWith("streaming:") || name in serviceCollectionAliases
}

private fun isServiceArtifact(item: BaseItemDto): Boolean {
    val name = item.name?.trim()?.lowercase() ?: return false
    return name.startsWith("streaming:") || name in serviceCollectionAliases
}

private fun serviceDisplayName(name: String?): String =
    name.orEmpty()
        .replace(Regex("^\\s*streaming:\\s*", RegexOption.IGNORE_CASE), "")
        .ifBlank { "Streaming service" }

private fun serviceLogoResource(name: String?): Int? {
    val normalized = serviceDisplayName(name).trim().lowercase()

    return when {
        normalized.contains("netflix") -> R.drawable.logo_netflix
        normalized.contains("disney") -> R.drawable.logo_disneyplus
        normalized.contains("amazon") || normalized.contains("prime") -> R.drawable.logo_primevideo
        normalized.contains("apple") -> R.drawable.logo_appletv
        normalized.contains("paramount") -> R.drawable.logo_paramountplus
        normalized == "max" || normalized.contains("hbo") -> R.drawable.logo_max
        normalized.startsWith("now") -> R.drawable.logo_now
        else -> null
    }
}

private fun collectionDisplayName(name: String?): String =
    name.orEmpty()
        .replace(Regex("\\s+collection$", RegexOption.IGNORE_CASE), "")
        .ifBlank { "Collection" }

private enum class MobileTab(val label: String, val icon: String) {
    HOME("Home", "⌂"),
    MOVIES("Movies", "▣"),
    TV("TV", "▤"),
    MYV("MyV", "♥"),
    SEARCH("Search", "⌕"),
}

@Composable
private fun VesperMobile(
    state: MobileHomeState,
    popularity: PopularityState,
    popularityScope: PopularityScope,
    tmdbConfigured: Boolean,
    selected: BaseItemDto?,
    userName: String,
    api: ApiClient,
    seerrConfigured: Boolean,
    onSeerrSearch: suspend (String) -> List<SeerrSearchResult>,
    onSeerrRequest: suspend (SeerrSearchResult) -> String?,
    onSelect: (BaseItemDto) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (BaseItemDto) -> Unit,
    onToggleFavorite: (BaseItemDto) -> Unit,
    onSwitchProfile: () -> Unit,
    onSettings: () -> Unit,
) {
    var tab by remember { mutableStateOf(MobileTab.HOME) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF05080C))
            .statusBarsPadding()
    ) {
        val expanded = maxWidth >= 600.dp

        if (selected != null) {
            BackHandler(onBack = onBack)
            if (selected.type == BaseItemKind.BOX_SET) {
                CollectionBrowser(
                    collection = selected,
                    service = isServiceCollection(selected),
                    api = api,
                    onBack = onBack,
                    onSelect = onSelect,
                    onToggleFavorite = onToggleFavorite,
                    expanded = expanded,
                )
            } else {
                MobileDetails(selected, api, onBack, onPlay, onToggleFavorite)
            }
        } else {
            BackHandler(enabled = tab != MobileTab.HOME) { tab = MobileTab.HOME }

            when (tab) {
                MobileTab.HOME -> MobileHome(
                    state = state,
                    popularity = popularity,
                    popularityScope = popularityScope,
                    userName = userName,
                    api = api,
                    onSelect = onSelect,
                    onRetry = onRetry,
                    onPlay = onPlay,
                    onToggleFavorite = onToggleFavorite,
                    onSwitchProfile = onSwitchProfile,
                    onSettings = onSettings,
                    expanded = expanded,
                )
                MobileTab.MOVIES -> LibraryBrowse(
                    title = "Movies",
                    media = state.movies,
                    api = api,
                    onSelect = onSelect,
                    onToggleFavorite = onToggleFavorite,
                    popularity = popularity,
                    popularityScope = popularityScope,
                    tmdbConfigured = tmdbConfigured,
                    expanded = expanded,
                )
                MobileTab.TV -> LibraryBrowse(
                    title = "TV Shows",
                    media = state.shows,
                    api = api,
                    onSelect = onSelect,
                    onToggleFavorite = onToggleFavorite,
                    popularity = popularity,
                    popularityScope = popularityScope,
                    tmdbConfigured = tmdbConfigured,
                    expanded = expanded,
                )
                MobileTab.MYV -> LibraryBrowse(
                    title = "MyV",
                    media = state.myV,
                    api = api,
                    onSelect = onSelect,
                    onToggleFavorite = onToggleFavorite,
                    popularity = popularity,
                    popularityScope = popularityScope,
                    tmdbConfigured = tmdbConfigured,
                    expanded = expanded,
                )
                MobileTab.SEARCH -> SearchBrowse(
                    state = state,
                    api = api,
                    seerrConfigured = seerrConfigured,
                    onSeerrSearch = onSeerrSearch,
                    onSeerrRequest = onSeerrRequest,
                    onSelect = onSelect,
                    onToggleFavorite = onToggleFavorite,
                    expanded = expanded,
                )
            }

            MobileBottomNav(
                active = tab,
                onSelect = { tab = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

    }
}

@Composable
private fun MobileHome(
    state: MobileHomeState,
    popularity: PopularityState,
    popularityScope: PopularityScope,
    userName: String,
    api: ApiClient,
    onSelect: (BaseItemDto) -> Unit,
    onRetry: () -> Unit,
    onPlay: (BaseItemDto) -> Unit,
    onToggleFavorite: (BaseItemDto) -> Unit,
    onSwitchProfile: () -> Unit,
    onSettings: () -> Unit,
    expanded: Boolean,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 92.dp),
    ) {
        item {
            MobileTopBar(
                userName = userName,
                onSwitchProfile = onSwitchProfile,
                onSettings = onSettings,
            )
        }

        if (state.loading) {
            item {
                BasicText(
                    "Loading your library…",
                    style = TextStyle(color = Color(0xFF9CA9B7), fontSize = 16.sp),
                    modifier = Modifier.padding(20.dp),
                )
            }
        } else if (state.error != null) {
            item {
                Column(Modifier.padding(20.dp)) {
                    BasicText(
                        "Couldn't load your library",
                        style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    )
                    Spacer(Modifier.height(7.dp))
                    BasicText(state.error, style = TextStyle(color = Color(0xFF9CA9B7), fontSize = 14.sp))
                    Spacer(Modifier.height(16.dp))
                    VesperButton("Try again", onRetry)
                }
            }
        } else {
            val hero = state.continueWatching.firstOrNull()
            val homeMyV = sortByPopularity(state.myV, popularity, popularityScope)
            val homeMovies = sortByPopularity(state.movies, popularity, popularityScope)
            val homeShows = sortByPopularity(state.shows, popularity, popularityScope)

            if (hero != null) {
                item {
                    VesperHero(
                        item = hero,
                        api = api,
                        expanded = expanded,
                        onPlay = onPlay,
                        onInfo = { onSelect(hero) },
                        onToggleFavorite = { onToggleFavorite(hero) },
                    )
                }
            }

            if (state.continueWatching.isNotEmpty()) item {
                MediaRow("Continue Watching", state.continueWatching, api, onSelect, onToggleFavorite, landscape = true)
            }
            if (homeMyV.isNotEmpty()) item { MediaRow("MyV", homeMyV, api, onSelect, onToggleFavorite) }
            if (homeMovies.isNotEmpty()) item { MediaRow("Movies", homeMovies, api, onSelect, onToggleFavorite) }
            if (homeShows.isNotEmpty()) item { MediaRow("TV Shows", homeShows, api, onSelect, onToggleFavorite) }
            if (state.services.isNotEmpty()) item {
                MediaRow(
                    title = "Services",
                    media = state.services,
                    api = api,
                    onSelect = onSelect,
                    onToggleFavorite = onToggleFavorite,
                    landscape = true,
                    nameFormatter = { serviceDisplayName(it.name) },
                    showFavorite = false,
                    providerTiles = true,
                )
            }
            if (state.collections.isNotEmpty()) item {
                MediaRow(
                    title = "Collections",
                    media = state.collections,
                    api = api,
                    onSelect = onSelect,
                    onToggleFavorite = onToggleFavorite,
                    landscape = true,
                    nameFormatter = { collectionDisplayName(it.name) },
                    showFavorite = false,
                )
            }
        }
    }
}

@Composable
private fun MobileTopBar(
    userName: String,
    onSwitchProfile: () -> Unit,
    onSettings: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.vesper_icon),
            contentDescription = "Vesper",
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(13.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            BasicText(
                "Vesper",
                style = TextStyle(color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold),
            )
            BasicText(
                "Your library",
                style = TextStyle(color = Color(0xFF8492A0), fontSize = 12.sp),
            )
        }

        Box {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF172632))
                    .clickable { menuOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    userName.take(1).uppercase(),
                    style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                )
            }

            if (menuOpen) {
                Popup(
                    alignment = Alignment.TopEnd,
                    onDismissRequest = { menuOpen = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    Column(
                        modifier = Modifier
                            .width(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF111922))
                            .padding(10.dp),
                    ) {
                        BasicText(
                            userName,
                            style = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                        )
                        ProfileMenuRow("⇄", "Switch profile") {
                            menuOpen = false
                            onSwitchProfile()
                        }
                        ProfileMenuRow("⚙", "Settings") {
                            menuOpen = false
                            onSettings()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileMenuRow(
    icon: String,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            icon,
            style = TextStyle(color = Color(0xFFBDEBFF), fontSize = 17.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.width(11.dp))
        BasicText(
            label,
            style = TextStyle(color = Color(0xFFE4EAF0), fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun VesperHero(
    item: BaseItemDto,
    api: ApiClient,
    expanded: Boolean,
    onPlay: (BaseItemDto) -> Unit,
    onInfo: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val image = item.itemBackdropImages.firstOrNull() ?: item.itemImages[ImageType.PRIMARY]
    val heroHeight = if (expanded) 390.dp else 330.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
            .padding(horizontal = if (expanded) 22.dp else 12.dp)
            .clip(RoundedCornerShape(if (expanded) 22.dp else 18.dp))
            .background(Color(0xFF111820)),
    ) {
        AsyncImage(
            modifier = Modifier.fillMaxSize(),
            url = image?.getUrl(api),
            blurHash = image?.blurHash,
            scaleType = ImageView.ScaleType.CENTER_CROP,
        )

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x2205080C),
                            Color(0xF205080C),
                        ),
                        startY = 80f,
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(if (expanded) .58f else .92f)
                .padding(22.dp),
        ) {
            BasicText(
                if ((item.userData?.playbackPositionTicks ?: 0L) > 0L) "CONTINUE WATCHING" else "FEATURED",
                style = TextStyle(
                    color = Color(0xFFBCEBFF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                ),
            )
            Spacer(Modifier.height(7.dp))
            BasicText(
                item.name ?: "Untitled",
                style = TextStyle(
                    color = Color.White,
                    fontSize = if (expanded) 42.sp else 34.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = if (expanded) 45.sp else 37.sp,
                ),
                maxLines = 2,
            )

            val metadata = listOfNotNull(
                item.productionYear?.toString(),
                item.officialRating,
                when (item.type) {
                    BaseItemKind.MOVIE -> "Movie"
                    BaseItemKind.SERIES -> "TV Series"
                    BaseItemKind.EPISODE -> listOfNotNull(
                        item.parentIndexNumber?.let { "S$it" },
                        item.indexNumber?.let { "E$it" },
                    ).joinToString("")
                    else -> null
                },
            ).filter { it.isNotBlank() }.joinToString("  •  ")

            if (metadata.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                BasicText(
                    metadata,
                    style = TextStyle(color = Color(0xFFC1CAD3), fontSize = 13.sp),
                )
            }

            if (!expanded && !item.overview.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                BasicText(
                    item.overview.orEmpty(),
                    style = TextStyle(color = Color(0xFFD4DAE0), fontSize = 14.sp, lineHeight = 19.sp),
                    maxLines = 2,
                )
            }

            Spacer(Modifier.height(15.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VesperButton(
                    if ((item.userData?.playbackPositionTicks ?: 0L) > 0L) "▶ Resume" else "▶ Play",
                    { onPlay(item) },
                )
                DarkButton("More Info", onInfo)
                FavoriteButton(
                    favorite = item.userData?.isFavorite == true,
                    onClick = onToggleFavorite,
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))
}

@Composable
private fun MediaRow(
    title: String,
    media: List<BaseItemDto>,
    api: ApiClient,
    onSelect: (BaseItemDto) -> Unit,
    onToggleFavorite: (BaseItemDto) -> Unit,
    landscape: Boolean = false,
    subtitle: String? = null,
    nameFormatter: (BaseItemDto) -> String = { it.name ?: "Untitled" },
    showFavorite: Boolean = true,
    providerTiles: Boolean = false,
) {
    Column(Modifier.padding(top = 14.dp)) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            BasicText(
                title,
                style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold),
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                BasicText(
                    subtitle,
                    style = TextStyle(color = Color(0xFF6F7E8C), fontSize = 11.sp),
                )
            }
        }
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(media, key = { it.id }) { item ->
                MediaCard(
                    item = item,
                    api = api,
                    landscape = landscape,
                    onSelect = onSelect,
                    onToggleFavorite = onToggleFavorite,
                    nameFormatter = nameFormatter,
                    showFavorite = showFavorite,
                    providerTile = providerTiles,
                )
            }
        }
    }
}

@Composable
private fun MediaCard(
    item: BaseItemDto,
    api: ApiClient,
    landscape: Boolean,
    onSelect: (BaseItemDto) -> Unit,
    onToggleFavorite: (BaseItemDto) -> Unit,
    nameFormatter: (BaseItemDto) -> String = { it.name ?: "Untitled" },
    showFavorite: Boolean = true,
    providerTile: Boolean = false,
) {
    val w = if (landscape) 210.dp else 132.dp
    val h = if (landscape) 122.dp else 198.dp
    val image = if (landscape) {
        item.itemBackdropImages.firstOrNull() ?: item.itemImages[ImageType.PRIMARY]
    } else {
        item.itemImages[ImageType.PRIMARY]
    }

    Column(
        modifier = Modifier.width(w).clickable { onSelect(item) },
    ) {
        Box(
            modifier = Modifier
                .width(w)
                .height(h)
                .clip(RoundedCornerShape(13.dp))
                .background(Color(0xFF111A23)),
        ) {
            if (providerTile) {
                ProviderWordmark(
                    name = serviceDisplayName(item.name),
                    logoResource = serviceLogoResource(item.name),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    url = image?.getUrl(api),
                    blurHash = image?.blurHash,
                    scaleType = ImageView.ScaleType.CENTER_CROP,
                )
            }

            if (showFavorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .size(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x9905080C))
                        .clickable { onToggleFavorite(item) },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        if (item.userData?.isFavorite == true) "♥" else "♡",
                        style = TextStyle(
                            color = if (item.userData?.isFavorite == true) Color(0xFFFF4D7A) else Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }

            if (landscape) {
                val runtime = item.runTimeTicks ?: 0L
                val position = item.userData?.playbackPositionTicks ?: 0L
                if (runtime > 0L && position > 0L) {
                    val progress = (position.toFloat() / runtime.toFloat()).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color(0x55000000))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress)
                                .height(3.dp)
                                .background(Color(0xFF8BD8FF))
                        )
                    }
                }
            }
        }

        if (!providerTile) {
            Spacer(Modifier.height(7.dp))
            BasicText(
                nameFormatter(item),
                style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
        }

        val secondary = when (item.type) {
            BaseItemKind.EPISODE -> listOfNotNull(
                item.seriesName,
                item.parentIndexNumber?.let { "S$it" },
                item.indexNumber?.let { "E$it" },
            ).joinToString(" • ")
            else -> item.productionYear?.toString().orEmpty()
        }

        if (!providerTile && secondary.isNotBlank() && item.type != BaseItemKind.BOX_SET) {
            BasicText(
                secondary,
                style = TextStyle(color = Color(0xFF8F9CAA), fontSize = 12.sp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ProviderWordmark(
    name: String,
    logoResource: Int?,
    modifier: Modifier = Modifier,
) {
    val normalized = name.trim().lowercase()
    val wordmark = when {
        normalized.contains("netflix") -> "NETFLIX"
        normalized.contains("disney") -> "Disney+"
        normalized.contains("amazon") || normalized.contains("prime") -> "prime video"
        normalized.contains("apple") -> "Apple TV+"
        normalized.contains("paramount") -> "Paramount+"
        normalized == "max" || normalized.contains("hbo") -> "max"
        normalized.startsWith("now") -> "NOW"
        else -> name
    }
    val background = when {
        normalized.contains("netflix") -> Color(0xFF050505)
        normalized.contains("disney") -> Color(0xFF0B1D3A)
        normalized.contains("amazon") || normalized.contains("prime") -> Color(0xFF07141D)
        normalized.contains("apple") -> Color(0xFF050505)
        normalized.contains("paramount") -> Color(0xFF0064FF)
        normalized == "max" || normalized.contains("hbo") -> Color(0xFF24105C)
        normalized.startsWith("now") -> Color(0xFF09130E)
        else -> Color(0xFF111A23)
    }
    val foreground = when {
        normalized.contains("netflix") -> Color(0xFFE50914)
        normalized.contains("amazon") || normalized.contains("prime") -> Color(0xFF27B7E8)
        normalized.startsWith("now") -> Color(0xFF00FF85)
        else -> Color.White
    }
    val size = when {
        normalized.contains("netflix") -> 28.sp
        normalized.contains("disney") -> 30.sp
        normalized.contains("amazon") || normalized.contains("prime") -> 27.sp
        normalized.contains("apple") -> 28.sp
        normalized.contains("paramount") -> 27.sp
        normalized == "max" || normalized.contains("hbo") -> 34.sp
        normalized.startsWith("now") -> 31.sp
        else -> 24.sp
    }

    Box(
        modifier = modifier.background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (logoResource != null) {
            Image(
                painter = painterResource(logoResource),
                contentDescription = wordmark,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            BasicText(
                wordmark,
                style = TextStyle(
                    color = foreground,
                    fontSize = size,
                    fontWeight = FontWeight.Black,
                    letterSpacing = if (normalized.contains("netflix")) 1.2.sp else 0.sp,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CollectionBrowser(
    collection: BaseItemDto,
    service: Boolean,
    api: ApiClient,
    onBack: () -> Unit,
    onSelect: (BaseItemDto) -> Unit,
    onToggleFavorite: (BaseItemDto) -> Unit,
    expanded: Boolean,
) {
    var items by remember(collection.id) { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var loading by remember(collection.id) { mutableStateOf(true) }
    var error by remember(collection.id) { mutableStateOf<String?>(null) }
    var selectedType by remember(collection.id) { mutableStateOf<BaseItemKind?>(null) }
    var selectedGenre by remember(collection.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(collection.id) {
        loading = true
        error = null
        runCatching {
            withContext(Dispatchers.IO) {
                api.itemsApi.getItems(
                    parentId = collection.id,
                    recursive = true,
                    includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                    fields = ItemRepository.browseFields,
                    imageTypeLimit = 1,
                    limit = 500,
                    sortBy = setOf(ItemSortBy.SORT_NAME),
                ).content.items
            }
        }.onSuccess { items = it }
            .onFailure { error = it.message ?: "Couldn't load this collection." }
        loading = false
    }

    val genres = remember(items, selectedType) {
        items
            .filter { selectedType == null || it.type == selectedType }
            .flatMap { it.genres.orEmpty() }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    val visible = remember(items, selectedType, selectedGenre) {
        items.filter { item ->
            (selectedType == null || item.type == selectedType) &&
                (selectedGenre == null || item.genres.orEmpty().any {
                    it.equals(selectedGenre, ignoreCase = true)
                })
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05080C)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VesperButton("‹ Back", onBack)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                BasicText(
                    if (service) serviceDisplayName(collection.name) else collectionDisplayName(collection.name),
                    style = TextStyle(color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                BasicText(
                    if (service) "Streaming service" else "${items.size} titles",
                    style = TextStyle(color = Color(0xFF81909E), fontSize = 12.sp),
                )
            }
        }

        if (service && !loading && items.isNotEmpty()) {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    GenreChip(
                        label = "All",
                        selected = selectedType == null,
                        onClick = { selectedType = null },
                    )
                }
                item {
                    GenreChip(
                        label = "Movies",
                        selected = selectedType == BaseItemKind.MOVIE,
                        onClick = {
                            selectedType = if (selectedType == BaseItemKind.MOVIE) null else BaseItemKind.MOVIE
                            selectedGenre = null
                        },
                    )
                }
                item {
                    GenreChip(
                        label = "TV Shows",
                        selected = selectedType == BaseItemKind.SERIES,
                        onClick = {
                            selectedType = if (selectedType == BaseItemKind.SERIES) null else BaseItemKind.SERIES
                            selectedGenre = null
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (service && genres.isNotEmpty()) {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    GenreChip(
                        label = "All genres",
                        selected = selectedGenre == null,
                        onClick = { selectedGenre = null },
                    )
                }
                items(genres, key = { it }) { genre ->
                    GenreChip(
                        label = genre,
                        selected = selectedGenre == genre,
                        onClick = { selectedGenre = if (selectedGenre == genre) null else genre },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        when {
            loading -> BasicText(
                "Loading…",
                style = TextStyle(color = Color(0xFF95A2AF), fontSize = 15.sp),
                modifier = Modifier.padding(20.dp),
            )
            error != null -> BasicText(
                error.orEmpty(),
                style = TextStyle(color = Color(0xFFFFB7BE), fontSize = 14.sp),
                modifier = Modifier.padding(20.dp),
            )
            visible.isEmpty() -> BasicText(
                "Nothing matched this view.",
                style = TextStyle(color = Color(0xFF95A2AF), fontSize = 15.sp),
                modifier = Modifier.padding(20.dp),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(if (expanded) 5 else 3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                gridItems(visible, key = { it.id }) { item ->
                    GridMediaCard(item, api, onSelect, onToggleFavorite)
                }
            }
        }
    }
}

@Composable
private fun LibraryBrowse(
    title: String,
    media: List<BaseItemDto>,
    api: ApiClient,
    onSelect: (BaseItemDto) -> Unit,
    onToggleFavorite: (BaseItemDto) -> Unit,
    popularity: PopularityState,
    popularityScope: PopularityScope,
    tmdbConfigured: Boolean,
    expanded: Boolean,
) {
    val genres = remember(media) {
        media
            .flatMap { it.genres.orEmpty() }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    var selectedGenre by remember(title, media) { mutableStateOf<String?>(null) }
    var sort by remember(title) { mutableStateOf(LibrarySort.POPULARITY) }

    val filtered = remember(media, selectedGenre) {
        selectedGenre?.let { genre ->
            media.filter { item ->
                item.genres.orEmpty().any { it.equals(genre, ignoreCase = true) }
            }
        } ?: media
    }

    val visibleMedia = remember(filtered, sort, popularity, popularityScope) {
        sortLibrary(filtered, sort, popularity, popularityScope)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 18.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            BasicText(
                title,
                style = TextStyle(color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold),
            )
            BasicText(
                if (selectedGenre == null) "${media.size} items"
                else "${visibleMedia.size} • $selectedGenre",
                style = TextStyle(color = Color(0xFF81909E), fontSize = 13.sp),
            )
        }

        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(LibrarySort.entries, key = { it.name }) { option ->
                SortChip(
                    label = option.label,
                    selected = sort == option,
                    onClick = { sort = option },
                )
            }
        }

        if (sort == LibrarySort.POPULARITY) {
            val sourceNote = when {
                popularityScope == PopularityScope.GLOBAL && popularity.globalAvailable ->
                    "Global popularity • TMDb this week"
                popularityScope == PopularityScope.GLOBAL && !tmdbConfigured ->
                    "Global popularity • add your TMDb key in Settings"
                popularityScope == PopularityScope.GLOBAL ->
                    "Global popularity • TMDb unavailable"
                popularityScope == PopularityScope.LOCAL && popularity.householdLocalAvailable ->
                    "Local popularity • Vesper users, last 30 days"
                else ->
                    "Local popularity • active Jellyfin profile"
            }
            BasicText(
                sourceNote,
                style = TextStyle(color = Color(0xFF6F7E8C), fontSize = 11.sp),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp),
            )
        } else {
            Spacer(Modifier.height(6.dp))
        }

        if (genres.isNotEmpty()) {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    GenreChip(
                        label = "All",
                        selected = selectedGenre == null,
                        onClick = { selectedGenre = null },
                    )
                }
                items(genres, key = { it }) { genre ->
                    GenreChip(
                        label = genre,
                        selected = selectedGenre == genre,
                        onClick = {
                            selectedGenre = if (selectedGenre == genre) null else genre
                        },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        } else {
            Spacer(Modifier.height(6.dp))
        }

        if (visibleMedia.isEmpty()) {
            BasicText(
                "Nothing here yet.",
                style = TextStyle(color = Color(0xFF95A2AF), fontSize = 15.sp),
                modifier = Modifier.padding(20.dp),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (expanded) 5 else 3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 98.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                gridItems(visibleMedia, key = { it.id }) { item ->
                    GridMediaCard(item, api, onSelect, onToggleFavorite)
                }
            }
        }
    }
}

@Composable
private fun SortChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Color(0xFFBDEBFF) else Color(0xFF101821))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 8.dp),
    ) {
        BasicText(
            label,
            style = TextStyle(
                color = if (selected) Color(0xFF071017) else Color(0xFFD2DAE2),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun GenreChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Color(0xFFEAF6FC) else Color(0xFF101821))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 9.dp),
    ) {
        BasicText(
            label,
            style = TextStyle(
                color = if (selected) Color(0xFF071017) else Color(0xFFD2DAE2),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

@Composable
private fun SearchBrowse(
    state: MobileHomeState,
    api: ApiClient,
    seerrConfigured: Boolean,
    onSeerrSearch: suspend (String) -> List<SeerrSearchResult>,
    onSeerrRequest: suspend (SeerrSearchResult) -> String?,
    onSelect: (BaseItemDto) -> Unit,
    onToggleFavorite: (BaseItemDto) -> Unit,
    expanded: Boolean,
) {
    var query by remember { mutableStateOf("") }
    var seerrResults by remember { mutableStateOf<List<SeerrSearchResult>>(emptyList()) }
    var seerrLoading by remember { mutableStateOf(false) }
    var seerrError by remember { mutableStateOf<String?>(null) }
    var seerrRefresh by remember { mutableStateOf(0) }

    val all = remember(state) {
        (state.continueWatching + state.myV + state.movies + state.shows)
            .distinctBy { it.id }
    }
    val localResults = if (query.isBlank()) all else all.filter { item ->
        val haystack = listOfNotNull(
            item.name,
            item.seriesName,
            item.productionYear?.toString(),
        ).joinToString(" ").lowercase()
        haystack.contains(query.trim().lowercase())
    }
    val localTmdbKeys = remember(all) {
        all.mapNotNull { item ->
            val mediaType = when (item.type) {
                BaseItemKind.MOVIE -> "movie"
                BaseItemKind.SERIES -> "tv"
                else -> null
            }
            val tmdbId = item.tmdbId()
            if (mediaType != null && tmdbId != null) "${mediaType}:${tmdbId}" else null
        }.toSet()
    }
    val remoteResults = seerrResults.filterNot {
        "${it.mediaType}:${it.tmdbId}" in localTmdbKeys
    }

    LaunchedEffect(query, seerrConfigured, seerrRefresh) {
        val cleanQuery = query.trim()
        if (!seerrConfigured || cleanQuery.length < 2) {
            seerrResults = emptyList()
            seerrLoading = false
            seerrError = null
            return@LaunchedEffect
        }

        delay(450)
        seerrLoading = true
        seerrError = null
        runCatching { onSeerrSearch(cleanQuery) }
            .onSuccess { seerrResults = it }
            .onFailure {
                seerrResults = emptyList()
                seerrError = it.message ?: "Couldn't reach Seerr."
            }
        seerrLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 18.dp),
    ) {
        BasicText(
            "Search",
            style = TextStyle(color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Color(0xFF101821))
                .padding(horizontal = 15.dp, vertical = 13.dp),
        ) {
            if (query.isBlank()) {
                BasicText(
                    if (seerrConfigured) "Search your library and Seerr" else "Movies and TV shows",
                    style = TextStyle(color = Color(0xFF657482), fontSize = 16.sp),
                )
            }
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(Color(0xFF8BD8FF)),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(if (expanded) 5 else 3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 10.dp,
                bottom = 98.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (query.isBlank()) {
                gridItems(localResults, key = { it.id }) { item ->
                    GridMediaCard(item, api, onSelect, onToggleFavorite)
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SearchSectionHeader(
                        title = "In your library",
                        subtitle = if (localResults.isEmpty()) "No local matches" else "${localResults.size} found",
                    )
                }

                gridItems(localResults, key = { "local-${it.id}" }) { item ->
                    GridMediaCard(item, api, onSelect, onToggleFavorite)
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    SearchSectionHeader(
                        title = "Request with Seerr",
                        subtitle = when {
                            !seerrConfigured -> "Connect Seerr in Settings"
                            seerrLoading -> "Searching…"
                            seerrError != null -> seerrError.orEmpty()
                            remoteResults.isEmpty() && query.trim().length >= 2 -> "No additional matches"
                            else -> "${remoteResults.size} found"
                        },
                    )
                }

                if (seerrConfigured && !seerrLoading && seerrError == null) {
                    gridItems(
                        remoteResults,
                        key = { "seerr-${it.mediaType}-${it.tmdbId}" },
                    ) { item ->
                        SeerrMediaCard(
                            item = item,
                            onRequest = onSeerrRequest,
                            onRequested = { seerrRefresh++ },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
    ) {
        BasicText(
            title,
            style = TextStyle(
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        BasicText(
            subtitle,
            style = TextStyle(
                color = Color(0xFF7F8D9A),
                fontSize = 11.sp,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun SeerrMediaCard(
    item: SeerrSearchResult,
    onRequest: suspend (SeerrSearchResult) -> String?,
    onRequested: () -> Unit,
) {
    var mediaStatus by remember(item.tmdbId, item.mediaType, item.mediaStatus) {
        mutableStateOf(item.mediaStatus)
    }
    var requesting by remember(item.tmdbId, item.mediaType) { mutableStateOf(false) }
    var message by remember(item.tmdbId, item.mediaType) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val statusLabel = when (mediaStatus) {
        SEERR_STATUS_PENDING -> "Requested"
        SEERR_STATUS_PROCESSING -> "Processing"
        SEERR_STATUS_PARTIALLY_AVAILABLE -> "Partially available"
        SEERR_STATUS_AVAILABLE -> "Available"
        SEERR_STATUS_BLOCKLISTED -> "Blocked"
        else -> "Request"
    }
    val requestEnabled =
        !requesting && (mediaStatus == SEERR_STATUS_UNKNOWN || mediaStatus == SEERR_STATUS_DELETED)

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF111A23)),
        ) {
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                url = item.posterUrl,
                scaleType = ImageView.ScaleType.CENTER_CROP,
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCC05080C))
                    .padding(horizontal = 7.dp, vertical = 4.dp),
            ) {
                BasicText(
                    "SEERR",
                    style = TextStyle(
                        color = Color(0xFFBDEBFF),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                    ),
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        BasicText(
            item.title,
            style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
        )
        BasicText(
            listOfNotNull(
                item.year?.toString(),
                if (item.mediaType == "movie") "Movie" else "TV",
            ).joinToString(" • "),
            style = TextStyle(color = Color(0xFF7F8D9A), fontSize = 11.sp),
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(
                    when {
                        mediaStatus == SEERR_STATUS_AVAILABLE -> Color(0xFF163224)
                        requestEnabled -> Color(0xFFEAF6FC)
                        else -> Color(0xFF17232D)
                    }
                )
                .clickable(enabled = requestEnabled) {
                    requesting = true
                    message = null
                    scope.launch {
                        val error = onRequest(item)
                        requesting = false
                        if (error == null || error == "Already requested.") {
                            mediaStatus = SEERR_STATUS_PENDING
                            message = if (error == null) "Requested" else error
                            onRequested()
                        } else {
                            message = error
                        }
                    }
                }
                .padding(horizontal = 8.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                if (requesting) "Requesting…" else statusLabel,
                style = TextStyle(
                    color = if (requestEnabled) Color(0xFF071017) else Color(0xFFD4DDE5),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
        }

        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            BasicText(
                message.orEmpty(),
                style = TextStyle(
                    color = if (message == "Requested" || message == "Already requested.") {
                        Color(0xFF8EDCB2)
                    } else {
                        Color(0xFFFFA6A6)
                    },
                    fontSize = 9.sp,
                ),
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun GridMediaCard(
    item: BaseItemDto,
    api: ApiClient,
    onSelect: (BaseItemDto) -> Unit,
    onToggleFavorite: (BaseItemDto) -> Unit,
) {
    val image = item.itemImages[ImageType.PRIMARY]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(item) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF111A23)),
        ) {
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                url = image?.getUrl(api),
                blurHash = image?.blurHash,
                scaleType = ImageView.ScaleType.CENTER_CROP,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color(0x9905080C))
                    .clickable { onToggleFavorite(item) },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    if (item.userData?.isFavorite == true) "♥" else "♡",
                    style = TextStyle(
                        color = if (item.userData?.isFavorite == true) Color(0xFFFF4D7A) else Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        BasicText(
            item.name ?: "Untitled",
            style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
        )
        BasicText(
            item.productionYear?.toString().orEmpty(),
            style = TextStyle(color = Color(0xFF7F8D9A), fontSize = 11.sp),
            maxLines = 1,
        )
    }
}

@Composable
private fun MobileBottomNav(
    active: MobileTab,
    onSelect: (MobileTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xF405080C))
            .padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MobileTab.entries.forEach { tab ->
            val selected = tab == active
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(13.dp))
                    .clickable { onSelect(tab) }
                    .background(if (selected) Color(0xFF101C25) else Color.Transparent)
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(
                    tab.icon,
                    style = TextStyle(
                        color = if (selected) Color(0xFFBDEBFF) else Color(0xFF7C8996),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(Modifier.height(2.dp))
                BasicText(
                    tab.label,
                    style = TextStyle(
                        color = if (selected) Color.White else Color(0xFF7C8996),
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                )
            }
        }
    }
}

@Composable
private fun FavoriteButton(
    favorite: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xAA111820))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            if (favorite) "♥ MyV" else "♡ MyV",
            style = TextStyle(
                color = if (favorite) Color(0xFFFF4D7A) else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun DarkButton(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xAA111820))
            .clickable(onClick = onClick)
            .padding(horizontal = 17.dp, vertical = 11.dp),
    ) {
        BasicText(
            label,
            style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun MobileDetails(
    item: BaseItemDto,
    api: ApiClient,
    onBack: () -> Unit,
    onPlay: (BaseItemDto) -> Unit,
    onToggleFavorite: (BaseItemDto) -> Unit,
) {
    val primary = item.itemImages[ImageType.PRIMARY]
    val backdrop = item.itemBackdropImages.firstOrNull() ?: primary

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 600.dp

        if (expanded) {
            Row(Modifier.fillMaxSize().background(Color(0xFF05080C))) {
                Box(
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxSize()
                        .background(Color(0xFF101820)),
                ) {
                    AsyncImage(
                        modifier = Modifier.fillMaxSize(),
                        url = backdrop?.getUrl(api),
                        blurHash = backdrop?.blurHash,
                        scaleType = ImageView.ScaleType.CENTER_CROP,
                    )
                    VesperButton(
                        "‹ Back",
                        onBack,
                        Modifier.align(Alignment.TopStart).padding(24.dp),
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(.85f)
                        .fillMaxSize()
                        .background(Color(0xFF05080C)),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 30.dp,
                        end = 30.dp,
                        top = 54.dp,
                        bottom = 40.dp,
                    ),
                ) {
                    item {
                        DetailCopy(item, api, onPlay, onToggleFavorite, expanded = true)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(Color(0xFF05080C)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 34.dp),
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(235.dp)
                            .background(Color(0xFF101820)),
                    ) {
                        AsyncImage(
                            modifier = Modifier.fillMaxSize(),
                            url = backdrop?.getUrl(api),
                            blurHash = backdrop?.blurHash,
                            scaleType = ImageView.ScaleType.CENTER_CROP,
                        )
                        VesperButton(
                            "‹ Back",
                            onBack,
                            Modifier.align(Alignment.TopStart).padding(16.dp),
                        )
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                        DetailCopy(item, api, onPlay, onToggleFavorite, expanded = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailCopy(
    item: BaseItemDto,
    api: ApiClient,
    onPlay: (BaseItemDto) -> Unit,
    onToggleFavorite: (BaseItemDto) -> Unit,
    expanded: Boolean,
) {
    BasicText(
        item.name ?: "Untitled",
        style = TextStyle(
            color = Color.White,
            fontSize = if (expanded) 38.sp else 30.sp,
            fontWeight = FontWeight.Bold,
        ),
    )

    Spacer(Modifier.height(9.dp))

    BasicText(
        listOfNotNull(
            item.productionYear?.toString(),
            item.officialRating,
            when (item.type) {
                BaseItemKind.MOVIE -> "Movie"
                BaseItemKind.SERIES -> "TV Series"
                BaseItemKind.EPISODE -> "Episode"
                else -> null
            },
        ).joinToString("  •  "),
        style = TextStyle(color = Color(0xFFA9B5C1), fontSize = 14.sp),
    )

    if (item.type != BaseItemKind.SERIES) {
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VesperButton(
                if ((item.userData?.playbackPositionTicks ?: 0L) > 0L) "▶ Resume" else "▶ Play",
                { onPlay(item) },
            )
            FavoriteButton(
                favorite = item.userData?.isFavorite == true,
                onClick = { onToggleFavorite(item) },
            )
        }
    }

    if (!item.overview.isNullOrBlank()) {
        Spacer(Modifier.height(22.dp))
        BasicText(
            item.overview.orEmpty(),
            style = TextStyle(
                color = Color(0xFFD0D7DE),
                fontSize = if (expanded) 17.sp else 16.sp,
                lineHeight = if (expanded) 25.sp else 23.sp,
            ),
        )
    }

    if (item.type == BaseItemKind.SERIES) {
        Spacer(Modifier.height(28.dp))
        SeriesEpisodePicker(
            series = item,
            api = api,
            onPlay = onPlay,
            expanded = expanded,
        )
    }
}

@Composable
private fun SeriesEpisodePicker(
    series: BaseItemDto,
    api: ApiClient,
    onPlay: (BaseItemDto) -> Unit,
    expanded: Boolean,
) {
    var episodes by remember(series.id) { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var selectedSeason by remember(series.id) { mutableStateOf<Int?>(null) }
    var loading by remember(series.id) { mutableStateOf(true) }
    var error by remember(series.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(series.id) {
        loading = true
        error = null
        runCatching {
            withContext(Dispatchers.IO) {
                api.tvShowsApi.getEpisodes(
                    seriesId = series.id,
                    isMissing = false,
                    fields = ItemRepository.browseFields,
                    limit = 500,
                ).content.items
                    .sortedWith(compareBy<BaseItemDto> { it.parentIndexNumber ?: Int.MAX_VALUE }
                        .thenBy { it.indexNumber ?: Int.MAX_VALUE })
            }
        }.onSuccess { loaded ->
            episodes = loaded
            val seasonWithUnwatched = loaded
                .firstOrNull { it.userData?.played != true }
                ?.parentIndexNumber
            selectedSeason = seasonWithUnwatched
                ?: loaded.firstOrNull()?.parentIndexNumber
                ?: 1
        }.onFailure {
            error = it.message ?: "Couldn't load episodes."
        }
        loading = false
    }

    BasicText(
        "Episodes",
        style = TextStyle(
            color = Color.White,
            fontSize = if (expanded) 22.sp else 20.sp,
            fontWeight = FontWeight.Bold,
        ),
    )

    Spacer(Modifier.height(12.dp))

    when {
        loading -> {
            BasicText(
                "Loading episodes…",
                style = TextStyle(color = Color(0xFF8F9CAA), fontSize = 14.sp),
            )
        }

        error != null -> {
            BasicText(
                error.orEmpty(),
                style = TextStyle(color = Color(0xFFFFB7BE), fontSize = 14.sp),
            )
        }

        episodes.isEmpty() -> {
            BasicText(
                "No episodes found.",
                style = TextStyle(color = Color(0xFF8F9CAA), fontSize = 14.sp),
            )
        }

        else -> {
            val seasons = episodes
                .mapNotNull { it.parentIndexNumber }
                .distinct()
                .sorted()

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp),
            ) {
                items(seasons, key = { it }) { season ->
                    SeasonChip(
                        season = season,
                        selected = selectedSeason == season,
                        onClick = { selectedSeason = season },
                    )
                }
            }

            val visibleEpisodes = episodes.filter { episode ->
                episode.parentIndexNumber == selectedSeason
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                visibleEpisodes.forEach { episode ->
                    EpisodeRow(
                        episode = episode,
                        api = api,
                        expanded = expanded,
                        onClick = { onPlay(episode) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SeasonChip(
    season: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Color(0xFFEAF6FC) else Color(0xFF121B24))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 9.dp),
    ) {
        BasicText(
            "Season $season",
            style = TextStyle(
                color = if (selected) Color(0xFF071017) else Color(0xFFD6DEE6),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

@Composable
private fun EpisodeRow(
    episode: BaseItemDto,
    api: ApiClient,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val image = episode.itemImages[ImageType.PRIMARY]
    val thumbWidth = if (expanded) 144.dp else 118.dp
    val thumbHeight = if (expanded) 82.dp else 68.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0E161E))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(thumbWidth)
                .height(thumbHeight)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0xFF17212A)),
        ) {
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                url = image?.getUrl(api),
                blurHash = image?.blurHash,
                scaleType = ImageView.ScaleType.CENTER_CROP,
            )

            if (episode.userData?.played == true) {
                BasicText(
                    "✓",
                    style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            val code = listOfNotNull(
                episode.parentIndexNumber?.let { "S$it" },
                episode.indexNumber?.let { "E$it" },
            ).joinToString("")

            if (code.isNotBlank()) {
                BasicText(
                    code,
                    style = TextStyle(
                        color = Color(0xFF8BD8FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(Modifier.height(3.dp))
            }

            BasicText(
                episode.name ?: "Episode",
                style = TextStyle(
                    color = Color.White,
                    fontSize = if (expanded) 15.sp else 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 2,
            )

            if ((episode.userData?.playbackPositionTicks ?: 0L) > 0L && episode.userData?.played != true) {
                Spacer(Modifier.height(4.dp))
                BasicText(
                    "Resume",
                    style = TextStyle(color = Color(0xFFAAB5C0), fontSize = 12.sp),
                )
            }
        }

        BasicText(
            "▶",
            style = TextStyle(color = Color.White, fontSize = 18.sp),
        )
    }
}

@Composable
private fun VesperButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFEAF6FC))
            .clickable(onClick = onClick)
            .padding(horizontal = 17.dp, vertical = 11.dp),
    ) {
        BasicText(label, style = TextStyle(color = Color(0xFF071017), fontSize = 14.sp, fontWeight = FontWeight.Bold))
    }
}
