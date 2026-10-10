package org.jellyfin.androidtv.ui.mobile

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import org.jellyfin.androidtv.data.repository.ItemMutationRepository
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Popup
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
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
import org.jellyfin.androidtv.util.apiclient.primaryImage
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
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

    private val profileSwitchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            recreate()
        }
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            recreate()
        }
    }

    private var state by mutableStateOf(MobileHomeState())
    private var popularity by mutableStateOf(PopularityState())
    private var musicState by mutableStateOf(MusicUiState())
    private var booksState by mutableStateOf(BooksUiState())
    private var selected by mutableStateOf<BaseItemDto?>(null)
    private var tmdbApiKey by mutableStateOf("")
    private var seerrApiKey by mutableStateOf("")
    private var musicAssistantBaseUrl by mutableStateOf("")
    private var musicAssistantToken by mutableStateOf("")
    private var musicRequestsUrl by mutableStateOf("")
    private var musicRequestsApiKey by mutableStateOf("")
    private var bookRequestsUrl by mutableStateOf("")
    private var bookRequestsApiKey by mutableStateOf("")
    private var audiobookLibraryUrl by mutableStateOf("")
    private var audiobookLibraryToken by mutableStateOf("")
    private var popularityScope by mutableStateOf(PopularityScope.GLOBAL)
    private var showPersistentMiniPlayer by mutableStateOf(true)
    private var musicFavourites by mutableStateOf<List<MaMediaItem>>(emptyList())
    private val hydratedTabs = mutableSetOf<MobileTab>()
    private val loadingTabs = mutableSetOf<MobileTab>()
    private var musicPlayerRefreshInFlight = false
    private var musicPlayerRefreshQueued = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (sessionRepository.currentSession.value == null || userRepository.currentUser.value == null) {
            startActivity(Intent(this, MobileStartupActivity::class.java))
            finish()
            return
        }

        activeMusicFavouritesProfile()?.let { profile ->
            musicFavourites = VesperMusicFavoritesStore(this).load(profile)
        }
        val vesperPreferences = getSharedPreferences("vesper", MODE_PRIVATE)
        tmdbApiKey = vesperPreferences
            .getString("tmdb_api_key", "")
            .orEmpty()
        seerrApiKey = vesperPreferences
            .getString("seerr_api_key", "")
            .orEmpty()
        musicAssistantBaseUrl = VesperServiceConfig.musicAssistantUrl(vesperPreferences)
        musicAssistantToken = vesperPreferences
            .getString("music_assistant_token", "")
            .orEmpty()
        musicRequestsUrl = VesperServiceConfig.aurralUrl(vesperPreferences)
        musicRequestsApiKey = vesperPreferences.getString("music_requests_api_key", "").orEmpty()
        bookRequestsUrl = VesperServiceConfig.lazyLibrarianUrl(vesperPreferences)
        bookRequestsApiKey = vesperPreferences.getString("book_requests_api_key", "").orEmpty()
        audiobookLibraryUrl = VesperServiceConfig.audiobookshelfUrl(vesperPreferences)
        audiobookLibraryToken = vesperPreferences.getString("audiobook_library_token", "").orEmpty()
        showPersistentMiniPlayer = vesperPreferences
            .getBoolean("show_persistent_mini_player", true)
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
                musicState = musicState,
                musicFavourites = musicFavourites,
                onToggleMusicFavourite = ::toggleMusicFavourite,
                onLoadMyVTrackGenres = ::loadMyVTrackGenres,
                musicConfigured = musicAssistantBaseUrl.isNotBlank() && musicAssistantToken.isNotBlank(),
                booksState = booksState,
                booksConfigured = audiobookLibraryUrl.isNotBlank() && audiobookLibraryToken.isNotBlank(),
                showPersistentMiniPlayer = showPersistentMiniPlayer,
                tmdbConfigured = tmdbApiKey.isNotBlank(),
                selected = selected,
                userName = userRepository.currentUser.value?.name ?: "Vesper",
                userAvatarUrl = userRepository.currentUser.value?.let { user ->
                    val revision = vesperPreferences.getLong("profile_avatar_revision_${user.id}", 0L)
                    if (revision > 0L) {
                        api.imageApi.getUserImageUrl(userId = user.id, tag = revision.toString())
                    } else {
                        user.primaryImage?.getUrl(api)
                    }
                },
                api = api,
                seerrConfigured = seerrApiKey.isNotBlank(),
                musicRequestsConfigured = musicRequestsUrl.isNotBlank() && musicRequestsApiKey.isNotBlank(),
                bookRequestsConfigured = bookRequestsUrl.isNotBlank() && bookRequestsApiKey.isNotBlank(),
                audiobookLibraryConfigured = audiobookLibraryUrl.isNotBlank() && audiobookLibraryToken.isNotBlank(),
                onLibrarySearch = ::searchLibrary,
                onSeerrSearch = ::searchSeerr,
                onSeerrRequest = ::requestSeerr,
                onMusicLibrarySearch = ::searchMusicLibrary,
                onMusicRequestSearch = ::searchMusicRequests,
                onMusicRequest = ::requestMusicAlbum,
                onBookLibrarySearch = ::searchBookLibrary,
                onBookRequestSearch = ::searchBookRequests,
                onBookRequest = ::requestBook,
                onRetryBooks = ::loadBooks,
                onLoadBookDetails = ::loadBookDetails,
                onSelect = { selected = it },
                onBack = { selected = null },
                onRetry = ::loadHome,
                onPlay = ::playItem,
                onToggleFavorite = ::toggleFavorite,
                onSwitchProfile = ::switchProfile,
                onTabSelected = { tab ->
                    loadLibraryTab(tab)
                    if (tab == MobileTab.BOOKS) loadBooks()
                },
                onRetryMusic = ::loadMusic,
                onLoadArtistAlbums = ::loadMusicArtistAlbums,
                onLoadAlbumDetails = ::loadMusicAlbumDetails,
                onLoadPlaylistDetails = ::loadMusicPlaylistDetails,
                onCreateMusicPlaylist = ::createMusicPlaylist,
                onRenameMusicPlaylist = ::renameMusicPlaylist,
                onAddMusicPlaylistTrack = ::addMusicPlaylistTrack,
                onRemoveMusicPlaylistTrack = ::removeMusicPlaylistTrack,
                onPlayMusic = ::playMusic,
                onControlMusic = ::controlMusic,
                onSeekMusic = ::seekMusic,
                onLoadMusicQueue = ::loadMusicQueue,
                onPlayMusicQueueItem = ::playMusicQueueItem,
                onEditMusicQueue = ::editMusicQueue,
                onClearMusicQueue = ::clearMusicQueue,
                onEnqueueMusic = ::enqueueMusic,
                onUpdateMusicGroup = ::updateMusicGroup,
                onSettings = ::openSettings,
            )
        }

        lifecycleScope.launch {
            VesperMusicPlaybackService.playerRefreshEvents.collect {
                refreshMusicPlayers()
            }
        }

        loadHome()
        if (musicAssistantBaseUrl.isNotBlank() && musicAssistantToken.isNotBlank()) {
            startForegroundService(
                Intent(this, VesperMusicPlaybackService::class.java)
                    .setAction(VesperMusicPlaybackService.ACTION_CONFIGURE)
            )
            loadMusic()
        } else {
            stopService(Intent(this, VesperMusicPlaybackService::class.java))
        }
    }

    private fun loadHome() {
        hydratedTabs.clear()
        loadingTabs.clear()
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
                            limit = 36,
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
                            limit = 36,
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
                            limit = 36,
                            sortBy = setOf(ItemSortBy.PLAY_COUNT),
                            sortOrder = setOf(SortOrder.DESCENDING),
                        ).content.items
                    }
                    val serviceBoxSets = async {
                        api.itemsApi.getItems(
                            fields = emptySet(),
                            includeItemTypes = setOf(BaseItemKind.BOX_SET),
                            recursive = true,
                            imageTypeLimit = 0,
                            limit = 1000,
                            sortBy = setOf(ItemSortBy.SORT_NAME),
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

                    val allServiceCollections = serviceBoxSets.await()
                    val allCollections = boxSets.await()
                    MobileHomeState(
                        continueWatching = resume.await().filterNot(::isServiceArtifact),
                        myV = favorites.await().filterNot(::isServiceArtifact),
                        movies = movies.await().filterNot(::isServiceArtifact),
                        shows = shows.await().filterNot(::isServiceArtifact),
                        services = serviceOrder.mapNotNull { key ->
                            allServiceCollections.firstOrNull { serviceKey(it.name) == key }
                        },
                        collections = allCollections.filterNot(::isServiceCollection),
                    )
                }
            }.getOrElse { error ->
                MobileHomeState(error = friendlyServiceError("Jellyfin", error))
            }

            if (state.error == null) {
                loadServiceLogos()
                loadPopularity()
            }
        }
    }

    private fun loadLibraryTab(tab: MobileTab) {
        if (tab == MobileTab.MUSIC) {
            if (!musicState.loading && !musicState.loaded && musicAssistantToken.isNotBlank()) {
                loadMusic()
            }
            return
        }
        if (tab !in setOf(MobileTab.MOVIES, MobileTab.TV, MobileTab.MYV)) return
        if (tab in hydratedTabs || tab in loadingTabs) return

        loadingTabs += tab
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    when (tab) {
                        MobileTab.MOVIES -> api.itemsApi.getItems(
                            fields = ItemRepository.browseFields,
                            includeItemTypes = setOf(BaseItemKind.MOVIE),
                            recursive = true,
                            imageTypeLimit = 1,
                            limit = 500,
                            sortBy = setOf(ItemSortBy.PLAY_COUNT),
                            sortOrder = setOf(SortOrder.DESCENDING),
                        ).content.items.filterNot(::isServiceArtifact)

                        MobileTab.TV -> api.itemsApi.getItems(
                            fields = ItemRepository.browseFields,
                            includeItemTypes = setOf(BaseItemKind.SERIES),
                            recursive = true,
                            imageTypeLimit = 1,
                            limit = 500,
                            sortBy = setOf(ItemSortBy.PLAY_COUNT),
                            sortOrder = setOf(SortOrder.DESCENDING),
                        ).content.items.filterNot(::isServiceArtifact)

                        MobileTab.MYV -> api.itemsApi.getItems(
                            fields = ItemRepository.browseFields,
                            includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                            recursive = true,
                            filters = setOf(ItemFilter.IS_FAVORITE),
                            imageTypeLimit = 1,
                            limit = 500,
                            sortBy = setOf(ItemSortBy.PLAY_COUNT),
                            sortOrder = setOf(SortOrder.DESCENDING),
                        ).content.items.filterNot(::isServiceArtifact)

                        else -> emptyList()
                    }
                }
            }.onSuccess { loaded ->
                state = when (tab) {
                    MobileTab.MOVIES -> state.copy(movies = loaded)
                    MobileTab.TV -> state.copy(shows = loaded)
                    MobileTab.MYV -> state.copy(myV = loaded)
                    else -> state
                }
                hydratedTabs += tab
            }
            loadingTabs -= tab
        }
    }

    private suspend fun searchLibrary(query: String): List<BaseItemDto> =
        withContext(Dispatchers.IO) {
            val cleanQuery = query.trim()
            if (cleanQuery.length < 2) return@withContext emptyList()

            try {
                api.itemsApi.getItems(
                    fields = ItemRepository.browseFields,
                    includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                    recursive = true,
                    imageTypeLimit = 1,
                    limit = 100,
                    searchTerm = cleanQuery,
                ).content.items.filterNot(::isServiceArtifact)
            } catch (error: Throwable) {
                throw IllegalStateException(friendlyServiceError("Jellyfin", error), error)
            }
        }

    private fun loadServiceLogos() {
        val key = tmdbApiKey.trim()
        if (key.isBlank()) return

        lifecycleScope.launch {
            val logos = withContext(Dispatchers.IO) {
                fetchTmdbProviderLogos(key)
            }
            if (logos.isNotEmpty()) {
                state = state.copy(serviceLogos = logos)
            }
        }
    }

    private fun fetchTmdbProviderLogos(apiKey: String): Map<String, String> = runCatching {
        val encoded = URLEncoder.encode(apiKey, StandardCharsets.UTF_8.name())
        buildMap {
            listOf("movie", "tv").forEach { mediaType ->
                val url = URL(
                    "https://api.themoviedb.org/3/watch/providers/$mediaType?api_key=$encoded&watch_region=GB"
                )
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 10000
                    setRequestProperty("Accept", "application/json")
                }
                if (connection.responseCode !in 200..299) {
                    connection.disconnect()
                    return@forEach
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val results = org.json.JSONObject(body).optJSONArray("results") ?: JSONArray()
                for (index in 0 until results.length()) {
                    val provider = results.optJSONObject(index) ?: continue
                    val name = provider.optString("provider_name").trim().lowercase()
                    val logoPath = provider.optString("logo_path").trim()
                    if (name.isNotBlank() && logoPath.isNotBlank()) {
                        put(name, "https://image.tmdb.org/t/p/w300$logoPath")
                    }
                }
            }
        }
    }.getOrDefault(emptyMap())

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

    private fun activeMusicFavouritesProfile(): String? {
        val server = sessionRepository.currentSession.value?.serverId?.toString() ?: return null
        val user = userRepository.currentUser.value?.id?.toString() ?: return null
        return "$server:$user"
    }

    private fun toggleMusicFavourite(item: MaMediaItem) {
        val profile = activeMusicFavouritesProfile() ?: return
        runCatching { VesperMusicFavoritesStore(this).toggle(profile, item) }
            .onSuccess { updated ->
                musicFavourites = updated
                val added = updated.any { it.uri == item.uri }
                Toast.makeText(this,
                    if (added) "Added to MyV" else "Removed from MyV",
                    Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(this, error.message ?: "Couldn't save MyV favourite.",
                    Toast.LENGTH_LONG).show()
            }
    }

    private fun switchProfile() {
        val serverId = sessionRepository.currentSession.value?.serverId ?: return
        profileSwitchLauncher.launch(
            Intent(this, MobileStartupActivity::class.java)
                .putExtra(MobileStartupActivity.EXTRA_SWITCH_SERVER_ID, serverId.toString())
                .putExtra(MobileStartupActivity.EXTRA_PROFILE_SWITCH, true)
        )
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun musicConnectionError(error: Throwable): String {
        val endpoint = runCatching {
            VesperMusicEndpoint.from(musicAssistantBaseUrl)
        }.getOrNull()

        if (endpoint != null && !endpoint.remoteReady) {
            return "Music Away isn't set up yet. Vesper is still using your home-only music address. " +
                "Open Settings and switch Music to your secure HTTPS address."
        }
        return friendlyServiceError("Music", error)
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
        401, 403 -> "Media requests authentication failed. Check Settings."
        408 -> "Media requests timed out. Try again."
        in 500..599 -> "Media requests are unavailable right now. Try again."
        else -> "Media request failed. Try again."
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
                if (error is IllegalStateException && error.message?.startsWith("Media ") == true) {
                    throw error
                }
                throw IllegalStateException(friendlyServiceError("Media requests", error), error)
            } finally {
                connection.disconnect()
            }
        }

    private suspend fun requestSeerr(item: SeerrSearchResult): String? =
        withContext(Dispatchers.IO) {
            val baseUrl = VesperServiceConfig.SEERR_BASE_URL
            val apiKey = seerrApiKey.trim()
            if (apiKey.isBlank()) {
                return@withContext "Set up Media Requests in Vesper settings first."
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
                friendlyServiceError("Media requests", error)
            } finally {
                connection.disconnect()
            }
        }

    private suspend fun loadMyVTrackGenres(item: MaMediaItem): List<String> =
        withContext(Dispatchers.IO) {
            if (musicAssistantBaseUrl.isBlank() || musicAssistantToken.isBlank()) emptyList()
            else MusicAssistantClient(musicAssistantBaseUrl, musicAssistantToken).loadTrackGenres(item)
        }

    private suspend fun searchMusicLibrary(query: String): MaSearchResults =
        withContext(Dispatchers.IO) {
            if (musicAssistantBaseUrl.isBlank() || musicAssistantToken.isBlank()) {
                return@withContext MaSearchResults()
            }
            MusicAssistantClient(
                baseUrl = musicAssistantBaseUrl,
                token = musicAssistantToken,
            ).searchLibrary(query)
        }

    private suspend fun searchMusicRequests(query: String): List<VesperMusicRequestResult> =
        withContext(Dispatchers.IO) {
            if (musicRequestsUrl.isBlank() || musicRequestsApiKey.isBlank()) {
                return@withContext emptyList()
            }
            AurralRequestClient(
                baseUrl = musicRequestsUrl,
                apiKey = musicRequestsApiKey,
            ).searchAlbums(query)
        }

    private suspend fun requestMusicAlbum(item: VesperMusicRequestResult): String? =
        withContext(Dispatchers.IO) {
            if (musicRequestsUrl.isBlank() || musicRequestsApiKey.isBlank()) {
                return@withContext "Set up Music Requests in Vesper settings first."
            }
            runCatching {
                AurralRequestClient(
                    baseUrl = musicRequestsUrl,
                    apiKey = musicRequestsApiKey,
                ).requestAlbum(item)
            }.fold(
                onSuccess = { it },
                onFailure = { friendlyServiceError("Music requests", it) },
            )
        }

    private suspend fun searchBookLibrary(query: String): List<VesperBookLibraryResult> =
        withContext(Dispatchers.IO) {
            if (audiobookLibraryUrl.isBlank() || audiobookLibraryToken.isBlank()) {
                return@withContext emptyList()
            }
            AudiobookshelfLibraryClient(
                baseUrl = audiobookLibraryUrl,
                token = audiobookLibraryToken,
            ).search(query)
        }

    private suspend fun searchBookRequests(query: String): List<VesperBookRequestResult> =
        withContext(Dispatchers.IO) {
            if (bookRequestsUrl.isBlank() || bookRequestsApiKey.isBlank()) {
                return@withContext emptyList()
            }
            LazyLibrarianRequestClient(
                baseUrl = bookRequestsUrl,
                apiKey = bookRequestsApiKey,
            ).searchBooks(query)
        }

    private suspend fun requestBook(
        item: VesperBookRequestResult,
        audiobook: Boolean,
    ): String? = withContext(Dispatchers.IO) {
        if (bookRequestsUrl.isBlank() || bookRequestsApiKey.isBlank()) {
            return@withContext "Set up Book Requests in Vesper settings first."
        }
        runCatching {
            LazyLibrarianRequestClient(
                baseUrl = bookRequestsUrl,
                apiKey = bookRequestsApiKey,
            ).requestBook(item, audiobook)
        }.fold(
            onSuccess = { it },
            onFailure = { friendlyServiceError("Book requests", it) },
        )
    }

    private fun loadBooks() {
        if (audiobookLibraryUrl.isBlank() || audiobookLibraryToken.isBlank()) {
            booksState = BooksUiState()
            return
        }
        if (!booksState.loaded) {
            booksState = booksState.copy(loading = true, error = null)
        }
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    AudiobookshelfLibraryClient(
                        baseUrl = audiobookLibraryUrl,
                        token = audiobookLibraryToken,
                    ).loadHome()
                }
            }.onSuccess { snapshot ->
                booksState = BooksUiState(
                    loaded = true,
                    snapshot = snapshot,
                )
            }.onFailure { error ->
                booksState = if (booksState.loaded) {
                    booksState.copy(loading = false, error = null)
                } else {
                    BooksUiState(
                        error = booksConnectionError(error),
                    )
                }
            }
        }
    }

    private suspend fun loadBookDetails(itemId: String): VesperBookItem =
        withContext(Dispatchers.IO) {
            AudiobookshelfLibraryClient(
                baseUrl = audiobookLibraryUrl,
                token = audiobookLibraryToken,
            ).loadItem(itemId)
        }

    private fun booksConnectionError(error: Throwable): String {
        val url = audiobookLibraryUrl.trim()
        val host = runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault("")
        val localOnly =
            url.startsWith("http://") &&
                (
                    host.startsWith("192.168.") ||
                        host.startsWith("10.") ||
                        host.startsWith("172.") ||
                        host == "localhost" ||
                        host.endsWith(".local")
                )
        return if (localOnly) {
            "Books Away isn't set up yet. Vesper is still using your home-only book library address."
        } else {
            friendlyServiceError("Books", error)
        }
    }

    private suspend fun loadMusicArtistAlbums(artist: MaMediaItem): List<MaMediaItem> =
        withContext(Dispatchers.IO) {
            MusicAssistantClient(
                baseUrl = musicAssistantBaseUrl,
                token = musicAssistantToken,
            ).loadArtistAlbums(artist)
        }

    private suspend fun loadMusicAlbumDetails(album: MaMediaItem): MaAlbumDetails =
        withContext(Dispatchers.IO) {
            MusicAssistantClient(
                baseUrl = musicAssistantBaseUrl,
                token = musicAssistantToken,
            ).loadAlbumDetails(album)
        }

    private suspend fun loadMusicPlaylistDetails(playlist: MaMediaItem): MaPlaylistDetails =
        withContext(Dispatchers.IO) {
            MusicAssistantClient(musicAssistantBaseUrl, musicAssistantToken).loadPlaylistDetails(playlist)
        }

    private suspend fun createMusicPlaylist(name: String) {
        withContext(Dispatchers.IO) {
            MusicAssistantClient(musicAssistantBaseUrl, musicAssistantToken).createPlaylist(name)
        }
        loadMusic()
    }

    private suspend fun renameMusicPlaylist(playlist: MaMediaItem, name: String) {
        withContext(Dispatchers.IO) {
            MusicAssistantClient(musicAssistantBaseUrl, musicAssistantToken).renamePlaylist(playlist, name)
        }
        loadMusic()
    }

    private suspend fun addMusicPlaylistTrack(playlist: MaMediaItem, track: MaMediaItem) =
        withContext(Dispatchers.IO) {
            MusicAssistantClient(musicAssistantBaseUrl, musicAssistantToken).addPlaylistTrack(playlist, track)
        }

    private suspend fun removeMusicPlaylistTrack(playlist: MaMediaItem, position: Int) =
        withContext(Dispatchers.IO) {
            MusicAssistantClient(musicAssistantBaseUrl, musicAssistantToken).removePlaylistTrack(playlist, position)
        }

    private fun loadMusic() {
        if (musicAssistantBaseUrl.isBlank() || musicAssistantToken.isBlank()) {
            musicState = MusicUiState(
                error = "Connect Music Assistant in Vesper settings first."
            )
            return
        }

        // Only replace the Music UI with the loading panel on the first load.
        // Subsequent full refreshes keep the last good snapshot visible.
        if (!musicState.loaded) {
            musicState = musicState.copy(loading = true, error = null)
        }

        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    MusicAssistantClient(
                        baseUrl = musicAssistantBaseUrl,
                        token = musicAssistantToken,
                    ).loadSnapshot()
                }
            }.onSuccess { snapshot ->
                musicState = MusicUiState(
                    loaded = true,
                    snapshot = snapshot,
                )
            }.onFailure { error ->
                musicState = if (musicState.loaded) {
                    // A transient MA failure must not throw away a usable screen.
                    musicState.copy(loading = false, error = null)
                } else {
                    MusicUiState(
                        error = musicConnectionError(error)
                    )
                }
            }
        }
    }

    private fun refreshMusicPlayers() {
        if (
            musicAssistantBaseUrl.isBlank() ||
            musicAssistantToken.isBlank() ||
            !musicState.loaded
        ) return

        if (musicPlayerRefreshInFlight) {
            musicPlayerRefreshQueued = true
            return
        }

        musicPlayerRefreshInFlight = true
        lifecycleScope.launch {
            try {
                runCatching {
                    withContext(Dispatchers.IO) {
                        MusicAssistantClient(
                            baseUrl = musicAssistantBaseUrl,
                            token = musicAssistantToken,
                        ).loadPlayers()
                    }
                }.onSuccess { players ->
                    musicState = musicState.copy(
                        loading = false,
                        loaded = true,
                        error = null,
                        snapshot = musicState.snapshot.copy(
                            players = preservePausedPlayerMetadata(
                                previous = musicState.snapshot.players,
                                refreshed = players,
                            )
                        ),
                    )
                }
                // Player refreshes are best-effort. Keep the last good snapshot
                // on any transient MA/HTTP failure and retry on the next event.
            } finally {
                musicPlayerRefreshInFlight = false
                if (musicPlayerRefreshQueued) {
                    musicPlayerRefreshQueued = false
                    refreshMusicPlayers()
                }
            }
        }
    }

    private fun playMusic(item: MaMediaItem, players: List<MaPlayer>) {
        if (musicAssistantBaseUrl.isBlank() || musicAssistantToken.isBlank() || players.isEmpty()) return

        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (
                        players.size == 1 &&
                        players.first().name.equals("This Device", ignoreCase = true)
                    ) {
                        VesperMusicPlaybackService.playLocalMedia(
                            itemUri = item.uri,
                            playerId = players.first().playerId,
                        )
                    } else {
                        MusicAssistantClient(
                            baseUrl = musicAssistantBaseUrl,
                            token = musicAssistantToken,
                        ).groupAndPlay(item, players.map { it.playerId })
                    }
                }
            }.onSuccess {
                val destination = if (players.size == 1) {
                    players.first().name
                } else {
                    "${players.size} rooms"
                }
                Toast.makeText(
                    this@MobileMainActivity,
                    "Playing ${item.name} in $destination",
                    Toast.LENGTH_SHORT,
                ).show()
                delay(700)
                refreshMusicPlayers()
            }.onFailure { error ->
                Toast.makeText(
                    this@MobileMainActivity,
                    error.message ?: "Couldn't start playback.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun enqueueMusic(item: MaMediaItem, player: MaPlayer, next: Boolean) {
        if (musicAssistantBaseUrl.isBlank() || musicAssistantToken.isBlank()) return
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    MusicAssistantClient(musicAssistantBaseUrl, musicAssistantToken)
                        .enqueue(item, player.queueId, next)
                }
            }.onSuccess {
                Toast.makeText(
                    this@MobileMainActivity,
                    if (next) "Playing next: ${item.name}" else "Added to queue: ${item.name}",
                    Toast.LENGTH_SHORT,
                ).show()
                refreshMusicPlayers()
            }.onFailure { error ->
                Toast.makeText(
                    this@MobileMainActivity,
                    error.message ?: "Couldn't add to queue.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private suspend fun clearMusicQueue(player: MaPlayer): Boolean {
        if (musicAssistantBaseUrl.isBlank() || musicAssistantToken.isBlank()) return false
        return try {
            withContext(Dispatchers.IO) {
                MusicAssistantClient(musicAssistantBaseUrl, musicAssistantToken)
                    .clearUpcoming(player.queueId, player.queueCurrentItemId.orEmpty(), player.queueCurrentIndex)
            }
            true
        } catch (error: Exception) {
            Toast.makeText(this@MobileMainActivity, error.message ?: "Couldn't clear queue.", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun preservePausedPlayerMetadata(
        previous: List<MaPlayer>,
        refreshed: List<MaPlayer>,
    ): List<MaPlayer> = refreshed.map { player ->
        val prior = previous.firstOrNull { it.playerId == player.playerId }
        val preserveParkedSession =
            prior?.playbackState == "paused" &&
                player.playbackState != "playing" &&
                !player.queueEnded &&
                player.queueItemCount > 0
        val needsMetadata = player.currentTitle.isNullOrBlank() && !prior?.currentTitle.isNullOrBlank()

        if (preserveParkedSession || (player.playbackState == "paused" && needsMetadata)) {
            player.copy(
                playbackState = "paused",
                currentTitle = player.currentTitle ?: prior?.currentTitle,
                currentArtist = player.currentArtist ?: prior?.currentArtist,
                currentImageUrl = player.currentImageUrl ?: prior?.currentImageUrl,
                currentMediaType = player.currentMediaType ?: prior?.currentMediaType,
                queueItemCount = maxOf(player.queueItemCount, prior?.queueItemCount ?: 0),
                queueCurrentIndex = player.queueCurrentIndex ?: prior?.queueCurrentIndex,
                queueResumePosition = maxOf(player.queueResumePosition, prior?.queueResumePosition ?: 0),
                queueElapsedTime = maxOf(player.queueElapsedTime, prior?.queueElapsedTime ?: 0),
                queueDuration = maxOf(player.queueDuration, prior?.queueDuration ?: 0),
                canPrevious = player.canPrevious || (prior?.canPrevious == true),
                canNext = player.canNext || (prior?.canNext == true),
            )
        } else {
            player
        }
    }

    private fun setMusicPlayerPlaybackState(playerId: String, playbackState: String) {
        musicState = musicState.copy(
            snapshot = musicState.snapshot.copy(
                players = musicState.snapshot.players.map { player ->
                    if (player.playerId == playerId) {
                        player.copy(playbackState = playbackState)
                    } else {
                        player
                    }
                }
            )
        )
    }

    private fun controlMusic(player: MaPlayer, action: MusicPlayerAction) {
        if (musicAssistantBaseUrl.isBlank() || musicAssistantToken.isBlank()) return

        val wasPlaying = player.playbackState == "playing"
        if (action == MusicPlayerAction.PLAY_PAUSE) {
            // Pause is a state transition, not the end of a session. Update the
            // visible player immediately so the mini-player/Now Playing cannot
            // disappear while Music Assistant catches up.
            setMusicPlayerPlaybackState(
                playerId = player.playerId,
                playbackState = if (wasPlaying) "paused" else "playing",
            )
        }

        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val client = MusicAssistantClient(
                        baseUrl = musicAssistantBaseUrl,
                        token = musicAssistantToken,
                    )
                    when (action) {
                        MusicPlayerAction.PREVIOUS -> client.previous(player.queueId)
                        MusicPlayerAction.PLAY_PAUSE -> {
                            if (wasPlaying) client.pause(player.queueId)
                            else client.resume(player.queueId)
                        }
                        MusicPlayerAction.NEXT -> client.next(player.queueId)
                        MusicPlayerAction.VOLUME_DOWN -> player.volumeLevel?.let {
                            client.setVolume(player.playerId, it - 5)
                        }
                        MusicPlayerAction.VOLUME_UP -> player.volumeLevel?.let {
                            client.setVolume(player.playerId, it + 5)
                        }
                    }
                }
            }.onSuccess {
                delay(350)
                refreshMusicPlayers()
            }.onFailure { error ->
                refreshMusicPlayers()
                Toast.makeText(
                    this@MobileMainActivity,
                    error.message ?: "Couldn't control playback.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun seekMusic(player: MaPlayer, positionSeconds: Int) {
        if (musicAssistantBaseUrl.isBlank() || musicAssistantToken.isBlank()) return
        if (player.queueDuration <= 0) return

        val target = positionSeconds.coerceIn(0, player.queueDuration)
        musicState = musicState.copy(
            snapshot = musicState.snapshot.copy(
                players = musicState.snapshot.players.map { item ->
                    if (item.queueId == player.queueId) {
                        item.copy(
                            queueElapsedTime = target,
                            queueResumePosition = target,
                        )
                    } else {
                        item
                    }
                }
            )
        )

        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val client = MusicAssistantClient(
                        baseUrl = musicAssistantBaseUrl,
                        token = musicAssistantToken,
                    )
                    val wasPaused = player.playbackState == "paused"
                    if (wasPaused && !player.queueActive) {
                        client.resume(player.queueId)
                    }
                    client.seek(player.queueId, target)
                    if (wasPaused) {
                        client.pause(player.queueId)
                    }
                }
            }.onSuccess {
                delay(350)
                refreshMusicPlayers()
            }.onFailure { error ->
                refreshMusicPlayers()
                Toast.makeText(
                    this@MobileMainActivity,
                    error.message ?: "Couldn't seek playback.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private suspend fun loadMusicQueue(player: MaPlayer): List<MaQueueItem> {
        if (musicAssistantBaseUrl.isBlank() || musicAssistantToken.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            MusicAssistantClient(
                baseUrl = musicAssistantBaseUrl,
                token = musicAssistantToken,
            ).loadQueue(player.queueId)
        }
    }

    private suspend fun editMusicQueue(item: MaQueueItem, shift: Int?, remove: Boolean): Boolean {
        if (musicAssistantBaseUrl.isBlank() || musicAssistantToken.isBlank()) return false
        return withContext(Dispatchers.IO) {
            runCatching {
                val client = MusicAssistantClient(musicAssistantBaseUrl, musicAssistantToken)
                if (remove) client.removeQueueItem(item.queueId, item.queueItemId)
                else client.moveQueueItem(item.queueId, item.queueItemId, shift ?: return@runCatching)
            }.onFailure { error ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MobileMainActivity, error.message ?: "Queue edit failed.", Toast.LENGTH_LONG).show()
                }
            }.isSuccess
        }
    }

    private fun playMusicQueueItem(item: MaQueueItem) {
        if (musicAssistantBaseUrl.isBlank() || musicAssistantToken.isBlank()) return
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    MusicAssistantClient(
                        baseUrl = musicAssistantBaseUrl,
                        token = musicAssistantToken,
                    ).playQueueItem(item.queueId, item.queueItemId)
                }
            }.onSuccess {
                delay(350)
                refreshMusicPlayers()
            }.onFailure { error ->
                Toast.makeText(
                    this@MobileMainActivity,
                    error.message ?: "Couldn't play that queue item.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun updateMusicGroup(
        player: MaPlayer,
        selectedPlayerIds: Set<String>,
    ) {
        if (musicAssistantBaseUrl.isBlank() || musicAssistantToken.isBlank()) return

        val currentMembers = if (player.type == "group") {
            player.groupMembers.toSet()
        } else {
            (player.groupMembers + player.playerId).toSet()
        }
        val desiredMembers = if (player.type == "group") {
            selectedPlayerIds
        } else {
            selectedPlayerIds + player.playerId
        }
        val toAdd = (desiredMembers - currentMembers).toList()
        val toRemove = (currentMembers - desiredMembers)
            .filterNot { player.type != "group" && it == player.playerId }

        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    MusicAssistantClient(
                        baseUrl = musicAssistantBaseUrl,
                        token = musicAssistantToken,
                    ).updateGroup(
                        targetPlayerId = player.playerId,
                        addPlayerIds = toAdd,
                        removePlayerIds = toRemove,
                    )
                }
            }.onSuccess {
                delay(500)
                refreshMusicPlayers()
            }.onFailure { error ->
                Toast.makeText(
                    this@MobileMainActivity,
                    error.message ?: "Couldn't update the room group.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun openSettings() {
        settingsLauncher.launch(Intent(this, MobileSettingsActivity::class.java))
    }

    private fun playItem(item: BaseItemDto) {
        startActivity(
            Intent(this, MobilePlayerActivity::class.java)
                .putExtra(MobilePlayerActivity.EXTRA_ITEM_ID, item.id.toString())
        )
    }
}

private enum class MusicPlayerAction {
    PREVIOUS,
    PLAY_PAUSE,
    NEXT,
    VOLUME_DOWN,
    VOLUME_UP,
}

private data class MusicUiState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val snapshot: MusicAssistantSnapshot = MusicAssistantSnapshot(),
)

private data class BooksUiState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val snapshot: VesperBookHomeSnapshot = VesperBookHomeSnapshot(),
)

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
    val serviceLogos: Map<String, String> = emptyMap(),
    val collections: List<BaseItemDto> = emptyList(),
)

private val serviceOrder = listOf(
    "netflix",
    "prime",
    "disney",
    "apple",
    "paramount",
    "max",
    "now",
    "iplayer",
    "itvx",
    "hulu",
    "peacock",
)

private fun cleanedServiceName(name: String?): String =
    name.orEmpty()
        .replace(Regex("^\\s*streaming\\s*[:\\-]\\s*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+(collection|library)$", RegexOption.IGNORE_CASE), "")
        .trim()

private fun serviceKey(name: String?): String? {
    val normalized = cleanedServiceName(name).lowercase()

    return when {
        normalized.contains("netflix") -> "netflix"
        normalized.contains("amazon") || normalized.contains("prime video") || normalized == "prime" -> "prime"
        normalized.contains("disney") -> "disney"
        normalized.contains("apple tv") || normalized == "apple" -> "apple"
        normalized.contains("paramount") -> "paramount"
        normalized == "max" || normalized.contains("hbo max") -> "max"
        normalized == "now" || normalized.startsWith("now tv") -> "now"
        normalized.contains("iplayer") -> "iplayer"
        normalized.contains("itvx") -> "itvx"
        normalized.contains("hulu") -> "hulu"
        normalized.contains("peacock") -> "peacock"
        else -> null
    }
}

private fun isServiceCollection(item: BaseItemDto): Boolean =
    serviceKey(item.name) != null || item.name.orEmpty().trim().lowercase().startsWith("streaming:")

private fun isServiceArtifact(item: BaseItemDto): Boolean =
    serviceKey(item.name) != null || item.name.orEmpty().trim().lowercase().startsWith("streaming:")

private fun serviceDisplayName(name: String?): String = when (serviceKey(name)) {
    "netflix" -> "Netflix"
    "prime" -> "prime video"
    "disney" -> "Disney+"
    "apple" -> "Apple TV+"
    "paramount" -> "Paramount+"
    "max" -> if (cleanedServiceName(name).contains("hbo", ignoreCase = true)) "HBO Max" else "Max"
    "now" -> "NOW"
    "iplayer" -> "BBC iPlayer"
    "itvx" -> "ITVX"
    "hulu" -> "Hulu"
    "peacock" -> "Peacock"
    else -> cleanedServiceName(name).ifBlank { "Streaming service" }
}

private fun serviceSortOrder(item: BaseItemDto): Int {
    val key = serviceKey(item.name) ?: return Int.MAX_VALUE
    return serviceOrder.indexOf(key).let { if (it >= 0) it else Int.MAX_VALUE }
}

private fun serviceLogoResource(name: String?): Int? = when (serviceKey(name)) {
    "netflix" -> R.drawable.service_netflix
    "prime" -> R.drawable.service_prime_video
    "disney" -> R.drawable.logo_disneyplus
    "apple" -> R.drawable.service_apple_tv
    "paramount" -> R.drawable.service_paramount_plus
    "max" -> R.drawable.service_max
    "now" -> R.drawable.logo_now
    else -> null
}

private fun serviceLogoUrl(
    name: String?,
    logos: Map<String, String>,
): String? {
    val normalized = serviceDisplayName(name).trim().lowercase()
    val candidates = when {
        normalized.contains("netflix") -> listOf("netflix")
        normalized.contains("disney") -> listOf("disney plus", "disney+")
        normalized.contains("amazon") || normalized.contains("prime") ->
            listOf("amazon prime video", "prime video")
        normalized.contains("apple") -> listOf("apple tv plus", "apple tv+", "apple tv")
        normalized.contains("paramount") -> listOf("paramount plus", "paramount+")
        normalized == "max" || normalized.contains("hbo") -> listOf("hbo max", "max")
        normalized.startsWith("now") -> listOf("now", "now tv")
        normalized.contains("iplayer") -> listOf("bbc iplayer")
        normalized.contains("itvx") -> listOf("itvx")
        normalized.contains("peacock") -> listOf("peacock")
        normalized.contains("hulu") -> listOf("hulu")
        else -> listOf(normalized)
    }

    candidates.forEach { candidate ->
        logos[candidate]?.let { return it }
    }

    return logos.entries
        .firstOrNull { (provider, _) ->
            provider.contains(normalized) || normalized.contains(provider)
        }
        ?.value
}

private fun collectionDisplayName(name: String?): String =
    name.orEmpty()
        .replace(Regex("\\s+collection$", RegexOption.IGNORE_CASE), "")
        .ifBlank { "Collection" }

private enum class MobileTab(val label: String, val icon: String) {
    HOME("Home", "⌂"),
    VIDEO("Video", "▣"),
    MUSIC("Music", "♫"),
    BOOKS("Books", "▤"),
    MOVIES("Movies", "▣"),
    TV("TV", "▤"),
    MYV("MyV", "V"),
    SEARCH("Search", "⌕"),
}

private val primaryMobileTabs = listOf(
    MobileTab.HOME,
    MobileTab.VIDEO,
    MobileTab.MUSIC,
    MobileTab.BOOKS,
)

@Composable
private fun VesperMobile(
    state: MobileHomeState,
    popularity: PopularityState,
    popularityScope: PopularityScope,
    musicState: MusicUiState,
    musicFavourites: List<MaMediaItem>,
    onToggleMusicFavourite: (MaMediaItem) -> Unit,
    onLoadMyVTrackGenres: suspend (MaMediaItem) -> List<String>,
    musicConfigured: Boolean,
    booksState: BooksUiState,
    booksConfigured: Boolean,
    showPersistentMiniPlayer: Boolean,
    tmdbConfigured: Boolean,
    selected: BaseItemDto?,
    userName: String,
    userAvatarUrl: String?,
    api: ApiClient,
    seerrConfigured: Boolean,
    musicRequestsConfigured: Boolean,
    bookRequestsConfigured: Boolean,
    audiobookLibraryConfigured: Boolean,
    onLibrarySearch: suspend (String) -> List<BaseItemDto>,
    onSeerrSearch: suspend (String) -> List<SeerrSearchResult>,
    onSeerrRequest: suspend (SeerrSearchResult) -> String?,
    onMusicLibrarySearch: suspend (String) -> MaSearchResults,
    onMusicRequestSearch: suspend (String) -> List<VesperMusicRequestResult>,
    onMusicRequest: suspend (VesperMusicRequestResult) -> String?,
    onBookLibrarySearch: suspend (String) -> List<VesperBookLibraryResult>,
    onBookRequestSearch: suspend (String) -> List<VesperBookRequestResult>,
    onBookRequest: suspend (VesperBookRequestResult, Boolean) -> String?,
    onRetryBooks: () -> Unit,
    onLoadBookDetails: suspend (String) -> VesperBookItem,
    onSelect: (BaseItemDto) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (BaseItemDto) -> Unit,
    onToggleFavorite: (BaseItemDto) -> Unit,
    onSwitchProfile: () -> Unit,
    onTabSelected: (MobileTab) -> Unit,
    onRetryMusic: () -> Unit,
    onLoadArtistAlbums: suspend (MaMediaItem) -> List<MaMediaItem>,
    onLoadAlbumDetails: suspend (MaMediaItem) -> MaAlbumDetails,
    onLoadPlaylistDetails: suspend (MaMediaItem) -> MaPlaylistDetails,
    onCreateMusicPlaylist: suspend (String) -> Unit,
    onRenameMusicPlaylist: suspend (MaMediaItem, String) -> Unit,
    onAddMusicPlaylistTrack: suspend (MaMediaItem, MaMediaItem) -> Unit,
    onRemoveMusicPlaylistTrack: suspend (MaMediaItem, Int) -> Unit,
    onPlayMusic: (MaMediaItem, List<MaPlayer>) -> Unit,
    onControlMusic: (MaPlayer, MusicPlayerAction) -> Unit,
    onSeekMusic: (MaPlayer, Int) -> Unit,
    onLoadMusicQueue: suspend (MaPlayer) -> List<MaQueueItem>,
    onPlayMusicQueueItem: (MaQueueItem) -> Unit,
    onEditMusicQueue: suspend (MaQueueItem, Int?, Boolean) -> Boolean,
    onClearMusicQueue: suspend (MaPlayer) -> Boolean,
    onEnqueueMusic: (MaMediaItem, MaPlayer, Boolean) -> Unit,
    onUpdateMusicGroup: (MaPlayer, Set<String>) -> Unit,
    onSettings: () -> Unit,
) {
    var tab by remember { mutableStateOf(MobileTab.HOME) }
    var myVSection by remember { mutableStateOf("Video") }
    var myVReturnTab by remember { mutableStateOf(MobileTab.HOME) }
    var nowPlayingQueueId by remember { mutableStateOf<String?>(null) }
    var nowPlayingRoomsPlayerId by remember { mutableStateOf<String?>(null) }

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
            BackHandler(enabled = tab != MobileTab.HOME) {
                tab = when (tab) {
                    MobileTab.MYV -> myVReturnTab
                    MobileTab.MOVIES, MobileTab.TV -> MobileTab.VIDEO
                    else -> MobileTab.HOME
                }
            }

            val openTab: (MobileTab) -> Unit = { destination ->
                if (destination == MobileTab.MYV && tab != MobileTab.MYV) {
                    myVReturnTab = tab
                    myVSection = when (tab) {
                        MobileTab.MUSIC -> "Music"
                        MobileTab.VIDEO, MobileTab.MOVIES, MobileTab.TV -> "Video"
                        else -> when {
                            state.myV.isEmpty() && musicFavourites.isNotEmpty() -> "Music"
                            state.myV.isNotEmpty() && musicFavourites.isEmpty() -> "Video"
                            else -> myVSection
                        }
                    }
                }
                tab = destination
                onTabSelected(destination)
            }

            val musicSessions = activeMusicSessions(musicState.snapshot.players)
            val persistentMusicPlayer = musicSessions.firstOrNull()

            when (tab) {
                MobileTab.HOME -> MobileHome(
                    state = state,
                    musicState = musicState,
                    userName = userName,
                    userAvatarUrl = userAvatarUrl,
                    api = api,
                    onSelect = onSelect,
                    onRetry = onRetry,
                    onPlay = onPlay,
                    onToggleFavorite = onToggleFavorite,
                    onSwitchProfile = onSwitchProfile,
                    onSettings = onSettings,
                    onOpenTab = openTab,
                    expanded = expanded,
                )
                MobileTab.VIDEO -> VideoHub(
                    state = state,
                    popularity = popularity,
                    popularityScope = popularityScope,
                    userName = userName,
                    userAvatarUrl = userAvatarUrl,
                    api = api,
                    onSelect = onSelect,
                    onPlay = onPlay,
                    onToggleFavorite = onToggleFavorite,
                    onSwitchProfile = onSwitchProfile,
                    onSettings = onSettings,
                    onOpenTab = openTab,
                    expanded = expanded,
                )
                MobileTab.MUSIC -> MusicHub(
                    state = musicState,
                    configured = musicConfigured,
                    userName = userName,
                    userAvatarUrl = userAvatarUrl,
                    onRetry = onRetryMusic,
                    onLoadArtistAlbums = onLoadArtistAlbums,
                    onLoadAlbumDetails = onLoadAlbumDetails,
                    onSearchMusicLibrary = onMusicLibrarySearch,
                    onOpenMyV = { openTab(MobileTab.MYV) },
                    musicFavourites = musicFavourites,
                    onToggleMusicFavourite = onToggleMusicFavourite,
                    onLoadPlaylistDetails = onLoadPlaylistDetails,
                    onCreatePlaylist = onCreateMusicPlaylist,
                    onRenamePlaylist = onRenameMusicPlaylist,
                    onAddPlaylistTrack = onAddMusicPlaylistTrack,
                    onRemovePlaylistTrack = onRemoveMusicPlaylistTrack,
                    onEnqueue = onEnqueueMusic,
                    onPlay = onPlayMusic,
                    onControl = onControlMusic,
                    onUpdateGroup = onUpdateMusicGroup,
                    onOpenNowPlaying = { player ->
                        nowPlayingQueueId = player.queueId
                    },
                    onSwitchProfile = onSwitchProfile,
                    onSettings = onSettings,
                )
                MobileTab.BOOKS -> BooksHub(
                    state = booksState,
                    configured = booksConfigured,
                    userName = userName,
                    userAvatarUrl = userAvatarUrl,
                    onRetry = onRetryBooks,
                    onLoadDetails = onLoadBookDetails,
                    onSwitchProfile = onSwitchProfile,
                    onSettings = onSettings,
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
                MobileTab.MYV -> MyVBrowser(
                    videos = state.myV,
                    tracks = musicFavourites,
                    musicPlayers = musicState.snapshot.players,
                    selectedSection = myVSection,
                    onSectionChange = { myVSection = it },
                    onLoadTrackGenres = onLoadMyVTrackGenres,
                    api = api,
                    onSelectVideo = onSelect,
                    onToggleVideo = onToggleFavorite,
                    onToggleMusic = onToggleMusicFavourite,
                    onPlayMusic = onPlayMusic,
                    popularity = popularity,
                    popularityScope = popularityScope,
                    tmdbConfigured = tmdbConfigured,
                    expanded = expanded,
                )
                MobileTab.SEARCH -> SearchBrowse(
                    state = state,
                    api = api,
                    videoRequestsConfigured = seerrConfigured,
                    musicLibraryConfigured = musicConfigured,
                    musicRequestsConfigured = musicRequestsConfigured,
                    bookRequestsConfigured = bookRequestsConfigured,
                    audiobookLibraryConfigured = audiobookLibraryConfigured,
                    onLibrarySearch = onLibrarySearch,
                    onVideoRequestSearch = onSeerrSearch,
                    onVideoRequest = onSeerrRequest,
                    onMusicLibrarySearch = onMusicLibrarySearch,
                    onMusicRequestSearch = onMusicRequestSearch,
                    onMusicRequest = onMusicRequest,
                    onBookLibrarySearch = onBookLibrarySearch,
                    onBookRequestSearch = onBookRequestSearch,
                    onBookRequest = onBookRequest,
                    musicPlayers = musicState.snapshot.players,
                    onPlayMusic = onPlayMusic,
                    onEnqueueMusic = onEnqueueMusic,
                    musicFavourites = musicFavourites,
                    onToggleMusicFavourite = onToggleMusicFavourite,
                    onSelect = onSelect,
                    onToggleFavorite = onToggleFavorite,
                    expanded = expanded,
                )
            }

            if (tab in primaryMobileTabs) {
                PersistentProfileButton(
                    userName = userName,
                    userAvatarUrl = userAvatarUrl,
                    onSwitchProfile = onSwitchProfile,
                    onSettings = onSettings,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 14.dp, end = 18.dp),
                )
            }

            if (showPersistentMiniPlayer && musicSessions.isNotEmpty()) {
                VesperMiniPlayer(
                    players = musicSessions,
                    onOpen = { player ->
                        nowPlayingQueueId = player.queueId
                    },
                    onControl = { player, action ->
                        onControlMusic(player, action)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 26.dp, end = 26.dp, bottom = 92.dp),
                )
            }

            MobileNavDock(
                active = tab,
                onSelect = openTab,
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            if (nowPlayingQueueId != null) {
                VesperNowPlaying(
                    players = if (musicSessions.isNotEmpty()) musicSessions else musicState.snapshot.players,
                    initialQueueId = nowPlayingQueueId,
                    onDismiss = { nowPlayingQueueId = null },
                    onControl = { player, action ->
                        onControlMusic(player, action)
                    },
                    onSeek = { player, position ->
                        onSeekMusic(player, position)
                    },
                    onLoadQueue = onLoadMusicQueue,
                    onPlayQueueItem = onPlayMusicQueueItem,
                    onEditQueue = onEditMusicQueue,
                    onClearQueue = onClearMusicQueue,
                    musicFavourites = musicFavourites,
                    onToggleMusicFavourite = onToggleMusicFavourite,
                    onSearchMusicLibrary = onMusicLibrarySearch,
                    onRooms = { player ->
                        nowPlayingRoomsPlayerId = player.playerId
                    },
                )
            }

            nowPlayingRoomsPlayerId?.let { playerId ->
                musicState.snapshot.players.firstOrNull { it.playerId == playerId }?.let { player ->
                    MusicGroupManager(
                        player = player,
                        players = musicState.snapshot.players,
                        onDismiss = { nowPlayingRoomsPlayerId = null },
                        onSave = { selectedIds ->
                            nowPlayingRoomsPlayerId = null
                            onUpdateMusicGroup(player, selectedIds)
                        },
                    )
                }
            }
        }

    }
}

private fun activeMusicSessions(players: List<MaPlayer>): List<MaPlayer> {
    val candidates = players.filter {
        it.playbackState in setOf("playing", "paused") &&
            !it.currentTitle.isNullOrBlank()
    }
    if (candidates.isEmpty()) return emptyList()

    // A Music Assistant queue is the playback session. Synced/grouped players
    // can all point at the same queue, while two rooms playing different media
    // have different queue ids. Build exactly one swipe page per queue.
    return candidates
        .groupBy { it.queueId }
        .values
        .map { queuePlayers ->
            queuePlayers.firstOrNull { it.type == "group" }
                ?: queuePlayers.firstOrNull { it.syncedTo == null }
                ?: queuePlayers.first()
        }
        .sortedWith(
            compareBy<MaPlayer> {
                if (it.name.equals("This Device", ignoreCase = true)) 0 else 1
            }.thenByDescending { it.playbackState == "playing" }
                .thenBy { it.name.lowercase() }
        )
}

@Composable
private fun VesperMiniPlayer(
    players: List<MaPlayer>,
    onOpen: (MaPlayer) -> Unit,
    onControl: (MaPlayer, MusicPlayerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (players.isEmpty()) return

    val queueIds = players.map { it.queueId }
    var currentIndex by remember(queueIds) { mutableStateOf(0) }
    val safeIndex = currentIndex.coerceIn(0, players.lastIndex)
    if (safeIndex != currentIndex) currentIndex = safeIndex
    val player = players[safeIndex]

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xF01A1B28))
            .border(1.dp, Color(0x665B47D8), RoundedCornerShape(22.dp))
            .pointerInput(queueIds, currentIndex) {
                var dragDistance = 0f
                val threshold = 44.dp.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { dragDistance = 0f },
                    onHorizontalDrag = { _, dragAmount -> dragDistance += dragAmount },
                    onDragEnd = {
                        when {
                            dragDistance <= -threshold && currentIndex < players.lastIndex ->
                                currentIndex += 1
                            dragDistance >= threshold && currentIndex > 0 ->
                                currentIndex -= 1
                        }
                        dragDistance = 0f
                    },
                    onDragCancel = { dragDistance = 0f },
                )
            }
            .clickable { onOpen(player) }
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF111B2C)),
            contentAlignment = Alignment.Center,
        ) {
            if (!player.currentImageUrl.isNullOrBlank()) {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    url = player.currentImageUrl,
                    scaleType = ImageView.ScaleType.CENTER_CROP,
                )
            } else {
                BasicText(
                    "♫",
                    style = TextStyle(color = Color(0xFFA98CFF), fontSize = 22.sp, fontWeight = FontWeight.Bold),
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            BasicText(
                player.currentTitle ?: "Music",
                style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            BasicText(
                buildList {
                    player.currentArtist?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }?.let(::add)
                    add(player.name)
                    if (players.size > 1) add("${safeIndex + 1}/${players.size}")
                }.joinToString(" · "),
                style = TextStyle(color = Color(0xFF8E97A4), fontSize = 9.sp),
                maxLines = 1,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MusicCircleButton(
                label = "⏮",
                enabled = player.canPrevious,
                compact = true,
            ) { onControl(player, MusicPlayerAction.PREVIOUS) }

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(Color(0xFFEAF4FB))
                    .clickable { onControl(player, MusicPlayerAction.PLAY_PAUSE) },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    if (player.playbackState == "playing") "Ⅱ" else "▶",
                    modifier = if (player.playbackState == "playing") Modifier else Modifier.offset(x = 1.dp),
                    style = TextStyle(
                        color = Color(0xFF101820),
                        fontSize = if (player.playbackState == "playing") 15.sp else 19.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }

            MusicCircleButton(
                label = "⏭",
                enabled = player.canNext,
                compact = true,
            ) { onControl(player, MusicPlayerAction.NEXT) }
        }
    }
}

@Composable
private fun VesperNowPlaying(
    players: List<MaPlayer>,
    initialQueueId: String?,
    onDismiss: () -> Unit,
    onControl: (MaPlayer, MusicPlayerAction) -> Unit,
    onSeek: (MaPlayer, Int) -> Unit,
    onLoadQueue: suspend (MaPlayer) -> List<MaQueueItem>,
    onPlayQueueItem: (MaQueueItem) -> Unit,
    onEditQueue: suspend (MaQueueItem, Int?, Boolean) -> Boolean,
    onClearQueue: suspend (MaPlayer) -> Boolean,
    musicFavourites: List<MaMediaItem>,
    onToggleMusicFavourite: (MaMediaItem) -> Unit,
    onSearchMusicLibrary: suspend (String) -> MaSearchResults,
    onRooms: (MaPlayer) -> Unit,
) {
    if (players.isEmpty()) return

    val queueIds = players.map { it.queueId }
    var currentIndex by remember(queueIds, initialQueueId) {
        mutableStateOf(
            players.indexOfFirst { it.queueId == initialQueueId }
                .takeIf { it >= 0 }
                ?: 0
        )
    }
    val safeIndex = currentIndex.coerceIn(0, players.lastIndex)
    if (safeIndex != currentIndex) currentIndex = safeIndex
    val player = players[safeIndex]
    var queueOpen by remember(player.queueId) { mutableStateOf(false) }
    var findCurrentTrack by remember(player.queueId, player.currentTitle) { mutableStateOf(false) }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF06090E))
                .padding(horizontal = 24.dp, vertical = 18.dp),
        ) {
            val compactHeight = maxHeight < 760.dp
            val artworkSize = minOf(
                maxWidth * if (compactHeight) 0.66f else 0.78f,
                maxHeight * if (compactHeight) 0.34f else 0.40f,
                360.dp,
            )
            val largeGap = if (compactHeight) 12.dp else 18.dp
            val smallGap = if (compactHeight) 6.dp else 8.dp
            val roomCount = if (player.type == "group") {
                player.groupMembers.distinct().size
            } else {
                (player.groupMembers + player.playerId).distinct().size
            }.coerceAtLeast(1)
            val roomLabel = if (roomCount > 1) "$roomCount rooms" else player.name

            AnimatedContent(
                targetState = queueOpen,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    if (targetState) {
                        (slideInVertically(animationSpec = tween(260), initialOffsetY = { it }) +
                            fadeIn(animationSpec = tween(180)))
                            .togetherWith(slideOutVertically(animationSpec = tween(260), targetOffsetY = { -it / 3 }) +
                                fadeOut(animationSpec = tween(160)))
                    } else {
                        (slideInVertically(animationSpec = tween(260), initialOffsetY = { -it / 3 }) +
                            fadeIn(animationSpec = tween(180)))
                            .togetherWith(slideOutVertically(animationSpec = tween(260), targetOffsetY = { it }) +
                                fadeOut(animationSpec = tween(160)))
                    }
                },
                label = "Now Playing Up Next transition",
            ) { showQueue ->
            if (showQueue) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF151420))
                            .clickable { queueOpen = false }
                            .pointerInput(player.queueId) {
                                var dragDistance = 0f
                                val threshold = 65.dp.toPx()
                                detectVerticalDragGestures(
                                    onDragStart = { dragDistance = 0f },
                                    onVerticalDrag = { change, amount ->
                                        dragDistance += amount
                                        if (dragDistance > threshold) {
                                            queueOpen = false
                                            change.consume()
                                        }
                                    },
                                    onDragEnd = { dragDistance = 0f },
                                    onDragCancel = { dragDistance = 0f },
                                )
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color(0xFF292039)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!player.currentImageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    modifier = Modifier.fillMaxSize(),
                                    url = player.currentImageUrl,
                                    scaleType = ImageView.ScaleType.CENTER_CROP,
                                )
                            } else {
                                BasicText("♫", style = TextStyle(color = Color(0xFFA98CFF), fontSize = 24.sp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            BasicText(
                                player.currentTitle ?: "Now Playing",
                                style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                maxLines = 1,
                            )
                            BasicText(
                                player.currentArtist ?: roomLabel,
                                style = TextStyle(color = Color(0xFF9CA4B0), fontSize = 12.sp),
                                maxLines = 1,
                            )
                        }
                        BasicText(
                            "Now Playing",
                            style = TextStyle(color = Color(0xFFC6ABFF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.weight(1f)) {
                        MusicQueueView(
                            player = player,
                            onBack = { queueOpen = false },
                            showBackButton = false,
                            onLoadQueue = onLoadQueue,
                            onEditQueue = onEditQueue,
                            onClearQueue = onClearQueue,
                            onPlayQueueItem = { item ->
                                onPlayQueueItem(item)
                                queueOpen = false
                            },
                        )
                    }
                }
            } else {
                Column(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(player.queueId) {
                        var verticalDrag = 0f
                        val threshold = 65.dp.toPx()
                        detectVerticalDragGestures(
                            onDragStart = { verticalDrag = 0f },
                            onVerticalDrag = { change, amount ->
                                verticalDrag += amount
                                if (verticalDrag < -threshold) {
                                    queueOpen = true
                                    verticalDrag = 0f
                                    change.consume()
                                }
                            },
                            onDragEnd = { verticalDrag = 0f },
                            onDragCancel = { verticalDrag = 0f },
                        )
                    }
                    .pointerInput(queueIds, currentIndex) {
                        var dragDistance = 0f
                        val threshold = 64.dp.toPx()
                        detectHorizontalDragGestures(
                            onDragStart = { dragDistance = 0f },
                            onHorizontalDrag = { _, dragAmount ->
                                dragDistance += dragAmount
                            },
                            onDragEnd = {
                                when {
                                    dragDistance <= -threshold && currentIndex < players.lastIndex ->
                                        currentIndex += 1
                                    dragDistance >= threshold && currentIndex > 0 ->
                                        currentIndex -= 1
                                }
                                dragDistance = 0f
                            },
                            onDragCancel = { dragDistance = 0f },
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(21.dp))
                            .background(Color(0x22FFFFFF))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText("×", style = TextStyle(color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.SemiBold))
                    }

                    Spacer(Modifier.weight(1f))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BasicText(
                            "NOW PLAYING",
                            style = TextStyle(
                                color = Color(0xFFA98CFF),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.8.sp,
                            ),
                        )
                        if (players.size > 1) {
                            Spacer(Modifier.height(4.dp))
                            BasicText(
                                "$roomLabel  ·  ${safeIndex + 1} of ${players.size}",
                                style = TextStyle(color = Color(0xFF7F8793), fontSize = 9.sp),
                                maxLines = 1,
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.width(42.dp))
                }

                if (players.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        players.indices.forEach { index ->
                            Box(
                                modifier = Modifier
                                    .width(if (index == safeIndex) 18.dp else 6.dp)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        if (index == safeIndex) Color(0xFFA98CFF)
                                        else Color(0x445F6670)
                                    )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(largeGap))

                Box(
                    modifier = Modifier
                        .size(artworkSize)
                        .clip(RoundedCornerShape(30.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF201A3B),
                                    Color(0xFF101A2A),
                                    Color(0xFF0B1018),
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!player.currentImageUrl.isNullOrBlank()) {
                        AsyncImage(
                            modifier = Modifier.fillMaxSize(),
                            url = player.currentImageUrl,
                            scaleType = ImageView.ScaleType.CENTER_CROP,
                        )
                    } else {
                        BasicText(
                            "♫",
                            style = TextStyle(color = Color(0xFFA98CFF), fontSize = 86.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                }

                Spacer(Modifier.height(largeGap))

                val knownTrack = player.currentTrack
                if (knownTrack != null) {
                    MyVMark(
                        favorite = musicFavourites.any { it.uri == knownTrack.uri },
                        modifier = Modifier.clickable { onToggleMusicFavourite(knownTrack) }
                            .padding(vertical = 5.dp),
                        withLabel = true,
                    )
                    Spacer(Modifier.height(smallGap))
                } else if (!player.currentTitle.isNullOrBlank()) {
                    BasicText(
                        "V+  Find in library to save to MyV",
                        modifier = Modifier.clickable { findCurrentTrack = true }
                            .padding(vertical = 5.dp),
                        style = TextStyle(color = Color(0xFFC4B5FD),
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(Modifier.height(smallGap))
                }

                BasicText(
                    player.currentTitle ?: "Nothing playing",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 29.sp,
                    ),
                    maxLines = 2,
                )

                if (!player.currentArtist.isNullOrBlank()) {
                    Spacer(Modifier.height(smallGap))
                    BasicText(
                        player.currentArtist,
                        style = TextStyle(color = Color(0xFF9CA4B0), fontSize = 15.sp),
                        maxLines = 1,
                    )
                }

                if (
                    player.queueDuration > 0 &&
                    player.currentMediaType !in setOf("radio", "audio_source")
                ) {
                    Spacer(Modifier.height(smallGap))
                    NowPlayingProgress(
                        player = player,
                        onSeek = { position -> onSeek(player, position) },
                    )
                }

                Spacer(Modifier.height(largeGap))

                val queueSummary = when {
                    player.currentMediaType in setOf("radio", "audio_source") -> "Live"
                    player.playbackState == "paused" -> "Paused"
                    player.queueItemCount > 0 && player.queueCurrentIndex != null ->
                        "Track ${player.queueCurrentIndex + 1} of ${player.queueItemCount}"
                    else -> "Playing"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0x99121722))
                        .padding(horizontal = 15.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        BasicText(
                            roomLabel,
                            style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(2.dp))
                        BasicText(
                            queueSummary,
                            style = TextStyle(color = Color(0xFF8A93A1), fontSize = 10.sp),
                        )
                    }
                }

                Spacer(Modifier.height(largeGap))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MusicCircleButton(
                        label = "⏮",
                        enabled = player.canPrevious,
                    ) { onControl(player, MusicPlayerAction.PREVIOUS) }

                    Spacer(Modifier.width(22.dp))

                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .clip(RoundedCornerShape(33.dp))
                            .background(Color(0xFFEAF4FB))
                            .clickable { onControl(player, MusicPlayerAction.PLAY_PAUSE) },
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            if (player.playbackState == "playing") "Ⅱ" else "▶",
                            style = TextStyle(
                                color = Color(0xFF101820),
                                fontSize = if (player.playbackState == "playing") 22.sp else 28.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }

                    Spacer(Modifier.width(22.dp))

                    MusicCircleButton(
                        label = "⏭",
                        enabled = player.canNext,
                    ) { onControl(player, MusicPlayerAction.NEXT) }
                }

                Spacer(Modifier.height(largeGap))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF141A25))
                            .border(1.dp, Color(0x334D4A75), RoundedCornerShape(18.dp))
                            .clickable { onRooms(player) },
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            "Rooms",
                            style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF141A25))
                            .border(1.dp, Color(0x334D4A75), RoundedCornerShape(18.dp))
                            .clickable { queueOpen = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            "Up Next",
                            style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        )
                    }

                    if (player.volumeLevel != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF141A25))
                                .border(1.dp, Color(0x334D4A75), RoundedCornerShape(18.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BasicText(
                                    "−",
                                    modifier = Modifier
                                        .clickable { onControl(player, MusicPlayerAction.VOLUME_DOWN) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                                )
                                BasicText(
                                    "${player.volumeLevel}%",
                                    style = TextStyle(color = Color(0xFFC8B9FF), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                )
                                BasicText(
                                    "+",
                                    modifier = Modifier
                                        .clickable { onControl(player, MusicPlayerAction.VOLUME_UP) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                                )
                            }
                        }
                    }
                }
            }
            }
            }
            if (findCurrentTrack) {
                MusicFavouriteSearchPopup(
                    title = "Find this song in MyV",
                    initialQuery = player.currentTitle.orEmpty(),
                    onSearch = onSearchMusicLibrary,
                    favorites = musicFavourites,
                    onToggle = onToggleMusicFavourite,
                    onDismiss = { findCurrentTrack = false },
                )
            }
        }
    }
}

@Composable
private fun MusicQueueView(
    player: MaPlayer,
    onBack: () -> Unit,
    showBackButton: Boolean = true,
    onLoadQueue: suspend (MaPlayer) -> List<MaQueueItem>,
    onPlayQueueItem: (MaQueueItem) -> Unit,
    onEditQueue: suspend (MaQueueItem, Int?, Boolean) -> Boolean,
    onClearQueue: suspend (MaPlayer) -> Boolean,
) {
    var loading by remember(player.queueId) { mutableStateOf(true) }
    var error by remember(player.queueId) { mutableStateOf<String?>(null) }
    var queue by remember(player.queueId) { mutableStateOf<List<MaQueueItem>>(emptyList()) }
    val queueListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var editingId by remember(player.queueId) { mutableStateOf<String?>(null) }
    var pendingRemove by remember(player.queueId) { mutableStateOf<MaQueueItem?>(null) }
    var confirmClear by remember(player.queueId) { mutableStateOf(false) }
    var selectedQueueItemId by remember(player.queueId) { mutableStateOf<String?>(null) }
    var queueActionsItemId by remember(player.queueId) { mutableStateOf<String?>(null) }

    LaunchedEffect(player.queueId) {
        loading = true
        error = null
        runCatching { onLoadQueue(player) }
            .onSuccess { queue = it }
            .onFailure { error = it.message ?: "Couldn't load the queue." }
        loading = false
    }

    val currentPosition = queue.indexOfFirst { it.queueItemId == player.queueCurrentItemId }
        .takeIf { it >= 0 }
        ?: player.queueCurrentIndex?.takeIf { it in queue.indices }
        ?: -1
    val upcoming = if (currentPosition >= 0) queue.drop(currentPosition) else emptyList()

    LaunchedEffect(player.queueId, loading) {
        if (!loading) queueListState.scrollToItem(0)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBackButton) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(Color(0x22FFFFFF))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("‹", style = TextStyle(color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold))
            }
            Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                BasicText(
                    "Up Next",
                    style = TextStyle(color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold),
                )
                BasicText(
                    player.name,
                    style = TextStyle(color = Color(0xFF8F98A5), fontSize = 12.sp),
                )
            }
            BasicText(
                if (queue.isEmpty()) "" else "${(upcoming.size - 1).coerceAtLeast(0)} upcoming",
                style = TextStyle(color = Color(0xFF8F98A5), fontSize = 11.sp),
            )
            Spacer(Modifier.width(12.dp))
            BasicText(
                "Clear Upcoming",
                modifier = Modifier.clickable(enabled = upcoming.size > 1 && editingId == null && currentPosition >= 0) {
                    confirmClear = true
                }.padding(8.dp),
                style = TextStyle(color = Color(0xFFFFA6A6), fontSize = 12.sp),
            )
        }

        Spacer(Modifier.height(18.dp))

        if (confirmClear) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    "Remove upcoming songs? The current song keeps playing.",
                    modifier = Modifier.weight(1f),
                    style = TextStyle(color = Color.White, fontSize = 12.sp),
                )
                BasicText("Cancel", modifier = Modifier.clickable { confirmClear = false }.padding(8.dp),
                    style = TextStyle(color = Color(0xFF9CA4B0), fontSize = 12.sp))
                BasicText("Clear", modifier = Modifier.clickable {
                    confirmClear = false
                    scope.launch {
                        editingId = "clear"
                        try {
                            if (onClearQueue(player)) queue = queue.take(currentPosition + 1)
                        } finally {
                            editingId = null
                        }
                    }
                }.padding(8.dp), style = TextStyle(color = Color(0xFFFFA6A6), fontSize = 12.sp))
            }
            Spacer(Modifier.height(10.dp))
        }

        pendingRemove?.let { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText("Remove ${item.name}?", modifier = Modifier.weight(1f), style = TextStyle(color = Color.White, fontSize = 12.sp))
                BasicText("Cancel", modifier = Modifier.clickable { pendingRemove = null }.padding(8.dp), style = TextStyle(color = Color(0xFF9CA4B0), fontSize = 12.sp))
                BasicText("Remove", modifier = Modifier.clickable {
                    pendingRemove = null
                    scope.launch {
                        editingId = item.queueItemId
                        if (onEditQueue(item, null, true)) queue = queue.filterNot { it.queueItemId == item.queueItemId }
                        editingId = null
                    }
                }.padding(8.dp), style = TextStyle(color = Color(0xFFFFA6A6), fontSize = 12.sp))
            }
            Spacer(Modifier.height(10.dp))
        }

        when {
            loading -> {
                BasicText(
                    "Loading queue…",
                    style = TextStyle(color = Color(0xFF9CA4B0), fontSize = 14.sp),
                )
            }
            error != null -> {
                BasicText(
                    error.orEmpty(),
                    style = TextStyle(color = Color(0xFFFFA6A6), fontSize = 13.sp),
                )
            }
            upcoming.isEmpty() -> {
                BasicText(
                    "Current track unavailable. Refresh the player to show Up Next.",
                    style = TextStyle(color = Color(0xFF9CA4B0), fontSize = 14.sp),
                )
            }
            else -> {
                LazyColumn(
                    state = queueListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    items(upcoming, key = { it.queueItemId }) { item ->
                        val current = item.queueItemId == upcoming.firstOrNull()?.queueItemId
                        val played = false
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    when {
                                        current -> Color(0x663D286D)
                                        item.queueItemId == selectedQueueItemId -> Color(0x332C244A)
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable(enabled = !current && !played) {
                                    selectedQueueItemId = if (selectedQueueItemId == item.queueItemId) null else item.queueItemId
                                }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(Color(0xFF111A23)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (!item.imageUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        modifier = Modifier.fillMaxSize(),
                                        url = item.imageUrl,
                                        scaleType = ImageView.ScaleType.CENTER_CROP,
                                    )
                                } else {
                                    BasicText(
                                        "♫",
                                        style = TextStyle(color = Color(0xFFA98CFF), fontSize = 20.sp, fontWeight = FontWeight.Bold),
                                    )
                                }
                            }
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                BasicText(
                                    item.name,
                                    style = TextStyle(
                                        color = when {
                                            current -> Color.White
                                            played -> Color(0xFF666D78)
                                            else -> Color(0xFFE0E5EA)
                                        },
                                        fontSize = 13.sp,
                                        fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                                    ),
                                    maxLines = 1,
                                )
                                if (!item.artist.isNullOrBlank()) {
                                    BasicText(
                                        item.artist,
                                        style = TextStyle(
                                            color = if (played) Color(0xFF555B64) else Color(0xFF89929E),
                                            fontSize = 10.sp,
                                        ),
                                        maxLines = 1,
                                    )
                                }
                            }
                            item.duration?.takeIf { it > 0 }?.let { duration ->
                                BasicText(
                                    formatPlaybackTime(duration),
                                    style = TextStyle(color = Color(0xFF727B87), fontSize = 10.sp),
                                )
                            }
                            if (!current && item.queueItemId == selectedQueueItemId) {
                                Spacer(Modifier.width(6.dp))
                                Column(
                                    modifier = Modifier
                                        .width(34.dp)
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(Color(0x553B2B59)),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    listOf(-1 to "⌃", 1 to "⌄").forEach { (shift, chevron) ->
                                        val enabled = editingId == null &&
                                            (if (shift < 0) item != upcoming.first() else item != upcoming.last())
                                        Box(
                                            modifier = Modifier
                                                .size(width = 34.dp, height = 28.dp)
                                                .clickable(enabled = enabled) {
                                                    scope.launch {
                                                        editingId = item.queueItemId
                                                        try {
                                                            if (onEditQueue(item, shift, false)) {
                                                                val oldIndex = queue.indexOf(item)
                                                                val newIndex = (oldIndex + shift).coerceIn(0, queue.lastIndex)
                                                                val reordered = queue.toMutableList()
                                                                reordered.removeAt(oldIndex)
                                                                reordered.add(newIndex, item)
                                                                queue = reordered
                                                                // Avoid reloading the entire queue on every tap.
                                                                // The server is authoritative; reload on the next queue opening.
                                                            }
                                                        } finally {
                                                            editingId = null
                                                        }
                                                    }
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            BasicText(
                                                chevron,
                                                style = TextStyle(
                                                    color = if (enabled) Color(0xFFC6ABFF) else Color(0xFF666074),
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Bold,
                                                ),
                                            )
                                        }
                                        if (shift < 0) {
                                            Box(Modifier.width(18.dp).height(1.dp).background(Color(0x334C3B69)))
                                        }
                                    }
                                }
                            }
                            if (!current) {
                                Box {
                                    BasicText(
                                        "⋮",
                                        modifier = Modifier
                                            .clickable { queueActionsItemId = item.queueItemId }
                                            .padding(horizontal = 8.dp, vertical = 7.dp),
                                        style = TextStyle(color = Color(0xFFAA9DBD), fontSize = 20.sp),
                                    )
                                    if (queueActionsItemId == item.queueItemId) {
                                        Popup(
                                            alignment = Alignment.TopEnd,
                                            onDismissRequest = { queueActionsItemId = null },
                                            properties = PopupProperties(focusable = true),
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .width(190.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(Color(0xFF252034))
                                                    .padding(8.dp),
                                            ) {
                                                BasicText(
                                                    "Play now",
                                                    modifier = Modifier.fillMaxWidth().clickable {
                                                        queueActionsItemId = null
                                                        onPlayQueueItem(item)
                                                    }.padding(12.dp),
                                                    style = TextStyle(color = Color.White, fontSize = 14.sp),
                                                )
                                                BasicText(
                                                    "Remove from queue",
                                                    modifier = Modifier.fillMaxWidth().clickable {
                                                        queueActionsItemId = null
                                                        pendingRemove = item
                                                    }.padding(12.dp),
                                                    style = TextStyle(color = Color(0xFFFFA6A6), fontSize = 14.sp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (current) {
                                Spacer(Modifier.width(8.dp))
                                BasicText(
                                    if (player.playbackState == "playing") "♫ Now Playing" else "Ⅱ Paused",
                                    style = TextStyle(color = Color(0xFFBEA5FF), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatPlaybackTime(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val seconds = safe % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

@Composable
private fun NowPlayingProgress(
    player: MaPlayer,
    onSeek: (Int) -> Unit,
) {
    val duration = player.queueDuration.coerceAtLeast(1)
    var position by remember(player.queueId) {
        mutableStateOf(
            maxOf(player.queueElapsedTime, player.queueResumePosition)
                .coerceIn(0, duration)
        )
    }

    LaunchedEffect(
        player.queueId,
        player.queueElapsedTime,
        player.queueResumePosition,
        player.playbackState,
    ) {
        position = maxOf(player.queueElapsedTime, player.queueResumePosition)
            .coerceIn(0, duration)
    }

    LaunchedEffect(player.queueId, player.playbackState, duration) {
        while (player.playbackState == "playing" && position < duration) {
            delay(1000)
            position = (position + 1).coerceAtMost(duration)
        }
    }

    val progress = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    val remaining = (duration - position).coerceAtLeast(0)

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(player.queueId, duration) {
                    detectTapGestures { offset ->
                        val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val target = (duration * fraction).toInt().coerceIn(0, duration)
                        position = target
                        onSeek(target)
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF252A35)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFA98CFF)),
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            BasicText(
                formatPlaybackTime(position),
                style = TextStyle(color = Color(0xFF8F98A5), fontSize = 10.sp),
            )
            Spacer(Modifier.weight(1f))
            BasicText(
                "-${formatPlaybackTime(remaining)}",
                style = TextStyle(color = Color(0xFF8F98A5), fontSize = 10.sp),
            )
        }
    }
}

@Composable
private fun VideoHub(
    state: MobileHomeState,
    popularity: PopularityState,
    popularityScope: PopularityScope,
    userName: String,
    userAvatarUrl: String?,
    api: ApiClient,
    onSelect: (BaseItemDto) -> Unit,
    onPlay: (BaseItemDto) -> Unit,
    onToggleFavorite: (BaseItemDto) -> Unit,
    onSwitchProfile: () -> Unit,
    onSettings: () -> Unit,
    onOpenTab: (MobileTab) -> Unit,
    expanded: Boolean,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 220.dp),
    ) {
        item {
            MobileSectionTopBar(
                title = "Video",
                subtitle = "Movies · TV · Services",
                userName = userName,
                userAvatarUrl = userAvatarUrl,
                onSwitchProfile = onSwitchProfile,
                onSettings = onSettings,
            )
        }

        item {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { HubChip("Movies") { onOpenTab(MobileTab.MOVIES) } }
                item { HubChip("TV Shows") { onOpenTab(MobileTab.TV) } }
                item { HubChip("MyV") { onOpenTab(MobileTab.MYV) } }
            }
        }

        val hero = state.continueWatching.firstOrNull()
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

        val movies = sortByPopularity(state.movies, popularity, popularityScope)
        if (movies.isNotEmpty()) item {
            MediaRow("Trending Movies", movies, api, onSelect, onToggleFavorite)
        }

        val shows = sortByPopularity(state.shows, popularity, popularityScope)
        if (shows.isNotEmpty()) item {
            MediaRow("Trending TV Shows", shows, api, onSelect, onToggleFavorite)
        }

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
                providerLogos = state.serviceLogos,
            )
        }

        if (state.collections.isNotEmpty()) item {
            MediaRow(
                title = "Curated Collections",
                media = state.collections,
                api = api,
                onSelect = onSelect,
                onToggleFavorite = onToggleFavorite,
                landscape = true,
                nameFormatter = { collectionDisplayName(it.name) },
                showFavorite = false,
            )
        }

        val myV = sortByPopularity(state.myV, popularity, popularityScope)
        if (myV.isNotEmpty()) item {
            MediaRow("MyV / Favourites", myV, api, onSelect, onToggleFavorite)
        }
    }
}

@Composable
private fun MusicHub(
    state: MusicUiState,
    configured: Boolean,
    userName: String,
    userAvatarUrl: String?,
    onRetry: () -> Unit,
    onLoadArtistAlbums: suspend (MaMediaItem) -> List<MaMediaItem>,
    onLoadAlbumDetails: suspend (MaMediaItem) -> MaAlbumDetails,
    onSearchMusicLibrary: suspend (String) -> MaSearchResults,
    onOpenMyV: () -> Unit,
    musicFavourites: List<MaMediaItem>,
    onToggleMusicFavourite: (MaMediaItem) -> Unit,
    onLoadPlaylistDetails: suspend (MaMediaItem) -> MaPlaylistDetails,
    onCreatePlaylist: suspend (String) -> Unit,
    onRenamePlaylist: suspend (MaMediaItem, String) -> Unit,
    onAddPlaylistTrack: suspend (MaMediaItem, MaMediaItem) -> Unit,
    onRemovePlaylistTrack: suspend (MaMediaItem, Int) -> Unit,
    onEnqueue: (MaMediaItem, MaPlayer, Boolean) -> Unit,
    onPlay: (MaMediaItem, List<MaPlayer>) -> Unit,
    onControl: (MaPlayer, MusicPlayerAction) -> Unit,
    onUpdateGroup: (MaPlayer, Set<String>) -> Unit,
    onOpenNowPlaying: (MaPlayer) -> Unit,
    onSwitchProfile: () -> Unit,
    onSettings: () -> Unit,
) {
    var pendingItem by remember { mutableStateOf<MaMediaItem?>(null) }
    var selectedArtist by remember { mutableStateOf<MaMediaItem?>(null) }
    var selectedAlbum by remember { mutableStateOf<MaMediaItem?>(null) }
    var selectedPlaylist by remember { mutableStateOf<MaMediaItem?>(null) }
    var createPlaylistDialog by remember { mutableStateOf(false) }
    var trackForPlaylist by remember { mutableStateOf<MaMediaItem?>(null) }
    var trackActionsItem by remember { mutableStateOf<MaMediaItem?>(null) }
    var favoriteSearchOpen by remember { mutableStateOf(false) }
    val musicListState = rememberLazyListState()
    val openMusicItem: (MaMediaItem) -> Unit = { item ->
        when (item.mediaType) {
            "artist" -> selectedArtist = item
            "album" -> selectedAlbum = item
            "playlist" -> selectedPlaylist = item
            else -> if (item.playable) pendingItem = item
        }
    }

    BackHandler(enabled = selectedArtist != null || selectedAlbum != null || selectedPlaylist != null ||
        pendingItem != null || createPlaylistDialog ||
        trackForPlaylist != null || trackActionsItem != null || favoriteSearchOpen) {
        when {
            pendingItem != null -> pendingItem = null
            favoriteSearchOpen -> favoriteSearchOpen = false
            trackForPlaylist != null -> trackForPlaylist = null
            trackActionsItem != null -> trackActionsItem = null
            createPlaylistDialog -> createPlaylistDialog = false
            selectedPlaylist != null -> selectedPlaylist = null
            selectedAlbum != null -> selectedAlbum = null
            else -> selectedArtist = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        val artist = selectedArtist
        if (artist != null) {
            MusicArtistDetail(
                artist = artist,
                onBack = { selectedArtist = null },
                onLoadAlbums = onLoadArtistAlbums,
                onAlbumSelected = { album -> selectedAlbum = album },
            )
        } else {
        LazyColumn(
            state = musicListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 220.dp),
        ) {
            item {
                MobileSectionTopBar(
                    title = "Music",
                    subtitle = "Artists · Albums · Radio · Rooms",
                    userName = userName,
                    userAvatarUrl = userAvatarUrl,
                    onSwitchProfile = onSwitchProfile,
                    onSettings = onSettings,
                )
            }

            when {
                !configured -> item {
                    MusicConnectPanel(onSettings = onSettings)
                }

                state.loading -> item {
                    MusicLoadingPanel()
                }

                state.error != null -> item {
                    MusicErrorPanel(
                        message = state.error,
                        onRetry = onRetry,
                        onSettings = onSettings,
                    )
                }

                state.loaded -> {
                    val snapshot = state.snapshot
                    item {
                        MusicHero(
                            snapshot = snapshot,
                            onPlay = openMusicItem,
                            onControl = onControl,
                            onOpenPlayer = onOpenNowPlaying,
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BasicText("MyV · Music",
                                modifier = Modifier.weight(1f).clickable(onClick = onOpenMyV)
                                    .padding(vertical = 8.dp),
                                style = TextStyle(color = Color.White,
                                    fontSize = 17.sp, fontWeight = FontWeight.Bold))
                            DarkButton("View MyV") { onOpenMyV() }
                            Spacer(Modifier.width(8.dp))
                            DarkButton("Find songs") { favoriteSearchOpen = true }
                        }
                    }

                    if (snapshot.recentlyPlayed.isNotEmpty()) item {
                        MaMediaRow(
                            title = "Recently Played",
                            items = snapshot.recentlyPlayed,
                            onClick = openMusicItem,
                            favoriteUris = musicFavourites.map { it.uri }.toSet(),
                            onToggleFavorite = onToggleMusicFavourite,
                        )
                    }

                    if (musicFavourites.isNotEmpty()) item {
                        MaMediaRow(
                            title = "Favourite Tracks · MyV",
                            items = musicFavourites,
                            onClick = { track -> if (track.playable) pendingItem = track },
                            favoriteUris = musicFavourites.map { it.uri }.toSet(),
                            onToggleFavorite = onToggleMusicFavourite,
                            onViewAll = onOpenMyV,
                        )
                    }

                    if (snapshot.albums.isNotEmpty()) item {
                        MaMediaRow(
                            title = "Albums",
                            items = snapshot.albums,
                            onClick = openMusicItem,
                        )
                    }

                    if (snapshot.artists.isNotEmpty()) item {
                        MaMediaRow(
                            title = "Artists",
                            items = snapshot.artists,
                            onClick = { artistItem -> selectedArtist = artistItem },
                            artistStyle = true,
                        )
                    }

                    if (snapshot.radios.isNotEmpty()) item {
                        MaMediaRow(
                            title = "Radio",
                            items = snapshot.radios,
                            onClick = { item ->
                                if (item.playable) pendingItem = item
                            },
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BasicText("Your Playlists", modifier = Modifier.weight(1f),
                                style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold))
                            DarkButton("+ New") { createPlaylistDialog = true }
                        }
                    }
                    if (snapshot.playlists.isNotEmpty()) item {
                        MaMediaRow(title = "Playlists", items = snapshot.playlists, onClick = openMusicItem)
                    }

                    if (snapshot.players.isNotEmpty()) item {
                        MaPlayerRow(
                            players = snapshot.players,
                            onClick = onOpenNowPlaying,
                        )
                    }
                }
            }
        }
        }

        selectedAlbum?.let { album ->
            MusicAlbumDetail(
                album = album,
                onBack = { selectedAlbum = null },
                onLoadDetails = onLoadAlbumDetails,
                onSelectPlayback = { item ->
                    if (item.playable) pendingItem = item
                },
                onOpenTrackActions = { track ->
                    if (track.mediaType == "track") trackActionsItem = track
                },
                favouriteUris = musicFavourites.map { it.uri }.toSet(),
                onToggleMusicFavourite = onToggleMusicFavourite,
            )
        }

        selectedPlaylist?.let { playlist ->
            MusicPlaylistDetail(
                playlist = playlist,
                onBack = { selectedPlaylist = null },
                onLoad = onLoadPlaylistDetails,
                onRename = onRenamePlaylist,
                onRemove = onRemovePlaylistTrack,
                onPlay = { item -> if (item.playable) pendingItem = item },
                onOpenTrackActions = { track ->
                    if (track.mediaType == "track") trackActionsItem = track
                },
                onRenamed = { selectedPlaylist = playlist.copy(name = it) },
                favouriteUris = musicFavourites.map { it.uri }.toSet(),
                onToggleMusicFavourite = onToggleMusicFavourite,
            )
        }

        if (createPlaylistDialog) {
            MusicPlaylistNameDialog(
                title = "New playlist", initial = "", actionLabel = "Create",
                onDismiss = { createPlaylistDialog = false },
                onSave = { name ->
                    onCreatePlaylist(name)
                    createPlaylistDialog = false
                },
            )
        }

        if (favoriteSearchOpen) {
            MusicFavouriteSearchPopup(
                title = "Find songs for MyV",
                initialQuery = "",
                onSearch = onSearchMusicLibrary,
                favorites = musicFavourites,
                onToggle = onToggleMusicFavourite,
                onDismiss = { favoriteSearchOpen = false },
            )
        }

        trackActionsItem?.let { track ->
            MusicTrackActionsPopup(
                track = track,
                sessions = activeMusicSessions(state.snapshot.players),
                onDismiss = { trackActionsItem = null },
                onEnqueue = { player, next ->
                    trackActionsItem = null
                    onEnqueue(track, player, next)
                },
                onSaveToPlaylist = {
                    trackActionsItem = null
                    trackForPlaylist = track
                },
                favourite = musicFavourites.any { it.uri == track.uri },
                onToggleFavourite = {
                    onToggleMusicFavourite(track)
                    trackActionsItem = null
                },
            )
        }

        trackForPlaylist?.let { track ->
            MusicPlaylistAddDialog(
                track = track,
                playlists = state.snapshot.playlists.filter {
                    it.provider == "library" && it.editable && it.itemId.toIntOrNull() != null
                },
                onDismiss = { trackForPlaylist = null },
                onAdd = { playlist -> onAddPlaylistTrack(playlist, track) },
                onSaved = { trackForPlaylist = null },
            )
        }

        pendingItem?.let { item ->
            MusicRoomPicker(
                item = item,
                players = state.snapshot.players,
                onDismiss = { pendingItem = null },
                onPlay = { selectedPlayers ->
                    pendingItem = null
                    onPlay(item, selectedPlayers)
                },
            )
        }

    }
}


@Composable
private fun MusicArtistDetail(
    artist: MaMediaItem,
    onBack: () -> Unit,
    onLoadAlbums: suspend (MaMediaItem) -> List<MaMediaItem>,
    onAlbumSelected: (MaMediaItem) -> Unit,
) {
    var albums by remember(artist.uri) { mutableStateOf<List<MaMediaItem>>(emptyList()) }
    var loading by remember(artist.uri) { mutableStateOf(true) }
    var error by remember(artist.uri) { mutableStateOf<String?>(null) }
    var retry by remember(artist.uri) { mutableStateOf(0) }

    LaunchedEffect(artist.uri, retry) {
        loading = true
        error = null
        try {
            albums = onLoadAlbums(artist)
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            error = failure.message ?: "Couldn't load this artist's albums."
        } finally {
            loading = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 220.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(Color(0x22FFFFFF))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("‹", style = TextStyle(color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold))
                }
                Spacer(Modifier.width(14.dp))
                BasicText("Artists", style = TextStyle(color = Color(0xFF9CA4B0), fontSize = 15.sp))
            }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(184.dp)
                        .clip(RoundedCornerShape(92.dp))
                        .background(Color(0xFF151A2A))
                        .border(1.dp, Color(0x665B47D8), RoundedCornerShape(92.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!artist.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            modifier = Modifier.fillMaxSize(),
                            url = artist.imageUrl,
                            scaleType = ImageView.ScaleType.CENTER_CROP,
                        )
                    } else {
                        BasicText(
                            artist.name.take(1).uppercase(),
                            style = TextStyle(color = Color(0xFFA98CFF), fontSize = 64.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                BasicText(
                    artist.name,
                    style = TextStyle(color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold),
                    maxLines = 2,
                )
                Spacer(Modifier.height(5.dp))
                BasicText("ARTIST", style = TextStyle(color = Color(0xFFA98CFF), fontSize = 10.sp, fontWeight = FontWeight.Bold))
            }
        }
        item {
            when {
                loading -> BasicText(
                    "Loading albums…",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                    style = TextStyle(color = Color(0xFF9CA4B0), fontSize = 14.sp),
                )
                error != null -> Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                    BasicText(error.orEmpty(), style = TextStyle(color = Color(0xFFFFA6A6), fontSize = 13.sp))
                    Spacer(Modifier.height(10.dp))
                    DarkButton("Try again") { retry += 1 }
                }
                albums.isEmpty() -> BasicText(
                    "No albums found for this artist.",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                    style = TextStyle(color = Color(0xFF9CA4B0), fontSize = 14.sp),
                )
                else -> MaMediaRow(
                    title = "Albums",
                    items = albums,
                    onClick = onAlbumSelected,
                )
            }
        }
    }
}


@Composable
private fun MusicAlbumDetail(
    album: MaMediaItem,
    onBack: () -> Unit,
    onLoadDetails: suspend (MaMediaItem) -> MaAlbumDetails,
    onSelectPlayback: (MaMediaItem) -> Unit,
    onOpenTrackActions: (MaMediaItem) -> Unit,
    favouriteUris: Set<String>,
    onToggleMusicFavourite: (MaMediaItem) -> Unit,
) {
    var details by remember(album.uri) { mutableStateOf<MaAlbumDetails?>(null) }
    var loading by remember(album.uri) { mutableStateOf(true) }
    var error by remember(album.uri) { mutableStateOf<String?>(null) }
    var retry by remember(album.uri) { mutableStateOf(0) }

    LaunchedEffect(album.uri, retry) {
        loading = true
        error = null
        try {
            details = onLoadDetails(album)
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            error = failure.message ?: "Couldn't load this album's tracks."
        } finally {
            loading = false
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05080C)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 220.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(Color(0x22FFFFFF))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        "‹",
                        style = TextStyle(color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold),
                    )
                }
                Spacer(Modifier.width(14.dp))
                BasicText(
                    "Albums",
                    style = TextStyle(color = Color(0xFF9CA4B0), fontSize = 15.sp),
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(196.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF151A2A))
                        .border(1.dp, Color(0x665B47D8), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!album.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            modifier = Modifier.fillMaxSize(),
                            url = album.imageUrl,
                            scaleType = ImageView.ScaleType.CENTER_CROP,
                        )
                    } else {
                        BasicText(
                            "♫",
                            style = TextStyle(color = Color(0xFFA98CFF), fontSize = 64.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                }
                Spacer(Modifier.height(15.dp))
                BasicText(
                    album.name,
                    style = TextStyle(color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold),
                    maxLines = 2,
                )
                if (album.subtitle.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    BasicText(
                        album.subtitle,
                        style = TextStyle(color = Color(0xFFB6BFCE), fontSize = 14.sp),
                        maxLines = 2,
                    )
                }
                Spacer(Modifier.height(6.dp))
                val metadata = buildList {
                    add("ALBUM")
                    details?.releaseYear?.let { add(it.toString()) }
                    details?.tracks?.size?.let { add(if (it == 1) "1 track" else it.toString() + " tracks") }
                }.joinToString("  ·  ")
                BasicText(
                    metadata,
                    style = TextStyle(color = Color(0xFFA98CFF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                )
                if (album.playable) {
                    Spacer(Modifier.height(17.dp))
                    VesperButton("Play album", { onSelectPlayback(album) })
                }
            }
        }

        item {
            BasicText(
                "Tracks",
                style = TextStyle(color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }

        when {
            loading -> item {
                BasicText(
                    "Loading tracks…",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = TextStyle(color = Color(0xFF9CA4B0), fontSize = 14.sp),
                )
            }
            error != null -> item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    BasicText(
                        error.orEmpty(),
                        style = TextStyle(color = Color(0xFFFFA6A6), fontSize = 13.sp),
                    )
                    Spacer(Modifier.height(10.dp))
                    DarkButton("Try again") { retry += 1 }
                }
            }
            details?.tracks.isNullOrEmpty() -> item {
                BasicText(
                    "No tracks found for this album.",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = TextStyle(color = Color(0xFF9CA4B0), fontSize = 14.sp),
                )
            }
            else -> {
                val tracks = details?.tracks.orEmpty()
                items(tracks) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = entry.item.playable) { onSelectPlayback(entry.item) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val position = if (entry.discNumber > 1) {
                            entry.discNumber.toString() + "." + entry.trackNumber.toString()
                        } else if (entry.trackNumber > 0) {
                            entry.trackNumber.toString()
                        } else {
                            "♫"
                        }
                        BasicText(
                            position,
                            modifier = Modifier.width(40.dp),
                            style = TextStyle(color = Color(0xFF8F98A5), fontSize = 13.sp),
                        )
                        Column(Modifier.weight(1f)) {
                            BasicText(
                                entry.item.name,
                                style = TextStyle(
                                    color = if (entry.item.playable) Color.White else Color(0xFF7F8793),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                maxLines = 2,
                            )
                            if (entry.item.subtitle.isNotBlank()) {
                                BasicText(
                                    entry.item.subtitle,
                                    style = TextStyle(color = Color(0xFF8F98A5), fontSize = 11.sp),
                                    maxLines = 1,
                                )
                            }
                        }
                        if (entry.item.mediaType == "track") {
                            MyVMark(
                                favorite = entry.item.uri in favouriteUris,
                                modifier = Modifier.clickable { onToggleMusicFavourite(entry.item) }
                                    .padding(horizontal = 7.dp, vertical = 5.dp),
                            )
                            BasicText("＋", modifier = Modifier
                                .clickable { onOpenTrackActions(entry.item) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                                style = TextStyle(color = Color(0xFFA98CFF), fontSize = 20.sp))
                        }
                        entry.durationSeconds?.let { seconds ->
                            val durationText = (seconds / 60).toString() + ":" +
                                (seconds % 60).toString().padStart(2, '0')
                            BasicText(
                                durationText,
                                style = TextStyle(color = Color(0xFF8F98A5), fontSize = 12.sp),
                            )
                        }
                    }
                }
            }
        }
    }
}




@Composable
private fun MusicFavouriteSearchPopup(
    title: String,
    initialQuery: String,
    onSearch: suspend (String) -> MaSearchResults,
    favorites: List<MaMediaItem>,
    onToggle: (MaMediaItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember(title, initialQuery) { mutableStateOf(initialQuery) }
    var tracks by remember { mutableStateOf<List<MaMediaItem>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            tracks = emptyList()
            error = null
            searching = false
        } else {
            delay(350)
            searching = true
            error = null
            try {
                tracks = onSearch(trimmed).tracks
                    .filter { it.mediaType == "track" && it.uri.isNotBlank() }
                    .distinctBy { it.uri }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                tracks = emptyList()
                error = failure.message ?: "Couldn't search Music Assistant."
            } finally {
                searching = false
            }
        }
    }
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier.width(345.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF191A25))
                .border(1.dp, Color(0x554D42A6), RoundedCornerShape(22.dp))
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    title,
                    modifier = Modifier.weight(1f),
                    style = TextStyle(color = Color.White,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold),
                )
                BasicText(
                    "×",
                    modifier = Modifier.clickable(onClick = onDismiss)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    style = TextStyle(color = Color.White, fontSize = 24.sp),
                )
            }
            Spacer(Modifier.height(8.dp))
            BasicText(
                "Search your music library. Choose the actual track to save — playing isn't required.",
                style = TextStyle(color = Color(0xFF9BA5B2),
                    fontSize = 12.sp, lineHeight = 16.sp),
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF101821))
                    .padding(13.dp),
            ) {
                if (query.isEmpty()) {
                    BasicText(
                        "Song or artist…",
                        style = TextStyle(color = Color(0xFF778391), fontSize = 14.sp),
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    cursorBrush = SolidColor(Color(0xFFA98CFF)),
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                )
            }
            Spacer(Modifier.height(9.dp))
            val message = when {
                searching -> "Searching music…"
                error != null -> error.orEmpty()
                query.trim().length < 2 -> "Type at least two characters."
                tracks.isEmpty() -> "No matching tracks in Music Assistant. Try a shorter title, or index the song in your library first."
                else -> "Select a track to add or remove from MyV."
            }
            BasicText(
                message,
                style = TextStyle(
                    color = if (error != null) Color(0xFFFFA6A6) else Color(0xFF9BA5B2),
                    fontSize = 11.sp, lineHeight = 15.sp,
                ),
            )
            Spacer(Modifier.height(7.dp))
            LazyColumn(modifier = Modifier.height(300.dp)) {
                items(tracks, key = { it.uri }) { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF151A26)), contentAlignment = Alignment.Center) {
                            if (!item.imageUrl.isNullOrBlank()) {
                                AsyncImage(modifier = Modifier.fillMaxSize(),
                                    url = item.imageUrl,
                                    scaleType = ImageView.ScaleType.CENTER_CROP)
                            } else {
                                BasicText("♫", style = TextStyle(
                                    color = Color(0xFFA98CFF), fontSize = 19.sp))
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            BasicText(item.name, maxLines = 2,
                                style = TextStyle(color = Color.White,
                                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                            BasicText(item.subtitle, maxLines = 1,
                                style = TextStyle(color = Color(0xFF9CA4B0), fontSize = 10.sp))
                        }
                        MyVMark(
                            favorite = favorites.any { it.uri == item.uri },
                            modifier = Modifier.clickable { onToggle(item) }
                                .padding(horizontal = 7.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicTrackActionsPopup(
    track: MaMediaItem,
    sessions: List<MaPlayer>,
    onDismiss: () -> Unit,
    onEnqueue: (MaPlayer, Boolean) -> Unit,
    onSaveToPlaylist: () -> Unit,
    favourite: Boolean,
    onToggleFavourite: () -> Unit,
) {
    // null: main menu; true: choose Play Next queue; false: choose Add to Queue destination.
    var chooseQueueForNext by remember(track.uri) { mutableStateOf<Boolean?>(null) }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier
                .width(345.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF191A25))
                .border(1.dp, Color(0x554D42A6), RoundedCornerShape(22.dp))
                .padding(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF111B2C)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!track.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            modifier = Modifier.fillMaxSize(),
                            url = track.imageUrl,
                            scaleType = ImageView.ScaleType.CENTER_CROP,
                        )
                    } else {
                        BasicText(
                            "♫",
                            style = TextStyle(color = Color(0xFFA98CFF), fontSize = 24.sp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    BasicText(
                        track.name,
                        style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        maxLines = 2,
                    )
                    if (track.subtitle.isNotBlank()) {
                        BasicText(
                            track.subtitle,
                            style = TextStyle(color = Color(0xFF9CA4B0), fontSize = 12.sp),
                            maxLines = 1,
                        )
                    }
                }
                BasicText(
                    "×",
                    modifier = Modifier.clickable(onClick = onDismiss)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    style = TextStyle(color = Color(0xFFB4BAC4), fontSize = 27.sp),
                )
            }
            Spacer(Modifier.height(15.dp))

            val mode = chooseQueueForNext
            if (mode == null) {
                val queueName = sessions.singleOrNull()?.name
                val queueAvailable = sessions.isNotEmpty()
                if (queueAvailable) {
                    BasicText(
                        if (queueName != null) "Now playing in $queueName" else
                            "${sessions.size} active music queues",
                        modifier = Modifier.padding(bottom = 8.dp),
                        style = TextStyle(color = Color(0xFFA98CFF), fontSize = 11.sp),
                        maxLines = 1,
                    )
                } else {
                    BasicText(
                        "Start music in a room to use the queue.",
                        modifier = Modifier.padding(bottom = 8.dp),
                        style = TextStyle(color = Color(0xFF9CA4B0), fontSize = 12.sp),
                    )
                }
                listOf(
                    Triple("Play Next", "Play immediately after the current track", true),
                    Triple("Add to Queue", "Play later in the current queue", false),
                ).forEach { (label, description, next) ->
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = queueAvailable) {
                                if (sessions.size == 1) onEnqueue(sessions.first(), next)
                                else chooseQueueForNext = next
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        BasicText(
                            label,
                            style = TextStyle(
                                color = if (queueAvailable) Color.White else Color(0xFF68707D),
                                fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            ),
                        )
                        BasicText(
                            description,
                            style = TextStyle(color = Color(0xFF88909C), fontSize = 11.sp),
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onToggleFavourite)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MyVMark(favorite = favourite)
                    Spacer(Modifier.width(12.dp))
                    BasicText(if (favourite) "Remove from MyV" else "Add to MyV",
                        style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                }
                Spacer(Modifier.height(5.dp))
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onSaveToPlaylist)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    BasicText(
                        "Save to Playlist",
                        style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    )
                    BasicText(
                        "Choose an existing playlist",
                        style = TextStyle(color = Color(0xFF88909C), fontSize = 11.sp),
                    )
                }
            } else {
                BasicText(
                    if (mode) "Play Next in…" else "Add to Queue in…",
                    style = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.height(220.dp)) {
                    items(sessions, key = { it.queueId }) { player ->
                        BasicText(
                            player.name,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onEnqueue(player, mode) }
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                            style = TextStyle(color = Color.White, fontSize = 15.sp),
                        )
                    }
                }
                DarkButton("Back") { chooseQueueForNext = null }
            }
        }
    }
}

@Composable
private fun MusicPlaylistNameDialog(
    title: String,
    initial: String,
    actionLabel: String,
    onDismiss: () -> Unit,
    onSave: suspend (String) -> Unit,
) {
    var name by remember(title, initial) { mutableStateOf(initial) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Popup(alignment = Alignment.Center, onDismissRequest = { if (!busy) onDismiss() },
        properties = PopupProperties(focusable = true)) {
        Column(Modifier.width(320.dp).clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF171725)).padding(20.dp)) {
            BasicText(title, style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF101821)).padding(13.dp)) {
                BasicTextField(value = name, onValueChange = { name = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    singleLine = true, cursorBrush = SolidColor(Color(0xFFA98CFF)),
                    modifier = Modifier.fillMaxWidth())
            }
            error?.let {
                BasicText(it, style = TextStyle(color = Color(0xFFFFA6A6), fontSize = 12.sp))
            }
            Spacer(Modifier.height(15.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DarkButton("Cancel") { if (!busy) onDismiss() }
                VesperButton(if (busy) "Saving…" else actionLabel, {
                    if (!busy) {
                        if (name.isBlank()) error = "Enter a playlist name."
                        else {
                            busy = true
                            scope.launch {
                                try { onSave(name.trim()) }
                                catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    error = e.message ?: "Couldn't save playlist."
                                } finally { busy = false }
                            }
                        }
                    }
                })
            }
        }
    }
}

@Composable
private fun MusicPlaylistAddDialog(
    track: MaMediaItem,
    playlists: List<MaMediaItem>,
    onDismiss: () -> Unit,
    onAdd: suspend (MaMediaItem) -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Popup(alignment = Alignment.Center, onDismissRequest = { if (!busy) onDismiss() },
        properties = PopupProperties(focusable = true)) {
        Column(Modifier.width(325.dp).clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF171725)).padding(18.dp)) {
            BasicText("Add to playlist", style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold))
            BasicText(track.name, style = TextStyle(color = Color(0xFF9CA4B0), fontSize = 12.sp), maxLines = 2)
            Spacer(Modifier.height(12.dp))
            if (playlists.isEmpty()) {
                BasicText("No editable playlists. Create one from Music first.",
                    style = TextStyle(color = Color(0xFF9CA4B0), fontSize = 13.sp))
            } else {
                LazyColumn(modifier = Modifier.height(240.dp)) {
                    items(playlists, key = { it.uri }) { playlist ->
                        BasicText(playlist.name,
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) {
                                busy = true
                                scope.launch {
                                    try { onAdd(playlist); onSaved() }
                                    catch (e: Exception) {
                                        if (e is CancellationException) throw e
                                        error = e.message ?: "Couldn't add track."
                                    } finally { busy = false }
                                }
                            }.padding(vertical = 13.dp),
                            style = TextStyle(color = Color.White, fontSize = 14.sp))
                    }
                }
            }
            error?.let { BasicText(it, style = TextStyle(color = Color(0xFFFFA6A6), fontSize = 12.sp)) }
            Spacer(Modifier.height(8.dp))
            DarkButton("Close") { if (!busy) onDismiss() }
        }
    }
}

@Composable
private fun MusicPlaylistDetail(
    playlist: MaMediaItem,
    onBack: () -> Unit,
    onLoad: suspend (MaMediaItem) -> MaPlaylistDetails,
    onRename: suspend (MaMediaItem, String) -> Unit,
    onRemove: suspend (MaMediaItem, Int) -> Unit,
    onPlay: (MaMediaItem) -> Unit,
    onOpenTrackActions: (MaMediaItem) -> Unit,
    onRenamed: (String) -> Unit,
    favouriteUris: Set<String>,
    onToggleMusicFavourite: (MaMediaItem) -> Unit,
) {
    var details by remember(playlist.uri) { mutableStateOf<MaPlaylistDetails?>(null) }
    var loading by remember(playlist.uri) { mutableStateOf(true) }
    var error by remember(playlist.uri) { mutableStateOf<String?>(null) }
    var retry by remember(playlist.uri) { mutableStateOf(0) }
    var rename by remember(playlist.uri) { mutableStateOf(false) }
    var remove by remember(playlist.uri) { mutableStateOf<MaPlaylistTrack?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(playlist.uri, retry) {
        loading = true; error = null
        try { details = onLoad(playlist) }
        catch (e: Exception) {
            if (e is CancellationException) throw e
            error = e.message ?: "Couldn't load playlist."
        } finally { loading = false }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFF05080C)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 220.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(21.dp))
                    .background(Color(0x22FFFFFF)).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center) {
                    BasicText("‹", style = TextStyle(color = Color.White, fontSize = 30.sp))
                }
                Spacer(Modifier.width(14.dp))
                BasicText("Playlists", style = TextStyle(color = Color(0xFF9CA4B0), fontSize = 15.sp))
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(174.dp).clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFF181632)), contentAlignment = Alignment.Center) {
                    if (!playlist.imageUrl.isNullOrBlank())
                        AsyncImage(modifier = Modifier.fillMaxSize(), url = playlist.imageUrl,
                            scaleType = ImageView.ScaleType.CENTER_CROP)
                    else VesperPlaylistArtwork(playlist.name)
                }
                Spacer(Modifier.height(15.dp))
                BasicText(details?.title ?: playlist.name,
                    style = TextStyle(color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold),
                    maxLines = 2)
                Spacer(Modifier.height(5.dp))
                BasicText(details?.tracks?.size?.let { "$it tracks" } ?: "PLAYLIST",
                    style = TextStyle(color = Color(0xFFA98CFF), fontSize = 12.sp))
                Spacer(Modifier.height(13.dp))
                if (playlist.playable) VesperButton("Play playlist", { onPlay(playlist) })
                if (details?.editable == true) {
                    Spacer(Modifier.height(10.dp))
                    DarkButton("Rename") { rename = true }
                }
            }
        }
        item {
            BasicText("Tracks", modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                style = TextStyle(color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold))
        }
        when {
            loading -> item { BasicText("Loading…", modifier = Modifier.padding(20.dp),
                style = TextStyle(color = Color(0xFF9CA4B0), fontSize = 13.sp)) }
            error != null -> item {
                Column(Modifier.padding(20.dp)) {
                    BasicText(error.orEmpty(), style = TextStyle(color = Color(0xFFFFA6A6), fontSize = 13.sp))
                    DarkButton("Retry") { retry++ }
                }
            }
            details?.tracks.isNullOrEmpty() -> item {
                BasicText("This playlist is empty.", modifier = Modifier.padding(20.dp),
                    style = TextStyle(color = Color(0xFF9CA4B0), fontSize = 13.sp))
            }
            else -> items(details?.tracks.orEmpty(), key = { "${it.position}:${it.item.uri}" }) { entry ->
                Row(Modifier.fillMaxWidth()
                    .clickable(enabled = entry.item.playable) { onPlay(entry.item) }
                    .padding(horizontal = 20.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    BasicText(entry.position.toString(), modifier = Modifier.width(34.dp),
                        style = TextStyle(color = Color(0xFF8F98A5), fontSize = 13.sp))
                    Column(Modifier.weight(1f)) {
                        BasicText(entry.item.name, maxLines = 2,
                            style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                        if (entry.item.subtitle.isNotBlank()) {
                            BasicText(entry.item.subtitle, maxLines = 1,
                                style = TextStyle(color = Color(0xFF8F98A5), fontSize = 11.sp))
                        }
                    }
                    if (entry.item.mediaType == "track") {
                        MyVMark(
                            favorite = entry.item.uri in favouriteUris,
                            modifier = Modifier.clickable { onToggleMusicFavourite(entry.item) }
                                .padding(horizontal = 7.dp, vertical = 5.dp),
                        )
                        BasicText("⋮", modifier = Modifier
                            .clickable { onOpenTrackActions(entry.item) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                            style = TextStyle(color = Color(0xFFA98CFF), fontSize = 22.sp))
                    }
                    if (details?.editable == true) {
                        BasicText("×", modifier = Modifier
                            .clickable(enabled = !saving) { remove = entry }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                            style = TextStyle(color = Color(0xFFA98CFF), fontSize = 20.sp))
                    }
                }
            }
        }
    }
    if (rename) MusicPlaylistNameDialog(
        title = "Rename playlist", initial = details?.title ?: playlist.name, actionLabel = "Rename",
        onDismiss = { rename = false },
        onSave = { name -> onRename(playlist, name); onRenamed(name); rename = false },
    )
    remove?.let { candidate ->
        Popup(alignment = Alignment.Center, onDismissRequest = { if (!saving) remove = null },
            properties = PopupProperties(focusable = true)) {
            Column(Modifier.width(310.dp).clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF171725)).padding(18.dp)) {
                BasicText("Remove from playlist?", style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(8.dp))
                BasicText(candidate.item.name,
                    style = TextStyle(color = Color(0xFF9CA4B0), fontSize = 13.sp))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DarkButton("Cancel") { if (!saving) remove = null }
                    VesperButton(if (saving) "Removing…" else "Remove", {
                        if (!saving) {
                            saving = true
                            scope.launch {
                                try {
                                    onRemove(playlist, candidate.position)
                                    remove = null
                                    delay(950)
                                    retry++
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    error = e.message ?: "Couldn't remove track."
                                    remove = null
                                } finally { saving = false }
                            }
                        }
                    })
                }
            }
        }
    }
}

@Composable
private fun MusicConnectPanel(
    onSettings: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF1A1631),
                        Color(0xFF10182B),
                        Color(0xFF07121D),
                    )
                )
            )
            .border(1.dp, Color(0x554D42A6), RoundedCornerShape(24.dp))
            .padding(22.dp),
    ) {
        Column {
            BasicText(
                "MUSIC",
                style = TextStyle(
                    color = Color(0xFFA98CFF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                ),
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                "Connect your music",
                style = TextStyle(color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(7.dp))
            BasicText(
                "Connect your music server for your library, rooms, queues and playback.",
                style = TextStyle(color = Color(0xFFA7B1BD), fontSize = 13.sp, lineHeight = 18.sp),
            )
            Spacer(Modifier.height(16.dp))
            VesperButton("Music Settings", onSettings)
        }
    }
}

@Composable
private fun MusicLoadingPanel() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF111722))
            .border(1.dp, Color(0x334D42A6), RoundedCornerShape(24.dp))
            .padding(24.dp),
    ) {
        Column {
            BasicText(
                "Loading your music…",
                style = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(6.dp))
            BasicText(
                "Artists, albums, radio and rooms are loading.",
                style = TextStyle(color = Color(0xFF8C96A5), fontSize = 13.sp),
            )
        }
    }
}

@Composable
private fun MusicErrorPanel(
    message: String,
    onRetry: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF17131D))
            .border(1.dp, Color(0x554D3348), RoundedCornerShape(24.dp))
            .padding(22.dp),
    ) {
        BasicText(
            "Music needs attention",
            style = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(7.dp))
        BasicText(
            message,
            style = TextStyle(color = Color(0xFFB8AAB5), fontSize = 13.sp, lineHeight = 18.sp),
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VesperButton("Try again", onRetry)
            DarkButton("Settings", onSettings)
        }
    }
}

@Composable
private fun MusicHero(
    snapshot: MusicAssistantSnapshot,
    onPlay: (MaMediaItem) -> Unit,
    onControl: (MaPlayer, MusicPlayerAction) -> Unit,
    onOpenPlayer: (MaPlayer) -> Unit,
) {
    val active = activeMusicSessions(snapshot.players).firstOrNull()
    val recent = snapshot.recentlyPlayed.firstOrNull()
    val activeRoomCount = active?.let {
        if (it.type == "group") it.groupMembers.distinct().size
        else (it.groupMembers + it.playerId).distinct().size
    } ?: 0
    val activeRoomLabel = when {
        active == null -> null
        activeRoomCount > 1 -> "$activeRoomCount rooms"
        else -> active.name
    }

    val imageUrl = active?.currentImageUrl ?: recent?.imageUrl
    val eyebrow = if (active != null) "NOW PLAYING" else "YOUR MUSIC"
    val title = active?.currentTitle ?: recent?.name ?: "A deeper listen"
    val subtitle = when {
        active != null -> listOfNotNull(
            active.currentArtist,
            activeRoomLabel,
            active.volumeLevel?.let { "$it%" },
        ).joinToString("  ·  ")
        recent != null -> recent.subtitle.ifBlank { "Pick up where you left off" }
        else -> "Music Assistant is connected and ready."
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF1B1731),
                        Color(0xFF10192B),
                        Color(0xFF08111C),
                    )
                )
            )
            .border(1.dp, Color(0x554D42A6), RoundedCornerShape(24.dp))
            .clickable(enabled = active != null) {
                active?.let(onOpenPlayer)
            },
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxWidth(.48f)
                    .height(220.dp),
                url = imageUrl,
                scaleType = ImageView.ScaleType.CENTER_CROP,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxWidth(.58f)
                    .height(220.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF101624),
                                Color(0xA0101624),
                                Color.Transparent,
                            )
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 24.dp)
                    .size(112.dp)
                    .clip(RoundedCornerShape(38.dp))
                    .background(Color(0x242F6BFF)),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    "♫",
                    style = TextStyle(color = Color(0xFF9D82FF), fontSize = 56.sp, fontWeight = FontWeight.Bold),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(.64f)
                .padding(start = 22.dp),
        ) {
            BasicText(
                eyebrow,
                style = TextStyle(
                    color = Color(0xFFA98CFF),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                ),
            )
            Spacer(Modifier.height(7.dp))
            BasicText(
                title,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 30.sp,
                ),
                maxLines = 2,
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(7.dp))
                BasicText(
                    subtitle,
                    style = TextStyle(color = Color(0xFFA7B1BD), fontSize = 12.sp, lineHeight = 16.sp),
                    maxLines = 2,
                )
            }
            if (active != null) {
                Spacer(Modifier.height(13.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MusicCircleButton(
                        label = "⏮",
                        enabled = active.canPrevious,
                    ) { onControl(active, MusicPlayerAction.PREVIOUS) }
                    MusicCircleButton(
                        label = if (active.playbackState == "playing") "Ⅱ" else "▶",
                        prominent = true,
                    ) { onControl(active, MusicPlayerAction.PLAY_PAUSE) }
                    MusicCircleButton(
                        label = "⏭",
                        enabled = active.canNext,
                    ) { onControl(active, MusicPlayerAction.NEXT) }
                    DarkButton(activeRoomLabel ?: active.name) { onOpenPlayer(active) }
                }
            } else if (recent != null && recent.playable) {
                Spacer(Modifier.height(13.dp))
                VesperButton("Play somewhere", { onPlay(recent) })
            }
        }
    }
}

@Composable
private fun MaMediaRow(
    title: String,
    items: List<MaMediaItem>,
    onClick: (MaMediaItem) -> Unit,
    artistStyle: Boolean = false,
    favoriteUris: Set<String> = emptySet(),
    onToggleFavorite: ((MaMediaItem) -> Unit)? = null,
    onViewAll: (() -> Unit)? = null,
) {
    Column(Modifier.padding(top = 10.dp)) {
        BasicText(
            "$title  ›",
            style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier
                .then(if (onViewAll != null) Modifier.clickable(onClick = onViewAll) else Modifier)
                .padding(horizontal = 20.dp, vertical = 7.dp),
        )

        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            items(items, key = { it.uri }) { item ->
                Column(
                    modifier = Modifier
                        .width(132.dp)
                        .clickable { onClick(item) },
                ) {
                    Box(
                        modifier = Modifier
                            .size(132.dp)
                            .clip(
                                if (artistStyle) RoundedCornerShape(66.dp)
                                else RoundedCornerShape(18.dp)
                            )
                            .background(Color(0xFF121A26))
                            .border(
                                1.dp,
                                Color(0x333D4F73),
                                if (artistStyle) RoundedCornerShape(66.dp)
                                else RoundedCornerShape(18.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (item.mediaType == "playlist") {
                            VesperPlaylistArtwork(item.name)
                        } else if (!item.imageUrl.isNullOrBlank()) {
                            AsyncImage(
                                modifier = Modifier.fillMaxSize(),
                                url = item.imageUrl,
                                scaleType = ImageView.ScaleType.CENTER_CROP,
                            )
                        } else {
                            BasicText(
                                item.name.take(1).uppercase(),
                                style = TextStyle(
                                    color = Color(0xFFA98CFF),
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                        if (item.mediaType == "track" && onToggleFavorite != null) {
                            Box(
                                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xE5141724))
                                    .clickable { onToggleFavorite(item) }
                                    .padding(5.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                MyVMark(favorite = item.uri in favoriteUris)
                            }
                        }
                    }

                    Spacer(Modifier.height(7.dp))
                    BasicText(
                        item.name,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 15.sp,
                        ),
                        maxLines = 2,
                    )
                    if (item.mediaType != "playlist" && item.subtitle.isNotBlank()) {
                        BasicText(
                            item.subtitle,
                            style = TextStyle(color = Color(0xFF7F8793), fontSize = 10.sp),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VesperPlaylistArtwork(
    name: String,
) {
    val lower = name.lowercase()
    val (glyph, label, colors) = when {
        "favour" in lower || "favor" in lower -> Triple(
            "♥",
            "FAVOURITES",
            listOf(Color(0xFF4B2468), Color(0xFF7D315D), Color(0xFF17172A)),
        )
        "recently added" in lower -> Triple(
            "+",
            "RECENTLY ADDED",
            listOf(Color(0xFF123D49), Color(0xFF245B68), Color(0xFF11182A)),
        )
        "recently played" in lower -> Triple(
            "↺",
            "RECENTLY PLAYED",
            listOf(Color(0xFF553522), Color(0xFF593A69), Color(0xFF17162A)),
        )
        "infinite" in lower -> Triple(
            "∞",
            "INFINITE MIX",
            listOf(Color(0xFF402759), Color(0xFF273E70), Color(0xFF101728)),
        )
        "random artist" in lower -> Triple(
            "✦",
            "RANDOM ARTIST",
            listOf(Color(0xFF30245E), Color(0xFF24506A), Color(0xFF101726)),
        )
        "random" in lower -> Triple(
            "✦",
            "RANDOM MIX",
            listOf(Color(0xFF30245E), Color(0xFF3A3973), Color(0xFF101726)),
        )
        else -> Triple(
            "♫",
            "PLAYLIST",
            listOf(Color(0xFF35265C), Color(0xFF223A5A), Color(0xFF101726)),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(colors)),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(28.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x22FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                "V",
                style = TextStyle(
                    color = Color(0xFFD8CCFF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(15.dp),
        ) {
            BasicText(
                glyph,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(Modifier.height(5.dp))
            BasicText(
                label,
                style = TextStyle(
                    color = Color(0xFFCEC7E5),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MaPlayerRow(
    players: List<MaPlayer>,
    onClick: (MaPlayer) -> Unit,
) {
    Column(Modifier.padding(top = 10.dp)) {
        BasicText(
            "Around the House  ›",
            style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 7.dp),
        )

        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(players, key = { it.playerId }) { player ->
                Row(
                    modifier = Modifier
                        .width(190.dp)
                        .height(92.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xD5151B25))
                        .border(
                            1.dp,
                            if (player.playbackState in setOf("playing", "paused")) Color(0x665B47D8)
                            else Color(0x223D4F73),
                            RoundedCornerShape(18.dp),
                        )
                        .clickable { onClick(player) }
                        .padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (player.playbackState == "playing") Color(0x443D2D9B)
                                else Color(0x222F6BFF)
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            if (player.playbackState == "playing") "♫" else "◉",
                            style = TextStyle(color = Color(0xFFA98CFF), fontSize = 20.sp, fontWeight = FontWeight.Bold),
                        )
                    }

                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        BasicText(
                            player.name,
                            style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(4.dp))
                        BasicText(
                            when {
                                player.playbackState == "playing" && player.groupMembers.size > 1 ->
                                    "Playing · ${player.groupMembers.distinct().size} rooms"
                                player.playbackState == "playing" && !player.currentTitle.isNullOrBlank() ->
                                    "Playing · ${player.currentTitle}"
                                player.playbackState == "paused" -> "Paused"
                                else -> "Ready"
                            },
                            style = TextStyle(color = Color(0xFF818A98), fontSize = 10.sp),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private fun musicPlayersCompatible(
    primary: MaPlayer,
    candidate: MaPlayer,
): Boolean {
    if (primary.playerId == candidate.playerId) return true

    // Music Assistant can expose grouping compatibility either as explicit
    // player ids OR as a provider instance id meaning "all players from this
    // provider". Honour both forms.
    return candidate.playerId in primary.canGroupWith ||
        candidate.provider in primary.canGroupWith ||
        primary.playerId in candidate.canGroupWith ||
        primary.provider in candidate.canGroupWith
}

@Composable
private fun MusicRoomPicker(
    item: MaMediaItem,
    players: List<MaPlayer>,
    onDismiss: () -> Unit,
    onPlay: (List<MaPlayer>) -> Unit,
) {
    val thisDevice = players.firstOrNull {
        it.type != "group" && it.name.equals("This Device", ignoreCase = true)
    }
    val rooms = players.filter {
        it.type != "group" && it.playerId != thisDevice?.playerId
    }
    val groups = players.filter { it.type == "group" }
    val selectableRooms = listOfNotNull(thisDevice) + rooms
    var selectedIds by remember(item.uri) { mutableStateOf(setOf<String>()) }

    val selectedRooms = selectableRooms.filter { it.playerId in selectedIds }
    val primary = selectedRooms.firstOrNull()

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier
                .width(350.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFA151722))
                .border(1.dp, Color(0x665B47D8), RoundedCornerShape(26.dp))
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    BasicText(
                        "PLAY ON…",
                        style = TextStyle(
                            color = Color(0xFFA98CFF),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.8.sp,
                        ),
                    )
                    Spacer(Modifier.height(4.dp))
                    BasicText(
                        item.name,
                        style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(3.dp))
                    BasicText(
                        "Choose this device, a room, or build a temporary group.",
                        style = TextStyle(color = Color(0xFF858E9B), fontSize = 10.sp),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x22FFFFFF))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("×", style = TextStyle(color = Color.White, fontSize = 24.sp))
                }
            }

            Spacer(Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.height(430.dp),
            ) {
                thisDevice?.let { localPlayer ->
                    item {
                        BasicText(
                            "THIS DEVICE",
                            style = TextStyle(
                                color = Color(0xFF777D92),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.4.sp,
                            ),
                            modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 5.dp),
                        )
                    }
                    item(key = "this-device-${localPlayer.playerId}") {
                        val selected = localPlayer.playerId in selectedIds
                        val compatible = primary == null ||
                            selected ||
                            musicPlayersCompatible(primary, localPlayer)

                        MusicDestinationRow(
                            player = localPlayer,
                            selected = selected,
                            enabled = compatible,
                            trailing = if (selected) "✓" else "",
                            onClick = {
                                selectedIds = if (selected) {
                                    selectedIds - localPlayer.playerId
                                } else {
                                    selectedIds + localPlayer.playerId
                                }
                            },
                        )
                    }
                }

                if (rooms.isNotEmpty()) {
                    item {
                        BasicText(
                            "ROOMS",
                            style = TextStyle(
                                color = Color(0xFF777D92),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.4.sp,
                            ),
                            modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 5.dp),
                        )
                    }

                    items(rooms, key = { "room-${it.playerId}" }) { room ->
                        val selected = room.playerId in selectedIds
                        val compatible = primary == null ||
                            selected ||
                            musicPlayersCompatible(primary, room)

                        MusicDestinationRow(
                            player = room,
                            selected = selected,
                            enabled = compatible,
                            trailing = if (selected) "✓" else "",
                            onClick = {
                                selectedIds = if (selected) {
                                    selectedIds - room.playerId
                                } else {
                                    selectedIds + room.playerId
                                }
                            },
                        )
                    }
                }

                if (groups.isNotEmpty()) {
                    item {
                        BasicText(
                            "GROUPS",
                            style = TextStyle(
                                color = Color(0xFF777D92),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.4.sp,
                            ),
                            modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 5.dp),
                        )
                    }

                    items(groups, key = { "group-${it.playerId}" }) { group ->
                        MusicDestinationRow(
                            player = group,
                            selected = false,
                            enabled = true,
                            trailing = "›",
                            onClick = { onPlay(listOf(group)) },
                        )
                    }
                }
            }

            if (selectedRooms.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                VesperButton(
                    label = if (selectedRooms.size == 1) {
                        "Play on ${selectedRooms.first().name}"
                    } else {
                        "Play on ${selectedRooms.size} outputs"
                    },
                    onClick = { onPlay(selectedRooms) },
                )
            }
        }
    }
}

@Composable
private fun MusicDestinationRow(
    player: MaPlayer,
    selected: Boolean,
    enabled: Boolean,
    trailing: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    selected -> Color(0x332F246C)
                    !enabled -> Color(0x2210131A)
                    else -> Color.Transparent
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(
                    if (selected) Color(0x553D2D9B)
                    else Color(0x332F6BFF)
                ),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                if (player.type == "group") "◉" else "♫",
                style = TextStyle(
                    color = if (enabled) Color(0xFFA98CFF) else Color(0xFF555D69),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            BasicText(
                player.name,
                style = TextStyle(
                    color = if (enabled) Color.White else Color(0xFF666E7A),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
            )
            BasicText(
                when {
                    !enabled -> "Can't join this combination"
                    player.type == "group" -> "Ready-made group"
                    player.playbackState == "playing" && !player.currentTitle.isNullOrBlank() ->
                        "Playing ${player.currentTitle}"
                    player.playbackState == "paused" -> "Paused"
                    else -> "Ready"
                },
                style = TextStyle(
                    color = if (enabled) Color(0xFF818A98) else Color(0xFF555D69),
                    fontSize = 10.sp,
                ),
                maxLines = 1,
            )
        }
        if (trailing.isNotBlank()) {
            BasicText(
                trailing,
                style = TextStyle(
                    color = if (selected) Color(0xFFC8B7FF) else Color(0xFF8D91A0),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

@Composable
private fun MusicCircleButton(
    label: String,
    prominent: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val buttonSize = if (compact) 30.dp else 38.dp
    val buttonRadius = if (compact) 15.dp else 19.dp

    Box(
        modifier = Modifier
            .size(buttonSize)
            .clip(RoundedCornerShape(buttonRadius))
            .background(
                if (prominent) Color(0xFFEAF4FB)
                else Color(0x44131520)
            )
            .border(
                1.dp,
                if (prominent) Color(0x33FFFFFF)
                else Color(0x334D4A75),
                RoundedCornerShape(buttonRadius),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label,
            style = TextStyle(
                color = when {
                    !enabled -> Color(0xFF555C69)
                    prominent -> Color(0xFF101820)
                    else -> Color.White
                },
                fontSize = when {
                    compact && label == "Ⅱ" -> 13.sp
                    compact && label in setOf("⏮", "⏭") -> 14.sp
                    compact -> 17.sp
                    label == "Ⅱ" -> 15.sp
                    label in setOf("⏮", "⏭") -> 16.sp
                    else -> 20.sp
                },
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun MusicPlayerControlPopup(
    player: MaPlayer,
    allPlayers: List<MaPlayer>,
    onDismiss: () -> Unit,
    onControl: (MusicPlayerAction) -> Unit,
    onManageRooms: () -> Unit,
) {
    val memberIds = if (player.type == "group") {
        player.groupMembers.distinct()
    } else {
        (player.groupMembers + player.playerId).distinct()
    }
    val memberNames = memberIds.mapNotNull { memberId ->
        allPlayers.firstOrNull { it.playerId == memberId }?.name
    }.distinct()
    val roomSummary = when {
        memberNames.size > 1 -> "${memberNames.size} rooms · ${memberNames.joinToString(", ")}"
        memberNames.size == 1 -> "${memberNames.first()} only"
        else -> player.name
    }
    val queueSummary = when {
        player.currentMediaType == "radio" -> "Live radio"
        player.queueItemCount <= 1 -> "Single item"
        player.queueCurrentIndex != null ->
            "Track ${player.queueCurrentIndex + 1} of ${player.queueItemCount}"
        else -> "${player.queueItemCount} queued"
    }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier
                .width(350.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFA151722))
                .border(1.dp, Color(0x665B47D8), RoundedCornerShape(28.dp))
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    BasicText(
                        "ROOM PLAYBACK",
                        style = TextStyle(
                            color = Color(0xFFA98CFF),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.8.sp,
                        ),
                    )
                    Spacer(Modifier.height(4.dp))
                    BasicText(
                        player.name,
                        style = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x22FFFFFF))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("×", style = TextStyle(color = Color.White, fontSize = 24.sp))
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF111B2C)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!player.currentImageUrl.isNullOrBlank()) {
                        AsyncImage(
                            modifier = Modifier.fillMaxSize(),
                            url = player.currentImageUrl,
                            scaleType = ImageView.ScaleType.CENTER_CROP,
                        )
                    } else {
                        BasicText(
                            "♫",
                            style = TextStyle(color = Color(0xFFA98CFF), fontSize = 34.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                }

                Spacer(Modifier.width(14.dp))

                Column(Modifier.weight(1f)) {
                    BasicText(
                        player.currentTitle ?: "Nothing playing",
                        style = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                        maxLines = 2,
                    )
                    if (!player.currentArtist.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        BasicText(
                            player.currentArtist,
                            style = TextStyle(color = Color(0xFF9CA4B0), fontSize = 12.sp),
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    BasicText(
                        when (player.playbackState) {
                            "playing" -> "Playing · $queueSummary"
                            "paused" -> "Paused · $queueSummary"
                            else -> queueSummary
                        },
                        style = TextStyle(color = Color(0xFFA98CFF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MusicCircleButton(
                    label = "⏮",
                    enabled = player.canPrevious,
                ) { onControl(MusicPlayerAction.PREVIOUS) }
                Spacer(Modifier.width(14.dp))
                MusicCircleButton(
                    label = if (player.playbackState == "playing") "Ⅱ" else "▶",
                    prominent = true,
                ) { onControl(MusicPlayerAction.PLAY_PAUSE) }
                Spacer(Modifier.width(14.dp))
                MusicCircleButton(
                    label = "⏭",
                    enabled = player.canNext,
                ) { onControl(MusicPlayerAction.NEXT) }
            }

            if (player.volumeLevel != null) {
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0x66111620))
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        "Volume",
                        style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.weight(1f),
                    )
                    MusicCircleButton("−") { onControl(MusicPlayerAction.VOLUME_DOWN) }
                    Spacer(Modifier.width(10.dp))
                    BasicText(
                        "${player.volumeLevel}%",
                        style = TextStyle(color = Color(0xFFD0C8FF), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    )
                    Spacer(Modifier.width(10.dp))
                    MusicCircleButton("+") { onControl(MusicPlayerAction.VOLUME_UP) }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0x66111620))
                    .clickable(onClick = onManageRooms)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    BasicText(
                        "Rooms",
                        style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(Modifier.height(3.dp))
                    BasicText(
                        roomSummary,
                        style = TextStyle(color = Color(0xFF8C96A5), fontSize = 10.sp),
                        maxLines = 1,
                    )
                }
                BasicText(
                    "›",
                    style = TextStyle(color = Color(0xFFA98CFF), fontSize = 22.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

@Composable
private fun MusicGroupManager(
    player: MaPlayer,
    players: List<MaPlayer>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    val roomPlayers = players.filter { it.type != "group" }
    val initialIds = if (player.type == "group") {
        player.groupMembers.toSet()
    } else {
        (player.groupMembers + player.playerId).toSet()
    }
    var selectedIds by remember(player.playerId, player.groupMembers) {
        mutableStateOf(initialIds)
    }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier
                .width(350.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFA151722))
                .border(1.dp, Color(0x665B47D8), RoundedCornerShape(28.dp))
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    BasicText(
                        "PLAYING IN",
                        style = TextStyle(
                            color = Color(0xFFA98CFF),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.8.sp,
                        ),
                    )
                    Spacer(Modifier.height(4.dp))
                    BasicText(
                        if (selectedIds.size > 1) "${selectedIds.size} rooms" else player.name,
                        style = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                    )
                    Spacer(Modifier.height(3.dp))
                    BasicText(
                        "Add or remove compatible rooms while playback continues.",
                        style = TextStyle(color = Color(0xFF858E9B), fontSize = 10.sp),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x22FFFFFF))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("×", style = TextStyle(color = Color.White, fontSize = 24.sp))
                }
            }

            Spacer(Modifier.height(10.dp))

            LazyColumn(modifier = Modifier.height(400.dp)) {
                items(roomPlayers, key = { "manage-${it.playerId}" }) { room ->
                    val selected = room.playerId in selectedIds
                    val isLeader = player.type != "group" && room.playerId == player.playerId
                    val compatible = isLeader ||
                        selected ||
                        musicPlayersCompatible(player, room)

                    MusicDestinationRow(
                        player = room,
                        selected = selected,
                        enabled = compatible,
                        trailing = when {
                            isLeader -> "●"
                            selected -> "✓"
                            else -> ""
                        },
                        onClick = {
                            if (!isLeader) {
                                selectedIds = if (selected) {
                                    selectedIds - room.playerId
                                } else {
                                    selectedIds + room.playerId
                                }
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            VesperButton(
                label = if (selectedIds.size > 1) {
                    "Update ${selectedIds.size} rooms"
                } else {
                    "Play in ${player.name} only"
                },
                onClick = { onSave(selectedIds) },
            )
        }
    }
}

@Composable
private fun BooksHub(
    state: BooksUiState,
    configured: Boolean,
    userName: String,
    userAvatarUrl: String?,
    onRetry: () -> Unit,
    onLoadDetails: suspend (String) -> VesperBookItem,
    onSwitchProfile: () -> Unit,
    onSettings: () -> Unit,
) {
    var selectedBook by remember { mutableStateOf<VesperBookItem?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 220.dp),
    ) {
        item {
            MobileSectionTopBar(
                title = "Books",
                subtitle = "Read · Listen · Resume",
                userName = userName,
                userAvatarUrl = userAvatarUrl,
                onSwitchProfile = onSwitchProfile,
                onSettings = onSettings,
            )
        }

        when {
            !configured -> {
                item {
                    BooksConnectPanel(onSettings = onSettings)
                }
            }

            state.loading && !state.loaded -> {
                item {
                    BooksStatusPanel(
                        title = "Loading your books…",
                        message = "Building your shelves, audiobooks, eBooks, authors and series.",
                    )
                }
            }

            state.error != null && !state.loaded -> {
                item {
                    BooksStatusPanel(
                        title = "Books needs attention",
                        message = state.error,
                        actionLabel = "Try again",
                        onAction = onRetry,
                        secondaryLabel = "Settings",
                        onSecondary = onSettings,
                    )
                }
            }

            else -> {
                val snapshot = state.snapshot
                val hero = snapshot.continueListening.firstOrNull()
                    ?: snapshot.continueReading.firstOrNull()
                    ?: snapshot.recentlyAdded.firstOrNull()
                    ?: snapshot.discover.firstOrNull()
                    ?: snapshot.audiobooks.firstOrNull()
                    ?: snapshot.ebooks.firstOrNull()

                if (hero != null) {
                    item {
                        BooksHero(
                            item = hero,
                            onClick = { selectedBook = hero },
                        )
                    }
                }

                item {
                    BooksModeSummary(
                        audiobookCount = snapshot.audiobooks.size,
                        ebookCount = snapshot.ebooks.size,
                    )
                }

                if (snapshot.continueListening.isNotEmpty()) {
                    item {
                        BookCoverRow(
                            title = "Continue Listening",
                            items = snapshot.continueListening,
                            onClick = { selectedBook = it },
                            showContinue = true,
                        )
                    }
                }

                if (snapshot.continueReading.isNotEmpty()) {
                    item {
                        BookCoverRow(
                            title = "Continue Reading",
                            items = snapshot.continueReading,
                            onClick = { selectedBook = it },
                            showContinue = true,
                        )
                    }
                }

                if (snapshot.recentlyAdded.isNotEmpty()) {
                    item {
                        BookCoverRow(
                            title = "Recently Added",
                            items = snapshot.recentlyAdded,
                            onClick = { selectedBook = it },
                        )
                    }
                }

                if (snapshot.audiobooks.isNotEmpty()) {
                    item {
                        BookCoverRow(
                            title = "Audiobooks",
                            items = snapshot.audiobooks,
                            onClick = { selectedBook = it },
                        )
                    }
                }

                if (snapshot.ebooks.isNotEmpty()) {
                    item {
                        BookCoverRow(
                            title = "eBooks",
                            items = snapshot.ebooks,
                            onClick = { selectedBook = it },
                        )
                    }
                }

                if (snapshot.discover.isNotEmpty()) {
                    item {
                        BookCoverRow(
                            title = "Discover",
                            items = snapshot.discover,
                            onClick = { selectedBook = it },
                        )
                    }
                }

                if (snapshot.authors.isNotEmpty()) {
                    item {
                        BookPeopleRow(
                            title = "Authors",
                            people = snapshot.authors,
                        )
                    }
                }

                if (snapshot.series.isNotEmpty()) {
                    item {
                        BookSeriesRow(
                            title = "Series",
                            series = snapshot.series,
                        )
                    }
                }

                if (
                    hero == null &&
                    snapshot.authors.isEmpty() &&
                    snapshot.series.isEmpty()
                ) {
                    item {
                        BooksStatusPanel(
                            title = "Your book library is empty",
                            message = "When books or audiobooks are added, they’ll appear here automatically.",
                        )
                    }
                }
            }
        }
    }

    selectedBook?.let { item ->
        BookDetailsPopup(
            initialItem = item,
            onDismiss = { selectedBook = null },
            onLoadDetails = onLoadDetails,
        )
    }
}

@Composable
private fun BooksConnectPanel(
    onSettings: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF20163A),
                        Color(0xFF11192C),
                        Color(0xFF07121D),
                    )
                )
            )
            .border(1.dp, Color(0x554D42A6), RoundedCornerShape(24.dp))
            .padding(22.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth(.72f)) {
            BasicText(
                "BOOKS",
                style = TextStyle(
                    color = Color(0xFFA98CFF),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                ),
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                "YOUR LIBRARY, ONE SHELF",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 30.sp,
                ),
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                "Connect your book library for audiobooks, eBooks, authors, series and synced progress.",
                style = TextStyle(color = Color(0xFFA4ADBA), fontSize = 13.sp, lineHeight = 18.sp),
            )
            Spacer(Modifier.height(16.dp))
            VesperButton("Book Settings", onSettings)
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(104.dp)
                .clip(RoundedCornerShape(34.dp))
                .background(Color(0x252F6BFF)),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                "▤",
                style = TextStyle(color = Color(0xFFA98CFF), fontSize = 50.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun BooksStatusPanel(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF121722))
            .border(1.dp, Color(0x334D4A75), RoundedCornerShape(22.dp))
            .padding(20.dp),
    ) {
        BasicText(
            title,
            style = TextStyle(color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(6.dp))
        BasicText(
            message,
            style = TextStyle(color = Color(0xFF929BA8), fontSize = 13.sp, lineHeight = 18.sp),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(15.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VesperButton(actionLabel, onAction)
                if (secondaryLabel != null && onSecondary != null) {
                    DarkButton(secondaryLabel, onSecondary)
                }
            }
        }
    }
}

@Composable
private fun BooksHero(
    item: VesperBookItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF251B45),
                        Color(0xFF11192B),
                        Color(0xFF071018),
                    )
                )
            )
            .border(1.dp, Color(0x554D42A6), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(112.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF101722)),
            contentAlignment = Alignment.Center,
        ) {
            if (!item.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    url = item.coverUrl,
                    scaleType = ImageView.ScaleType.CENTER_CROP,
                )
            } else {
                BasicText(
                    "▤",
                    style = TextStyle(color = Color(0xFFA98CFF), fontSize = 42.sp, fontWeight = FontWeight.Bold),
                )
            }
        }

        Spacer(Modifier.width(18.dp))

        Column(Modifier.weight(1f)) {
            BasicText(
                when {
                    item.progress != null -> "CONTINUE"
                    item.hasAudio && item.hasEbook -> "LISTEN OR READ"
                    item.hasAudio -> "AUDIOBOOK"
                    else -> "EBOOK"
                },
                style = TextStyle(
                    color = Color(0xFFA98CFF),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.7.sp,
                ),
            )
            Spacer(Modifier.height(7.dp))
            BasicText(
                item.title,
                style = TextStyle(color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
                maxLines = 3,
            )
            Spacer(Modifier.height(6.dp))
            BasicText(
                item.author,
                style = TextStyle(color = Color(0xFFAAB1BC), fontSize = 13.sp),
                maxLines = 2,
            )

            item.series?.let { series ->
                Spacer(Modifier.height(5.dp))
                BasicText(
                    series,
                    style = TextStyle(color = Color(0xFF7F88A0), fontSize = 11.sp),
                    maxLines = 1,
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                BookFormatChip(item.mediaKind)
                item.progress?.let { progress ->
                    BookFormatChip("${(progress * 100).toInt().coerceIn(0, 100)}%")
                }
            }
        }
    }
}

@Composable
private fun BooksModeSummary(
    audiobookCount: Int,
    ebookCount: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xD5161824))
            .border(1.dp, Color(0x334D4A75), RoundedCornerShape(18.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(
            Triple("♫", "Audiobooks", audiobookCount),
            Triple("▤", "eBooks", ebookCount),
        ).forEach { (icon, label, count) ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x1E6B55FF))
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    icon,
                    style = TextStyle(color = Color(0xFFA98CFF), fontSize = 18.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    BasicText(
                        label,
                        style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    )
                    BasicText(
                        count.toString(),
                        style = TextStyle(color = Color(0xFF858E9B), fontSize = 9.sp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BookCoverRow(
    title: String,
    items: List<VesperBookItem>,
    onClick: (VesperBookItem) -> Unit,
    showContinue: Boolean = false,
) {
    Column(Modifier.padding(top = 10.dp)) {
        BasicText(
            title,
            style = TextStyle(color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 7.dp),
        )

        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            items(items, key = { "${title}-${it.itemId}" }) { item ->
                Column(
                    modifier = Modifier
                        .width(136.dp)
                        .clickable { onClick(item) },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF111722))
                            .border(1.dp, Color(0x334D4A75), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!item.coverUrl.isNullOrBlank()) {
                            AsyncImage(
                                modifier = Modifier.fillMaxSize(),
                                url = item.coverUrl,
                                scaleType = ImageView.ScaleType.CENTER_CROP,
                            )
                        } else {
                            BasicText(
                                "▤",
                                style = TextStyle(color = Color(0xFFA98CFF), fontSize = 34.sp, fontWeight = FontWeight.Bold),
                            )
                        }

                        if (showContinue) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color(0xE60B0E14))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                BasicText(
                                    "CONTINUE",
                                    style = TextStyle(color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    BasicText(
                        item.title,
                        style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        maxLines = 2,
                    )
                    Spacer(Modifier.height(2.dp))
                    BasicText(
                        item.author,
                        style = TextStyle(color = Color(0xFF848D99), fontSize = 9.sp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun BookPeopleRow(
    title: String,
    people: List<VesperBookPerson>,
) {
    Column(Modifier.padding(top = 10.dp)) {
        BasicText(
            title,
            style = TextStyle(color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 7.dp),
        )
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(people, key = { it.id }) { person ->
                Row(
                    modifier = Modifier
                        .width(176.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xD5161824))
                        .border(1.dp, Color(0x334D4A75), RoundedCornerShape(18.dp))
                        .padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0x332F6BFF)),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            person.name.take(1).uppercase(),
                            style = TextStyle(color = Color(0xFFA98CFF), fontSize = 17.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        BasicText(
                            person.name,
                            style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                            maxLines = 2,
                        )
                        BasicText(
                            if (person.count > 0) "${person.count} books" else "Author",
                            style = TextStyle(color = Color(0xFF808996), fontSize = 9.sp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookSeriesRow(
    title: String,
    series: List<VesperBookSeries>,
) {
    Column(Modifier.padding(top = 10.dp)) {
        BasicText(
            title,
            style = TextStyle(color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 7.dp),
        )
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(series, key = { it.id }) { item ->
                Column(
                    modifier = Modifier
                        .width(176.dp)
                        .height(106.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xD5251D45),
                                    Color(0xD5101721),
                                )
                            )
                        )
                        .border(1.dp, Color(0x3D6B55FF), RoundedCornerShape(18.dp))
                        .padding(14.dp),
                ) {
                    BasicText(
                        "SERIES",
                        style = TextStyle(color = Color(0xFFA98CFF), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp),
                    )
                    Spacer(Modifier.height(7.dp))
                    BasicText(
                        item.name,
                        style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        maxLines = 2,
                    )
                    Spacer(Modifier.weight(1f))
                    BasicText(
                        if (item.count > 0) "${item.count} books" else "Book series",
                        style = TextStyle(color = Color(0xFF838C99), fontSize = 9.sp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BookFormatChip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0x332F6BFF))
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        BasicText(
            label,
            style = TextStyle(color = Color(0xFFD8D0FF), fontSize = 9.sp, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun BookDetailsPopup(
    initialItem: VesperBookItem,
    onDismiss: () -> Unit,
    onLoadDetails: suspend (String) -> VesperBookItem,
) {
    var details by remember(initialItem.itemId) { mutableStateOf(initialItem) }
    var loading by remember(initialItem.itemId) { mutableStateOf(true) }
    var error by remember(initialItem.itemId) { mutableStateOf<String?>(null) }

    LaunchedEffect(initialItem.itemId) {
        loading = true
        error = null
        runCatching { onLoadDetails(initialItem.itemId) }
            .onSuccess { details = it }
            .onFailure { error = it.message ?: "Couldn't load book details." }
        loading = false
    }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF705080C))
                .padding(horizontal = 22.dp, vertical = 18.dp),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(
                            "BOOKS",
                            style = TextStyle(color = Color(0xFFA98CFF), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp),
                        )
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(21.dp))
                                .background(Color(0x22FFFFFF))
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                "×",
                                style = TextStyle(color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.SemiBold),
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }

                item {
                    Box(
                        modifier = Modifier
                            .width(190.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0xFF111722)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!details.coverUrl.isNullOrBlank()) {
                            AsyncImage(
                                modifier = Modifier.fillMaxSize(),
                                url = details.coverUrl,
                                scaleType = ImageView.ScaleType.CENTER_CROP,
                            )
                        } else {
                            BasicText(
                                "▤",
                                style = TextStyle(color = Color(0xFFA98CFF), fontSize = 48.sp, fontWeight = FontWeight.Bold),
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }

                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        BasicText(
                            details.title,
                            style = TextStyle(color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold, lineHeight = 31.sp),
                        )
                        Spacer(Modifier.height(5.dp))
                        BasicText(
                            details.author,
                            style = TextStyle(color = Color(0xFFB0B7C1), fontSize = 14.sp),
                        )

                        Spacer(Modifier.height(13.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            if (details.hasAudio) BookFormatChip("Audiobook")
                            if (details.hasEbook) BookFormatChip("eBook")
                            details.publishedYear?.let { BookFormatChip(it) }
                        }

                        details.series?.let { series ->
                            Spacer(Modifier.height(16.dp))
                            BookDetailLine("Series", series)
                        }
                        details.narrator?.let { narrator ->
                            Spacer(Modifier.height(9.dp))
                            BookDetailLine("Narrated by", narrator)
                        }
                        if (details.durationSeconds > 0) {
                            Spacer(Modifier.height(9.dp))
                            BookDetailLine("Length", formatBookDuration(details.durationSeconds))
                        }

                        details.progress?.let { progress ->
                            Spacer(Modifier.height(18.dp))
                            val percent = (progress * 100).toInt().coerceIn(0, 100)
                            BasicText(
                                "Progress  ·  $percent%",
                                style = TextStyle(color = Color(0xFFD8D0FF), fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            )
                            Spacer(Modifier.height(7.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFF262B35)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(percent / 100f)
                                        .height(5.dp)
                                        .background(Color(0xFFA98CFF)),
                                )
                            }
                        }

                        if (!details.description.isNullOrBlank()) {
                            Spacer(Modifier.height(20.dp))
                            BasicText(
                                details.description.orEmpty(),
                                style = TextStyle(color = Color(0xFFCBD1D9), fontSize = 14.sp, lineHeight = 21.sp),
                            )
                        }

                        if (loading) {
                            Spacer(Modifier.height(16.dp))
                            BasicText(
                                "Loading full details…",
                                style = TextStyle(color = Color(0xFF7F8895), fontSize = 10.sp),
                            )
                        }
                        if (!error.isNullOrBlank()) {
                            Spacer(Modifier.height(16.dp))
                            BasicText(
                                error.orEmpty(),
                                style = TextStyle(color = Color(0xFFFFA6A6), fontSize = 11.sp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookDetailLine(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        BasicText(
            label,
            style = TextStyle(color = Color(0xFF7F8895), fontSize = 11.sp),
            modifier = Modifier.width(92.dp),
        )
        BasicText(
            value,
            style = TextStyle(color = Color(0xFFE0E4EA), fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
        )
    }
}

private fun formatBookDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "< 1m"
    }
}

@Composable
private fun MobileSectionTopBar(
    title: String,
    subtitle: String,
    userName: String,
    userAvatarUrl: String?,
    onSwitchProfile: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 18.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(
                "Vesper",
                style = TextStyle(
                    color = Color(0xFFB8A0FF),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp,
                ),
            )
            Spacer(Modifier.height(7.dp))
            BasicText(
                title.uppercase() + "   ·   " + subtitle,
                style = TextStyle(
                    color = Color(0xFF8C88B7),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.3.sp,
                ),
                maxLines = 1,
            )
        }

        Spacer(Modifier.size(44.dp))
    }
}

@Composable
private fun HubChip(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xB51B2230))
            .border(1.dp, Color(0x334D65FF), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        BasicText(
            label,
            style = TextStyle(color = Color(0xFFE8EBF3), fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun PlaceholderHero(
    eyebrow: String,
    title: String,
    subtitle: String,
    action: String,
    icon: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF1A1631),
                        Color(0xFF11182A),
                        Color(0xFF08111C),
                    )
                )
            )
            .border(1.dp, Color(0x554D42A6), RoundedCornerShape(22.dp)),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp)
                .size(112.dp)
                .clip(RoundedCornerShape(38.dp))
                .background(Color(0x242F6BFF)),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                icon,
                style = TextStyle(color = Color(0xFF9D82FF), fontSize = 58.sp, fontWeight = FontWeight.Bold),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(.66f)
                .padding(start = 22.dp),
        ) {
            BasicText(
                eyebrow,
                style = TextStyle(
                    color = Color(0xFFA98CFF),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.2.sp,
                ),
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                title.uppercase(),
                style = TextStyle(
                    color = Color.White,
                    fontSize = 29.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 31.sp,
                    letterSpacing = 1.2.sp,
                ),
                maxLines = 2,
            )
            Spacer(Modifier.height(7.dp))
            BasicText(
                subtitle,
                style = TextStyle(color = Color(0xFFA7B1BD), fontSize = 12.sp, lineHeight = 16.sp),
                maxLines = 2,
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0x302F6BFF))
                    .border(1.dp, Color(0x334D65FF), RoundedCornerShape(999.dp))
                    .padding(horizontal = 13.dp, vertical = 7.dp),
            ) {
                BasicText(
                    action,
                    style = TextStyle(color = Color(0xFFC0B0FF), fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

@Composable
private fun PlaceholderTileRow(
    title: String,
    items: List<Pair<String, String>>,
) {
    Column(Modifier.padding(top = 9.dp)) {
        BasicText(
            "$title  ›",
            style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 7.dp),
        )
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items) { item ->
                Row(
                    modifier = Modifier
                        .width(164.dp)
                        .height(92.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xD51A1C29),
                                    Color(0xC5101721),
                                )
                            )
                        )
                        .border(1.dp, Color(0x334D4A75), RoundedCornerShape(18.dp))
                        .padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(Color(0x332F6BFF)),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            item.first.take(1).uppercase(),
                            style = TextStyle(color = Color(0xFFA98CFF), fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        BasicText(
                            item.first,
                            style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(4.dp))
                        BasicText(
                            item.second,
                            style = TextStyle(color = Color(0xFF818A98), fontSize = 10.sp, lineHeight = 13.sp),
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeMusicRow(
    state: MusicUiState,
    configured: Boolean,
    onOpenMusic: () -> Unit,
) {
    Column(Modifier.padding(top = 9.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenMusic)
                .padding(horizontal = 20.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                "Continue Listening",
                style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
            )
            BasicText(
                "›",
                style = TextStyle(color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold),
            )
        }

        when {
            !configured -> {
                PlaceholderTileRowContent(
                    items = listOf(
                        "Music" to "Connect Music Assistant",
                        "Radio" to "Clyde 1 and favourites",
                        "Rooms" to "Playback around the house",
                    ),
                    onClick = onOpenMusic,
                )
            }

            state.loading && !state.loaded -> {
                PlaceholderTileRowContent(
                    items = listOf(
                        "Music" to "Loading your library…",
                        "Radio" to "Loading favourites…",
                        "Rooms" to "Finding players…",
                    ),
                    onClick = onOpenMusic,
                )
            }

            state.loaded -> {
                val snapshot = state.snapshot
                val active = snapshot.players.firstOrNull {
                    it.playbackState in setOf("playing", "paused") &&
                        !it.currentTitle.isNullOrBlank() &&
                        it.syncedTo == null
                } ?: snapshot.players.firstOrNull {
                    it.playbackState in setOf("playing", "paused") &&
                        !it.currentTitle.isNullOrBlank()
                }

                val recent = snapshot.recentlyPlayed.take(8)

                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (active != null) {
                        item(key = "home-now-playing-${active.playerId}") {
                            HomeNowPlayingMusicCard(
                                player = active,
                                onClick = onOpenMusic,
                            )
                        }
                    }

                    items(recent, key = { "home-music-${it.uri}" }) { item ->
                        HomeRecentMusicCard(
                            item = item,
                            onClick = onOpenMusic,
                        )
                    }

                    if (active == null && recent.isEmpty()) {
                        item {
                            HomeMusicEmptyCard(onClick = onOpenMusic)
                        }
                    }
                }
            }

            else -> {
                HomeMusicEmptyCard(onClick = onOpenMusic)
            }
        }
    }
}

@Composable
private fun PlaceholderTileRowContent(
    items: List<Pair<String, String>>,
    onClick: () -> Unit,
) {
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items) { item ->
            Row(
                modifier = Modifier
                    .width(164.dp)
                    .height(92.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xD51A1C29),
                                Color(0xC5101721),
                            )
                        )
                    )
                    .border(1.dp, Color(0x334D4A75), RoundedCornerShape(18.dp))
                    .clickable(onClick = onClick)
                    .padding(13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color(0x332F6BFF)),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        item.first.take(1).uppercase(),
                        style = TextStyle(color = Color(0xFFA98CFF), fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    BasicText(
                        item.first,
                        style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(4.dp))
                    BasicText(
                        item.second,
                        style = TextStyle(color = Color(0xFF818A98), fontSize = 10.sp, lineHeight = 13.sp),
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeNowPlayingMusicCard(
    player: MaPlayer,
    onClick: () -> Unit,
) {
    val rooms = if (player.type == "group") {
        player.groupMembers.distinct().size
    } else {
        (player.groupMembers + player.playerId).distinct().size
    }

    Row(
        modifier = Modifier
            .width(220.dp)
            .height(106.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xD528214C),
                        Color(0xD5101721),
                    )
                )
            )
            .border(1.dp, Color(0x555B47D8), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Color(0xFF111B2C)),
            contentAlignment = Alignment.Center,
        ) {
            if (!player.currentImageUrl.isNullOrBlank()) {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    url = player.currentImageUrl,
                    scaleType = ImageView.ScaleType.CENTER_CROP,
                )
            } else {
                BasicText(
                    "♫",
                    style = TextStyle(color = Color(0xFFA98CFF), fontSize = 28.sp, fontWeight = FontWeight.Bold),
                )
            }
        }

        Spacer(Modifier.width(11.dp))

        Column(Modifier.weight(1f)) {
            BasicText(
                if (player.playbackState == "playing") "NOW PLAYING" else "PAUSED",
                style = TextStyle(
                    color = Color(0xFFA98CFF),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                ),
            )
            Spacer(Modifier.height(4.dp))
            BasicText(
                player.currentTitle ?: "Music",
                style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                maxLines = 2,
            )
            if (!player.currentArtist.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                BasicText(
                    player.currentArtist,
                    style = TextStyle(color = Color(0xFF9AA3AF), fontSize = 10.sp),
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(3.dp))
            BasicText(
                if (rooms > 1) "$rooms rooms" else player.name,
                style = TextStyle(color = Color(0xFF7F8793), fontSize = 9.sp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun HomeRecentMusicCard(
    item: MaMediaItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(122.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(122.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF121A26))
                .border(1.dp, Color(0x333D4F73), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (item.mediaType == "playlist") {
                VesperPlaylistArtwork(item.name)
            } else if (!item.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    url = item.imageUrl,
                    scaleType = ImageView.ScaleType.CENTER_CROP,
                )
            } else {
                BasicText(
                    item.name.take(1).uppercase(),
                    style = TextStyle(color = Color(0xFFA98CFF), fontSize = 30.sp, fontWeight = FontWeight.Bold),
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        BasicText(
            item.name,
            style = TextStyle(
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 14.sp,
            ),
            maxLines = 2,
        )
        if (item.mediaType != "playlist" && item.subtitle.isNotBlank()) {
            BasicText(
                item.subtitle,
                style = TextStyle(color = Color(0xFF7F8793), fontSize = 9.sp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun HomeMusicEmptyCard(
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 18.dp)
            .width(190.dp)
            .height(92.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xD5151B25))
            .border(1.dp, Color(0x334D4A75), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x332F6BFF)),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                "♫",
                style = TextStyle(color = Color(0xFFA98CFF), fontSize = 20.sp, fontWeight = FontWeight.Bold),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column {
            BasicText(
                "Music",
                style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(3.dp))
            BasicText(
                "Open Vesper Music",
                style = TextStyle(color = Color(0xFF818A98), fontSize = 10.sp),
            )
        }
    }
}

@Composable
private fun BookModeStrip() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xD5161824))
            .border(1.dp, Color(0x334D4A75), RoundedCornerShape(18.dp))
            .padding(5.dp),
    ) {
        listOf("▤" to "Read", "♫" to "Listen").forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (index == 0) Color(0x3A6848FF) else Color.Transparent)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                BasicText(
                    item.first,
                    style = TextStyle(
                        color = if (index == 0) Color(0xFFA98CFF) else Color(0xFF8B93A0),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                BasicText(
                    item.second,
                    style = TextStyle(
                        color = if (index == 0) Color.White else Color(0xFFB2B7C0),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        }
    }
}

@Composable
private fun MobileHome(
    state: MobileHomeState,
    musicState: MusicUiState,
    userName: String,
    userAvatarUrl: String?,
    api: ApiClient,
    onSelect: (BaseItemDto) -> Unit,
    onRetry: () -> Unit,
    onPlay: (BaseItemDto) -> Unit,
    onToggleFavorite: (BaseItemDto) -> Unit,
    onSwitchProfile: () -> Unit,
    onSettings: () -> Unit,
    onOpenTab: (MobileTab) -> Unit,
    expanded: Boolean,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 220.dp),
    ) {
        item {
            MobileTopBar(
                userName = userName,
                userAvatarUrl = userAvatarUrl,
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

            if (hero != null) {
                item {
                    VesperHero(
                        item = hero,
                        api = api,
                        expanded = expanded,
                        onPlay = onPlay,
                        onInfo = { onSelect(hero) },
                        onToggleFavorite = { onToggleFavorite(hero) },
                        eyebrow = "UP NEXT",
                    )
                }
            }

            val continueVideo = if (hero != null) state.continueWatching.drop(1) else state.continueWatching
            val continueMusic = musicState.snapshot.recentlyPlayed

            if (continueVideo.isNotEmpty() || continueMusic.isNotEmpty()) {
                item {
                    HomeContinueRow(
                        video = continueVideo.take(6),
                        music = continueMusic.take(6),
                        api = api,
                        onVideo = onSelect,
                        onMusic = { onOpenTab(MobileTab.MUSIC) },
                    )
                }
            }

            item {
                HomeQuickAccess(
                    onMyV = { onOpenTab(MobileTab.MYV) },
                    onRequest = { onOpenTab(MobileTab.SEARCH) },
                    onRooms = { onOpenTab(MobileTab.MUSIC) },
                    onSettings = onSettings,
                )
            }
        }
    }
}

private data class HomeContinueEntry(
    val video: BaseItemDto? = null,
    val music: MaMediaItem? = null,
)

@Composable
private fun HomeContinueRow(
    video: List<BaseItemDto>,
    music: List<MaMediaItem>,
    api: ApiClient,
    onVideo: (BaseItemDto) -> Unit,
    onMusic: (MaMediaItem) -> Unit,
) {
    val entries = buildList {
        val count = maxOf(video.size, music.size)
        repeat(count) { index ->
            video.getOrNull(index)?.let { add(HomeContinueEntry(video = it)) }
            music.getOrNull(index)?.let { add(HomeContinueEntry(music = it)) }
        }
    }

    Column(Modifier.padding(top = 8.dp)) {
        BasicText(
            "Continue",
            style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 7.dp),
        )

        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(
                items = entries,
                key = { entry ->
                    entry.video?.let { "video-${it.id}" }
                        ?: entry.music?.let { "music-${it.uri}" }
                        ?: "continue"
                },
            ) { entry ->
                when {
                    entry.video != null -> HomeContinueVideoCard(
                        item = entry.video,
                        api = api,
                        onClick = { onVideo(entry.video) },
                    )
                    entry.music != null -> HomeContinueMusicCard(
                        item = entry.music,
                        onClick = { onMusic(entry.music) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeContinueVideoCard(
    item: BaseItemDto,
    api: ApiClient,
    onClick: () -> Unit,
) {
    val image = item.itemBackdropImages.firstOrNull() ?: item.itemImages[ImageType.PRIMARY]

    Row(
        modifier = Modifier
            .width(224.dp)
            .height(92.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xD5151B25))
            .border(1.dp, Color(0x283D4F73), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(74.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF111A23)),
        ) {
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                url = image?.getUrl(api),
                blurHash = image?.blurHash,
                scaleType = ImageView.ScaleType.CENTER_CROP,
            )

            val runtime = item.runTimeTicks ?: 0L
            val position = item.userData?.playbackPositionTicks ?: 0L
            if (runtime > 0L && position > 0L) {
                val progress = (position.toFloat() / runtime.toFloat()).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color(0x66000000))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .background(Color(0xFF825CFF))
                    )
                }
            }
        }

        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            BasicText(
                "VIDEO",
                style = TextStyle(
                    color = Color(0xFF8E80C9),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                ),
            )
            Spacer(Modifier.height(3.dp))
            BasicText(
                item.name ?: "Untitled",
                style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                maxLines = 2,
            )
            val secondary = when (item.type) {
                BaseItemKind.EPISODE -> listOfNotNull(
                    item.seriesName,
                    item.parentIndexNumber?.let { "S$it" },
                    item.indexNumber?.let { "E$it" },
                ).joinToString(" • ")
                else -> item.productionYear?.toString().orEmpty()
            }
            if (secondary.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                BasicText(
                    secondary,
                    style = TextStyle(color = Color(0xFF818A98), fontSize = 9.sp),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun HomeContinueMusicCard(
    item: MaMediaItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .width(224.dp)
            .height(92.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xD5151B25))
            .border(1.dp, Color(0x283D4F73), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(74.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF111A23)),
            contentAlignment = Alignment.Center,
        ) {
            if (item.mediaType == "playlist") {
                VesperPlaylistArtwork(item.name)
            } else if (!item.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    url = item.imageUrl,
                    scaleType = ImageView.ScaleType.CENTER_CROP,
                )
            } else {
                BasicText(
                    "♫",
                    style = TextStyle(color = Color(0xFFA98CFF), fontSize = 28.sp, fontWeight = FontWeight.Bold),
                )
            }
        }

        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            BasicText(
                "MUSIC",
                style = TextStyle(
                    color = Color(0xFF8E80C9),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                ),
            )
            Spacer(Modifier.height(3.dp))
            BasicText(
                item.name,
                style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                maxLines = 2,
            )
            if (item.subtitle.isNotBlank() && item.mediaType != "playlist") {
                Spacer(Modifier.height(2.dp))
                BasicText(
                    item.subtitle,
                    style = TextStyle(color = Color(0xFF818A98), fontSize = 9.sp),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun HomeQuickAccess(
    onMyV: () -> Unit,
    onRequest: () -> Unit,
    onRooms: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(Modifier.padding(top = 14.dp, bottom = 12.dp)) {
        BasicText(
            "Quick Access",
            style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 7.dp),
        )

        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            item { HomeQuickAccessChip("V", "MyV", onMyV) }
            item { HomeQuickAccessChip("⌕", "Request", onRequest) }
            item { HomeQuickAccessChip("♫", "Rooms", onRooms) }
            item { HomeQuickAccessChip("⚙", "Settings", onSettings) }
        }
    }
}

@Composable
private fun HomeQuickAccessChip(
    icon: String,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(52.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(Color(0xD5151B25))
            .border(1.dp, Color(0x283D4F73), RoundedCornerShape(17.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            icon,
            style = TextStyle(color = Color(0xFFA98CFF), fontSize = 16.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.width(8.dp))
        BasicText(
            label,
            style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun MobileTopBar(
    userName: String,
    userAvatarUrl: String?,
    onSwitchProfile: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 18.dp, top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(
                "Vesper",
                style = TextStyle(
                    color = Color(0xFFB8A0FF),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp,
                ),
            )
            Spacer(Modifier.height(7.dp))
            BasicText(
                "HOME   ·   VIDEO   ·   MUSIC   ·   BOOKS",
                style = TextStyle(
                    color = Color(0xFF8C88B7),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.8.sp,
                ),
            )
        }

        Spacer(Modifier.size(44.dp))
    }
}

@Composable
private fun PersistentProfileButton(
    userName: String,
    userAvatarUrl: String?,
    onSwitchProfile: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        VesperProfileAvatar(
            name = userName,
            imageUrl = userAvatarUrl,
            modifier = Modifier
                .size(44.dp)
                .border(1.dp, Color(0x665B47D8), RoundedCornerShape(22.dp))
                .clickable { menuOpen = true },
        )

        if (menuOpen) {
            Popup(
                alignment = Alignment.TopEnd,
                onDismissRequest = { menuOpen = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    modifier = Modifier
                        .width(220.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xF21A1C2A))
                        .border(1.dp, Color(0x554C4D7B), RoundedCornerShape(18.dp))
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
    eyebrow: String? = null,
) {
    val image = item.itemBackdropImages.firstOrNull() ?: item.itemImages[ImageType.PRIMARY]
    val heroHeight = if (expanded) 330.dp else 245.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(if (expanded) 24.dp else 20.dp))
            .background(Color(0xFF111820))
            .border(1.dp, Color(0x334E4B80), RoundedCornerShape(if (expanded) 24.dp else 20.dp)),
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
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xF20A0B12),
                            Color(0xA10A0B12),
                            Color(0x220A0B12),
                            Color.Transparent,
                        )
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x1005080C),
                            Color(0xC805080C),
                        ),
                        startY = 90f,
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(if (expanded) .62f else .78f)
                .padding(start = 20.dp, end = 12.dp, bottom = 18.dp),
        ) {
            BasicText(
                eyebrow ?: if ((item.userData?.playbackPositionTicks ?: 0L) > 0L) "CONTINUE WATCHING" else "FEATURED",
                style = TextStyle(
                    color = Color(0xFFA98CFF),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.8.sp,
                ),
            )
            Spacer(Modifier.height(5.dp))
            BasicText(
                item.name ?: "Untitled",
                style = TextStyle(
                    color = Color.White,
                    fontSize = if (expanded) 35.sp else 27.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = if (expanded) 38.sp else 29.sp,
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
                Spacer(Modifier.height(6.dp))
                BasicText(
                    metadata,
                    style = TextStyle(color = Color(0xFFB7BDC9), fontSize = 11.sp),
                    maxLines = 1,
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    providerLogos: Map<String, String> = emptyMap(),
) {
    Column(Modifier.padding(top = 10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                "$title  ›",
                style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
            )
            if (!subtitle.isNullOrBlank()) {
                BasicText(
                    subtitle,
                    style = TextStyle(color = Color(0xFF7775A3), fontSize = 10.sp),
                )
            }
        }
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                    providerLogos = providerLogos,
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
    providerLogos: Map<String, String> = emptyMap(),
) {
    val w = if (landscape) 158.dp else 118.dp
    val h = if (landscape) 94.dp else 174.dp
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
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF111A23))
                .border(1.dp, Color(0x223D4F73), RoundedCornerShape(14.dp)),
        ) {
            if (providerTile) {
                ProviderWordmark(
                    name = serviceDisplayName(item.name),
                    logoUrl = serviceLogoUrl(item.name, providerLogos),
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
                        .padding(6.dp)
                        .size(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xA805080C))
                        .clickable { onToggleFavorite(item) },
                    contentAlignment = Alignment.Center,
                ) {
                    MyVMark(favorite = item.userData?.isFavorite == true)
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
                            .background(Color(0x66000000))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress)
                                .height(3.dp)
                                .background(Color(0xFF825CFF))
                        )
                    }
                }
            }
        }

        if (!providerTile) {
            Spacer(Modifier.height(6.dp))
            BasicText(
                nameFormatter(item),
                style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
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
                style = TextStyle(color = Color(0xFF7F8793), fontSize = 11.sp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ProviderWordmark(
    name: String,
    logoUrl: String?,
    logoResource: Int?,
    modifier: Modifier = Modifier,
) {
    val key = serviceKey(name)
    val displayName = serviceDisplayName(name)
    val background = when (key) {
        "netflix" -> Color(0xFF050505)
        "disney" -> Color(0xFF0B1D3A)
        "prime" -> Color(0xFF07141D)
        "apple" -> Color(0xFF050505)
        "paramount" -> Color(0xFF0064FF)
        "max" -> Color(0xFF25105D)
        "now" -> Color(0xFF09130E)
        "iplayer" -> Color(0xFF111111)
        "itvx" -> Color(0xFF16121D)
        "hulu" -> Color(0xFF0B1F17)
        "peacock" -> Color(0xFF111111)
        else -> Color(0xFF111A23)
    }

    Box(
        modifier = modifier.background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (logoResource != null) {
            val tint = when (key) {
                "prime" -> ColorFilter.tint(Color(0xFF00A8E1))
                "apple", "paramount", "max" -> ColorFilter.tint(Color.White)
                else -> null
            }

            Image(
                painter = painterResource(logoResource),
                contentDescription = displayName,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = when (key) {
                            "apple" -> 34.dp
                            "paramount" -> 28.dp
                            "max" -> 36.dp
                            else -> 26.dp
                        },
                        vertical = when (key) {
                            "apple" -> 24.dp
                            "paramount" -> 25.dp
                            "max" -> 28.dp
                            else -> 19.dp
                        },
                    ),
                contentScale = ContentScale.Fit,
                colorFilter = tint,
            )
        } else if (!logoUrl.isNullOrBlank()) {
            AsyncImage(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 20.dp),
                url = logoUrl,
                scaleType = ImageView.ScaleType.FIT_CENTER,
            )
        } else {
            BasicText(
                displayName,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold,
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
    showVideoTypeFilter: Boolean = false,
) {
    var selectedVideoType by remember(title) { mutableStateOf<BaseItemKind?>(null) }
    val genreMedia = remember(media, selectedVideoType, showVideoTypeFilter) {
        if (showVideoTypeFilter && selectedVideoType != null) {
            media.filter { it.type == selectedVideoType }
        } else media
    }
    val genres = remember(genreMedia) {
        genreMedia
            .flatMap { it.genres.orEmpty() }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    var selectedGenre by remember(title, media) { mutableStateOf<String?>(null) }
    var sort by remember(title) { mutableStateOf(LibrarySort.POPULARITY) }

    val filtered = remember(genreMedia, selectedGenre) {
        selectedGenre?.let { genre ->
            genreMedia.filter { item ->
                item.genres.orEmpty().any { it.equals(genre, ignoreCase = true) }
            }
        } ?: genreMedia
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

        if (showVideoTypeFilter && media.isNotEmpty()) {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    GenreChip("All", selectedVideoType == null) {
                        selectedVideoType = null
                        selectedGenre = null
                    }
                }
                item {
                    GenreChip("Movies", selectedVideoType == BaseItemKind.MOVIE) {
                        selectedVideoType = BaseItemKind.MOVIE
                        selectedGenre = null
                    }
                }
                item {
                    GenreChip("TV Shows", selectedVideoType == BaseItemKind.SERIES) {
                        selectedVideoType = BaseItemKind.SERIES
                        selectedGenre = null
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
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
                    bottom = 118.dp,
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

private enum class SearchScope(val label: String) {
    ALL("All"),
    VIDEO("Video"),
    MUSIC("Music"),
    BOOKS("Books"),
}

private enum class UnifiedSearchFilter(val label: String) {
    ALL("All"),
    VIDEO("Video"),
    MUSIC("Music"),
    BOOKS("Books"),
}

@Composable
private fun SearchBrowse(
    state: MobileHomeState,
    api: ApiClient,
    videoRequestsConfigured: Boolean,
    musicLibraryConfigured: Boolean,
    musicRequestsConfigured: Boolean,
    bookRequestsConfigured: Boolean,
    audiobookLibraryConfigured: Boolean,
    onLibrarySearch: suspend (String) -> List<BaseItemDto>,
    onVideoRequestSearch: suspend (String) -> List<SeerrSearchResult>,
    onVideoRequest: suspend (SeerrSearchResult) -> String?,
    onMusicLibrarySearch: suspend (String) -> MaSearchResults,
    onMusicRequestSearch: suspend (String) -> List<VesperMusicRequestResult>,
    onMusicRequest: suspend (VesperMusicRequestResult) -> String?,
    onBookLibrarySearch: suspend (String) -> List<VesperBookLibraryResult>,
    onBookRequestSearch: suspend (String) -> List<VesperBookRequestResult>,
    onBookRequest: suspend (VesperBookRequestResult, Boolean) -> String?,
    musicPlayers: List<MaPlayer>,
    onPlayMusic: (MaMediaItem, List<MaPlayer>) -> Unit,
    onEnqueueMusic: (MaMediaItem, MaPlayer, Boolean) -> Unit,
    musicFavourites: List<MaMediaItem>,
    onToggleMusicFavourite: (MaMediaItem) -> Unit,
    onSelect: (BaseItemDto) -> Unit,
    onToggleFavorite: (BaseItemDto) -> Unit,
    expanded: Boolean,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(UnifiedSearchFilter.ALL) }

    var videoLibrary by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var videoRequests by remember { mutableStateOf<List<SeerrSearchResult>>(emptyList()) }
    var videoLoading by remember { mutableStateOf(false) }
    var videoError by remember { mutableStateOf<String?>(null) }
    var videoRefresh by remember { mutableStateOf(0) }

    var musicLibrary by remember { mutableStateOf(MaSearchResults()) }
    var musicRequests by remember { mutableStateOf<List<VesperMusicRequestResult>>(emptyList()) }
    var musicLoading by remember { mutableStateOf(false) }
    var musicError by remember { mutableStateOf<String?>(null) }
    var musicRefresh by remember { mutableStateOf(0) }

    var bookLibrary by remember { mutableStateOf<List<VesperBookLibraryResult>>(emptyList()) }
    var bookRequests by remember { mutableStateOf<List<VesperBookRequestResult>>(emptyList()) }
    var booksLoading by remember { mutableStateOf(false) }
    var booksError by remember { mutableStateOf<String?>(null) }
    var booksRefresh by remember { mutableStateOf(0) }

    var selectedVideoRequest by remember { mutableStateOf<SeerrSearchResult?>(null) }
    var selectedMusicRequest by remember { mutableStateOf<VesperMusicRequestResult?>(null) }
    var selectedBookRequest by remember { mutableStateOf<VesperBookRequestResult?>(null) }
    var pendingMusicItem by remember { mutableStateOf<MaMediaItem?>(null) }
    var pendingQueueMusicItem by remember { mutableStateOf<MaMediaItem?>(null) }

    val cleanQuery = query.trim()
    val fallbackVideo = remember(state) {
        (state.continueWatching + state.myV + state.movies + state.shows)
            .distinctBy { it.id }
    }

    val visibleVideoLibrary = when {
        cleanQuery.isBlank() -> fallbackVideo
        cleanQuery.length < 2 -> fallbackVideo.filter { item ->
            listOfNotNull(
                item.name,
                item.seriesName,
                item.productionYear?.toString(),
            ).joinToString(" ").contains(cleanQuery, ignoreCase = true)
        }
        else -> videoLibrary
    }

    val localTmdbKeys = remember(visibleVideoLibrary) {
        visibleVideoLibrary.mapNotNull { item ->
            val mediaType = when (item.type) {
                BaseItemKind.MOVIE -> "movie"
                BaseItemKind.SERIES -> "tv"
                else -> null
            }
            val tmdbId = item.tmdbId()
            if (mediaType != null && tmdbId != null) "${mediaType}:${tmdbId}" else null
        }.toSet()
    }
    val visibleVideoRequests = videoRequests.filterNot {
        "${it.mediaType}:${it.tmdbId}" in localTmdbKeys
    }

    val localMusicAlbumKeys = remember(musicLibrary.albums) {
        musicLibrary.albums.map {
            "${it.name.trim().lowercase()}|${it.subtitle.trim().lowercase()}"
        }.toSet()
    }
    val visibleMusicRequests = musicRequests.filterNot {
        "${it.albumName.trim().lowercase()}|${it.artistName.trim().lowercase()}" in localMusicAlbumKeys
    }

    val localBookKeys = remember(bookLibrary) {
        bookLibrary.map {
            "${it.title.trim().lowercase()}|${it.author.trim().lowercase()}"
        }.toSet()
    }
    val visibleBookRequests = bookRequests.filterNot {
        "${it.title.trim().lowercase()}|${it.author.trim().lowercase()}" in localBookKeys
    }

    LaunchedEffect(
        query,
        videoRequestsConfigured,
        musicLibraryConfigured,
        musicRequestsConfigured,
        bookRequestsConfigured,
        audiobookLibraryConfigured,
        videoRefresh,
        musicRefresh,
        booksRefresh,
    ) {
        val requestedQuery = query.trim()
        if (requestedQuery.length < 2) {
            videoLibrary = emptyList()
            videoRequests = emptyList()
            musicLibrary = MaSearchResults()
            musicRequests = emptyList()
            bookLibrary = emptyList()
            bookRequests = emptyList()
            videoLoading = false
            musicLoading = false
            booksLoading = false
            videoError = null
            musicError = null
            booksError = null
            return@LaunchedEffect
        }

        delay(350)
        videoLoading = true
        musicLoading = musicLibraryConfigured || musicRequestsConfigured
        booksLoading = audiobookLibraryConfigured || bookRequestsConfigured
        videoError = null
        musicError = null
        booksError = null

        val videoLibraryDeferred = async { runCatching { onLibrarySearch(requestedQuery) } }
        val videoRequestDeferred = if (videoRequestsConfigured) {
            async { runCatching { onVideoRequestSearch(requestedQuery) } }
        } else null
        val musicLibraryDeferred = if (musicLibraryConfigured) {
            async { runCatching { onMusicLibrarySearch(requestedQuery) } }
        } else null
        val musicRequestDeferred = if (musicRequestsConfigured) {
            async { runCatching { onMusicRequestSearch(requestedQuery) } }
        } else null
        val bookLibraryDeferred = if (audiobookLibraryConfigured) {
            async { runCatching { onBookLibrarySearch(requestedQuery) } }
        } else null
        val bookRequestDeferred = if (bookRequestsConfigured) {
            async { runCatching { onBookRequestSearch(requestedQuery) } }
        } else null

        videoLibraryDeferred.await()
            .onSuccess { videoLibrary = it }
            .onFailure {
                videoLibrary = emptyList()
                videoError = it.message ?: "Couldn't search your video library."
            }
        if (videoRequestDeferred != null) {
            videoRequestDeferred.await()
                .onSuccess { videoRequests = it }
                .onFailure {
                    videoRequests = emptyList()
                    videoError = videoError ?: (it.message ?: "Couldn't search video requests.")
                }
        } else {
            videoRequests = emptyList()
        }
        videoLoading = false

        if (musicLibraryDeferred != null) {
            musicLibraryDeferred.await()
                .onSuccess { musicLibrary = it }
                .onFailure {
                    musicLibrary = MaSearchResults()
                    musicError = it.message ?: "Couldn't search your music library."
                }
        } else {
            musicLibrary = MaSearchResults()
        }
        if (musicRequestDeferred != null) {
            musicRequestDeferred.await()
                .onSuccess { musicRequests = it }
                .onFailure {
                    musicRequests = emptyList()
                    musicError = musicError ?: (it.message ?: "Couldn't search music requests.")
                }
        } else {
            musicRequests = emptyList()
        }
        musicLoading = false

        if (bookLibraryDeferred != null) {
            bookLibraryDeferred.await()
                .onSuccess { bookLibrary = it }
                .onFailure {
                    bookLibrary = emptyList()
                    booksError = it.message ?: "Couldn't search your audiobook library."
                }
        } else {
            bookLibrary = emptyList()
        }
        if (bookRequestDeferred != null) {
            bookRequestDeferred.await()
                .onSuccess { bookRequests = it }
                .onFailure {
                    bookRequests = emptyList()
                    booksError = booksError ?: (it.message ?: "Couldn't search book requests.")
                }
        } else {
            bookRequests = emptyList()
        }
        booksLoading = false
    }

    val showVideo = filter == UnifiedSearchFilter.ALL || filter == UnifiedSearchFilter.VIDEO
    val showMusic = filter == UnifiedSearchFilter.ALL || filter == UnifiedSearchFilter.MUSIC
    val showBooks = filter == UnifiedSearchFilter.ALL || filter == UnifiedSearchFilter.BOOKS
    val allLimit = if (filter == UnifiedSearchFilter.ALL) 6 else Int.MAX_VALUE

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
                    "Search Media",
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

        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 4.dp,
                bottom = 8.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(UnifiedSearchFilter.entries) { option ->
                GenreChip(
                    label = option.label,
                    selected = filter == option,
                    onClick = { filter = option },
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(if (expanded) 5 else 3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 220.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (cleanQuery.length < 2) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SearchSectionHeader(
                        title = "Search across Vesper",
                        subtitle = "Movies, TV, music, books and audiobooks",
                    )
                }
                if (showVideo) {
                    gridItems(visibleVideoLibrary.take(12), key = { "browse-${it.id}" }) { item ->
                        GridMediaCard(item, api, onSelect, onToggleFavorite)
                    }
                }
            } else {
                if (showVideo) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SearchSectionHeader(
                            title = "Video",
                            subtitle = when {
                                videoLoading -> "Searching…"
                                videoError != null -> videoError.orEmpty()
                                visibleVideoLibrary.isEmpty() && visibleVideoRequests.isEmpty() -> "No matches"
                                else -> "${visibleVideoLibrary.size} in your library · ${visibleVideoRequests.size} requestable"
                            },
                        )
                    }

                    gridItems(
                        visibleVideoLibrary.take(allLimit),
                        key = { "video-local-${it.id}" },
                    ) { item ->
                        GridMediaCard(item, api, onSelect, onToggleFavorite)
                    }

                    gridItems(
                        visibleVideoRequests.take(allLimit),
                        key = { "video-request-${it.mediaType}-${it.tmdbId}" },
                    ) { item ->
                        SeerrMediaCard(
                            item = item,
                            onRequest = onVideoRequest,
                            onRequested = { videoRefresh++ },
                            onOpenDetails = { selectedVideoRequest = it },
                        )
                    }
                }

                if (showMusic) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SearchSectionHeader(
                            title = "Music",
                            subtitle = when {
                                !musicLibraryConfigured && !musicRequestsConfigured ->
                                    "Connect Music and Music Requests in Settings"
                                musicLoading -> "Searching…"
                                musicError != null -> musicError.orEmpty()
                                musicLibrary.all.isEmpty() && visibleMusicRequests.isEmpty() -> "No matches"
                                else -> "${musicLibrary.all.size} in your library · ${visibleMusicRequests.size} requestable albums"
                            },
                        )
                    }

                    gridItems(
                        musicLibrary.all.take(allLimit),
                        key = { "music-local-${it.uri}" },
                    ) { item ->
                        SearchMusicLibraryCard(
                            item = item,
                            onClick = {
                                if (item.playable) pendingMusicItem = item
                            },
                            onQueue = {
                                if (item.playable) pendingQueueMusicItem = item
                            },
                            isFavorite = item.uri in musicFavourites.map { it.uri }.toSet(),
                            onToggleFavorite = if (item.mediaType == "track") {
                                { onToggleMusicFavourite(item) }
                            } else null,
                        )
                    }

                    gridItems(
                        visibleMusicRequests.take(allLimit),
                        key = { "music-request-${it.albumMbid}" },
                    ) { item ->
                        SearchMusicRequestCard(
                            item = item,
                            onOpen = { selectedMusicRequest = item },
                        )
                    }
                }

                if (showBooks) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SearchSectionHeader(
                            title = "Books & Audiobooks",
                            subtitle = when {
                                !bookRequestsConfigured && !audiobookLibraryConfigured ->
                                    "Connect Book Requests or your Audiobook Library in Settings"
                                booksLoading -> "Searching…"
                                booksError != null -> booksError.orEmpty()
                                bookLibrary.isEmpty() && visibleBookRequests.isEmpty() -> "No matches"
                                else -> "${bookLibrary.size} in your library · ${visibleBookRequests.size} requestable"
                            },
                        )
                    }

                    gridItems(
                        bookLibrary.take(allLimit),
                        key = { "book-local-${it.itemId}" },
                    ) { item ->
                        SearchBookLibraryCard(item)
                    }

                    gridItems(
                        visibleBookRequests.take(allLimit),
                        key = { "book-request-${it.bookId}" },
                    ) { item ->
                        SearchBookRequestCard(
                            item = item,
                            onOpen = { selectedBookRequest = item },
                        )
                    }
                }
            }
        }
    }

    selectedVideoRequest?.let { item ->
        MediaRequestDetailsPopup(
            item = item,
            onDismiss = { selectedVideoRequest = null },
            onRequest = onVideoRequest,
            onRequested = { videoRefresh++ },
        )
    }

    selectedMusicRequest?.let { item ->
        MusicRequestDetailsPopup(
            item = item,
            onDismiss = { selectedMusicRequest = null },
            onRequest = onMusicRequest,
            onRequested = { musicRefresh++ },
        )
    }

    selectedBookRequest?.let { item ->
        BookRequestDetailsPopup(
            item = item,
            onDismiss = { selectedBookRequest = null },
            onRequest = onBookRequest,
            onRequested = { booksRefresh++ },
        )
    }

    pendingQueueMusicItem?.let { item ->
        val target = musicPlayers.firstOrNull {
            it.name.equals("This Device", ignoreCase = true) &&
                it.playbackState in listOf("playing", "paused")
        } ?: musicPlayers.firstOrNull { it.playbackState in listOf("playing", "paused") }
        Popup(
            alignment = Alignment.Center,
            onDismissRequest = { pendingQueueMusicItem = null },
            properties = PopupProperties(focusable = true),
        ) {
            Column(
                modifier = Modifier.width(300.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF171725))
                    .padding(18.dp),
            ) {
                BasicText(item.name, style = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(8.dp))
                BasicText("Queue: ${target?.name ?: "No active player"}",
                    style = TextStyle(color = Color(0xFF9CA4B0), fontSize = 12.sp))
                Spacer(Modifier.height(12.dp))
                listOf(true to "Play Next", false to "Add to Queue").forEach { (next, label) ->
                    BasicText(label,
                        modifier = Modifier.fillMaxWidth().clickable(enabled = target != null) {
                            target?.let { onEnqueueMusic(item, it, next) }
                            pendingQueueMusicItem = null
                        }.padding(12.dp),
                        style = TextStyle(color = if (target == null) Color.Gray else Color.White, fontSize = 15.sp))
                }
                BasicText("Cancel", modifier = Modifier.clickable { pendingQueueMusicItem = null }.padding(12.dp),
                    style = TextStyle(color = Color(0xFF9CA4B0), fontSize = 13.sp))
            }
        }
    }

    pendingMusicItem?.let { item ->
        MusicRoomPicker(
            item = item,
            players = musicPlayers,
            onDismiss = { pendingMusicItem = null },
            onPlay = { selectedPlayers ->
                pendingMusicItem = null
                onPlayMusic(item, selectedPlayers)
            },
        )
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
private fun SearchMusicLibraryCard(
    item: MaMediaItem,
    onClick: () -> Unit,
    onQueue: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.playable, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF111A23))
                .border(1.dp, Color(0x333D4F73), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (!item.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    url = item.imageUrl,
                    scaleType = ImageView.ScaleType.CENTER_CROP,
                )
            } else {
                BasicText(
                    if (item.mediaType == "artist") "◎" else "♫",
                    style = TextStyle(
                        color = Color(0xFFA98CFF),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xDD171725))
                    .clickable(enabled = item.playable, onClick = onQueue),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("⋮", style = TextStyle(color = Color.White, fontSize = 22.sp))
            }

            if (item.mediaType == "track" && onToggleFavorite != null) {
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xDD171725))
                        .clickable(onClick = onToggleFavorite)
                        .padding(7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MyVMark(favorite = isFavorite)
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xDD15293B))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                BasicText(
                    "LIBRARY",
                    style = TextStyle(
                        color = Color(0xFFBDEBFF),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                    ),
                )
            }
        }

        Spacer(Modifier.height(7.dp))
        BasicText(
            item.name,
            style = TextStyle(
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 14.sp,
            ),
            maxLines = 2,
        )
        BasicText(
            item.subtitle.ifBlank { item.mediaType.replaceFirstChar { it.uppercase() } },
            style = TextStyle(color = Color(0xFF89929E), fontSize = 10.sp),
            maxLines = 1,
        )
    }
}

private fun musicRequestBadge(item: VesperMusicRequestResult): String {
    if (item.inLibrary || item.status.equals("available", ignoreCase = true)) return "AVAILABLE"
    return when (item.status.lowercase()) {
        "monitored", "searching", "queued", "requested" -> "REQUESTED"
        else -> "REQUEST"
    }
}

@Composable
private fun SearchMusicRequestCard(
    item: VesperMusicRequestResult,
    onOpen: () -> Unit,
) {
    val badge = musicRequestBadge(item)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF111A23)),
            contentAlignment = Alignment.Center,
        ) {
            if (!item.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    url = item.coverUrl,
                    scaleType = ImageView.ScaleType.CENTER_CROP,
                )
            } else {
                BasicText(
                    "♫",
                    style = TextStyle(color = Color(0xFFA98CFF), fontSize = 34.sp, fontWeight = FontWeight.Bold),
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (badge == "AVAILABLE") Color(0xDD173D2A)
                        else Color(0xDD26305C)
                    )
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                BasicText(
                    badge,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.7.sp,
                    ),
                )
            }
        }

        Spacer(Modifier.height(7.dp))
        BasicText(
            item.albumName,
            style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 14.sp),
            maxLines = 2,
        )
        BasicText(
            item.artistName,
            style = TextStyle(color = Color(0xFF89929E), fontSize = 10.sp),
            maxLines = 1,
        )
    }
}

@Composable
private fun SearchBookLibraryCard(
    item: VesperBookLibraryResult,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF1B1931),
                            Color(0xFF111927),
                            Color(0xFF0B1018),
                        )
                    )
                )
                .border(1.dp, Color(0x333D4F73), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                "▤",
                style = TextStyle(
                    color = Color(0xFFC6B8FF),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xDD173D2A))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                BasicText(
                    "AVAILABLE",
                    style = TextStyle(color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        BasicText(
            item.title,
            style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 14.sp),
            maxLines = 2,
        )
        BasicText(
            "${item.author} · ${item.mediaKind}",
            style = TextStyle(color = Color(0xFF89929E), fontSize = 10.sp),
            maxLines = 2,
        )
    }
}

private fun bookRequestState(status: String?): String? {
    val normalized = status.orEmpty().trim().lowercase()
    return when {
        normalized.isBlank() || normalized == "open" || normalized == "skipped" -> null
        "have" in normalized || "downloaded" in normalized -> "Available"
        "wanted" in normalized || "snatched" in normalized || "queued" in normalized -> "Requested"
        else -> null
    }
}

@Composable
private fun SearchBookRequestCard(
    item: VesperBookRequestResult,
    onOpen: () -> Unit,
) {
    val bookState = bookRequestState(item.ebookStatus)
    val audioState = bookRequestState(item.audiobookStatus)
    val badge = when {
        bookState == "Available" || audioState == "Available" -> "AVAILABLE"
        bookState == "Requested" || audioState == "Requested" -> "REQUESTED"
        else -> "REQUEST"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF111A23)),
            contentAlignment = Alignment.Center,
        ) {
            if (!item.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    url = item.coverUrl,
                    scaleType = ImageView.ScaleType.CENTER_CROP,
                )
            } else {
                BasicText(
                    "▤",
                    style = TextStyle(color = Color(0xFFC6B8FF), fontSize = 44.sp, fontWeight = FontWeight.Bold),
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (badge == "AVAILABLE") Color(0xDD173D2A)
                        else Color(0xDD26305C)
                    )
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                BasicText(
                    badge,
                    style = TextStyle(color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        BasicText(
            item.title,
            style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 14.sp),
            maxLines = 2,
        )
        BasicText(
            item.author,
            style = TextStyle(color = Color(0xFF89929E), fontSize = 10.sp),
            maxLines = 1,
        )
    }
}

@Composable
private fun RequestFormatButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (enabled) Color(0xFFEAF6FC) else Color(0xFF17232D))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label,
            style = TextStyle(
                color = if (enabled) Color(0xFF071017) else Color(0xFFD4DDE5),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun MusicRequestDetailsPopup(
    item: VesperMusicRequestResult,
    onDismiss: () -> Unit,
    onRequest: suspend (VesperMusicRequestResult) -> String?,
    onRequested: () -> Unit,
) {
    var status by remember(item.albumMbid, item.status, item.inLibrary) {
        mutableStateOf(musicRequestBadge(item))
    }
    var requesting by remember(item.albumMbid) { mutableStateOf(false) }
    var message by remember(item.albumMbid) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val requestEnabled = !requesting && status == "REQUEST"

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF706090D))
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(
                            "MUSIC",
                            style = TextStyle(
                                color = Color(0xFFA98CFF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                            ),
                        )
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(21.dp))
                                .background(Color(0x22FFFFFF))
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                "×",
                                style = TextStyle(
                                    color = Color.White,
                                    fontSize = 25.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }

                item {
                    Box(
                        modifier = Modifier
                            .width(250.dp)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF111A23)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!item.coverUrl.isNullOrBlank()) {
                            AsyncImage(
                                modifier = Modifier.fillMaxSize(),
                                url = item.coverUrl,
                                scaleType = ImageView.ScaleType.CENTER_CROP,
                            )
                        } else {
                            BasicText(
                                "♫",
                                style = TextStyle(
                                    color = Color(0xFFA98CFF),
                                    fontSize = 70.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }

                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        BasicText(
                            item.albumName,
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 25.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        Spacer(Modifier.height(5.dp))
                        BasicText(
                            listOfNotNull(
                                item.artistName,
                                item.releaseDate?.take(4),
                                when (status) {
                                    "AVAILABLE" -> "Available"
                                    "REQUESTED" -> "Requested"
                                    else -> "Album"
                                },
                            ).joinToString("  •  "),
                            style = TextStyle(color = Color(0xFF9CA4B0), fontSize = 13.sp),
                        )
                        Spacer(Modifier.height(22.dp))
                        RequestFormatButton(
                            label = when {
                                requesting -> "Requesting…"
                                status == "AVAILABLE" -> "Available"
                                status == "REQUESTED" -> "Requested"
                                else -> "Request Album"
                            },
                            enabled = requestEnabled,
                        ) {
                            requesting = true
                            message = null
                            scope.launch {
                                val result = onRequest(item)
                                requesting = false
                                if (result != null && !result.contains("failed", ignoreCase = true)) {
                                    status = if (result.equals("Available", ignoreCase = true)) {
                                        "AVAILABLE"
                                    } else {
                                        "REQUESTED"
                                    }
                                    message = result
                                    onRequested()
                                } else {
                                    message = result ?: "Request failed"
                                }
                            }
                        }
                        if (!message.isNullOrBlank()) {
                            Spacer(Modifier.height(9.dp))
                            BasicText(
                                message.orEmpty(),
                                style = TextStyle(color = Color(0xFF9BA8B5), fontSize = 11.sp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookRequestDetailsPopup(
    item: VesperBookRequestResult,
    onDismiss: () -> Unit,
    onRequest: suspend (VesperBookRequestResult, Boolean) -> String?,
    onRequested: () -> Unit,
) {
    var bookState by remember(item.bookId, item.ebookStatus) {
        mutableStateOf(bookRequestState(item.ebookStatus))
    }
    var audioState by remember(item.bookId, item.audiobookStatus) {
        mutableStateOf(bookRequestState(item.audiobookStatus))
    }
    var requestingBook by remember(item.bookId) { mutableStateOf(false) }
    var requestingAudio by remember(item.bookId) { mutableStateOf(false) }
    var message by remember(item.bookId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF706090D))
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(
                            "BOOKS & AUDIOBOOKS",
                            style = TextStyle(
                                color = Color(0xFFA98CFF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.3.sp,
                            ),
                        )
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(21.dp))
                                .background(Color(0x22FFFFFF))
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                "×",
                                style = TextStyle(
                                    color = Color.White,
                                    fontSize = 25.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }

                item {
                    Box(
                        modifier = Modifier
                            .width(210.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0xFF111A23)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!item.coverUrl.isNullOrBlank()) {
                            AsyncImage(
                                modifier = Modifier.fillMaxSize(),
                                url = item.coverUrl,
                                scaleType = ImageView.ScaleType.CENTER_CROP,
                            )
                        } else {
                            BasicText(
                                "▤",
                                style = TextStyle(
                                    color = Color(0xFFC6B8FF),
                                    fontSize = 64.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }

                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        BasicText(
                            item.title,
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 25.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        Spacer(Modifier.height(5.dp))
                        BasicText(
                            listOfNotNull(item.author, item.year?.take(4)).joinToString("  •  "),
                            style = TextStyle(color = Color(0xFF9CA4B0), fontSize = 13.sp),
                        )
                        Spacer(Modifier.height(22.dp))

                        RequestFormatButton(
                            label = when {
                                requestingBook -> "Requesting Book…"
                                bookState == "Available" -> "Book Available"
                                bookState == "Requested" -> "Book Requested"
                                else -> "Request Book"
                            },
                            enabled = !requestingBook && bookState == null,
                        ) {
                            requestingBook = true
                            message = null
                            scope.launch {
                                val result = onRequest(item, false)
                                requestingBook = false
                                if (result != null && !result.contains("failed", ignoreCase = true)) {
                                    bookState = if (result.contains("available", ignoreCase = true)) {
                                        "Available"
                                    } else {
                                        "Requested"
                                    }
                                    message = result
                                    onRequested()
                                } else {
                                    message = result ?: "Request failed"
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        RequestFormatButton(
                            label = when {
                                requestingAudio -> "Requesting Audiobook…"
                                audioState == "Available" -> "Audiobook Available"
                                audioState == "Requested" -> "Audiobook Requested"
                                else -> "Request Audiobook"
                            },
                            enabled = !requestingAudio && audioState == null,
                        ) {
                            requestingAudio = true
                            message = null
                            scope.launch {
                                val result = onRequest(item, true)
                                requestingAudio = false
                                if (result != null && !result.contains("failed", ignoreCase = true)) {
                                    audioState = if (result.contains("available", ignoreCase = true)) {
                                        "Available"
                                    } else {
                                        "Requested"
                                    }
                                    message = result
                                    onRequested()
                                } else {
                                    message = result ?: "Request failed"
                                }
                            }
                        }

                        if (!message.isNullOrBlank()) {
                            Spacer(Modifier.height(9.dp))
                            BasicText(
                                message.orEmpty(),
                                style = TextStyle(color = Color(0xFF9BA8B5), fontSize = 11.sp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeerrMediaCard(
    item: SeerrSearchResult,
    onRequest: suspend (SeerrSearchResult) -> String?,
    onRequested: () -> Unit,
    onOpenDetails: (SeerrSearchResult) -> Unit,
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
                .background(Color(0xFF111A23))
                .clickable { onOpenDetails(item) },
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
                    when (mediaStatus) {
                        SEERR_STATUS_PENDING -> "REQUESTED"
                        SEERR_STATUS_PROCESSING -> "PROCESSING"
                        SEERR_STATUS_PARTIALLY_AVAILABLE -> "PARTIAL"
                        SEERR_STATUS_AVAILABLE -> "AVAILABLE"
                        SEERR_STATUS_BLOCKLISTED -> "BLOCKED"
                        else -> "REQUEST"
                    },
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
private fun MediaRequestDetailsPopup(
    item: SeerrSearchResult,
    onDismiss: () -> Unit,
    onRequest: suspend (SeerrSearchResult) -> String?,
    onRequested: () -> Unit,
) {
    var mediaStatus by remember(item.tmdbId, item.mediaType, item.mediaStatus) {
        mutableStateOf(item.mediaStatus)
    }
    var requesting by remember(item.tmdbId, item.mediaType) { mutableStateOf(false) }
    var message by remember(item.tmdbId, item.mediaType) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val requestEnabled =
        !requesting && (mediaStatus == SEERR_STATUS_UNKNOWN || mediaStatus == SEERR_STATUS_DELETED)
    val statusLabel = when (mediaStatus) {
        SEERR_STATUS_PENDING -> "Requested"
        SEERR_STATUS_PROCESSING -> "Processing"
        SEERR_STATUS_PARTIALLY_AVAILABLE -> "Partially available"
        SEERR_STATUS_AVAILABLE -> "Available"
        SEERR_STATUS_BLOCKLISTED -> "Blocked"
        else -> "Not requested"
    }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF706090D))
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(
                            "MEDIA REQUEST",
                            style = TextStyle(
                                color = Color(0xFFA98CFF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                            ),
                        )
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(21.dp))
                                .background(Color(0x22FFFFFF))
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                "×",
                                style = TextStyle(color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.SemiBold),
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }

                item {
                    Box(
                        modifier = Modifier
                            .width(220.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0xFF111A23)),
                    ) {
                        AsyncImage(
                            modifier = Modifier.fillMaxSize(),
                            url = item.posterUrl,
                            scaleType = ImageView.ScaleType.CENTER_CROP,
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                }

                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        BasicText(
                            item.title,
                            style = TextStyle(color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold),
                        )
                        Spacer(Modifier.height(6.dp))
                        BasicText(
                            listOfNotNull(
                                item.year?.toString(),
                                if (item.mediaType == "movie") "Movie" else "TV Series",
                                statusLabel,
                            ).joinToString("  •  "),
                            style = TextStyle(color = Color(0xFF9CA4B0), fontSize = 13.sp),
                        )

                        if (item.overview.isNotBlank()) {
                            Spacer(Modifier.height(20.dp))
                            BasicText(
                                item.overview,
                                style = TextStyle(
                                    color = Color(0xFFD0D7DE),
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp,
                                ),
                            )
                        }

                        Spacer(Modifier.height(22.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(17.dp))
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
                                            message = if (error == null) "Request sent" else "Already requested"
                                            onRequested()
                                        } else {
                                            message = error
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                when {
                                    requesting -> "Requesting…"
                                    requestEnabled -> "Request Media"
                                    else -> statusLabel
                                },
                                style = TextStyle(
                                    color = if (requestEnabled) Color(0xFF071017) else Color(0xFFD4DDE5),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }

                        if (!message.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            BasicText(
                                message.orEmpty(),
                                style = TextStyle(
                                    color = if (message == "Request sent" || message == "Already requested") {
                                        Color(0xFF8EDCB2)
                                    } else {
                                        Color(0xFFFFA6A6)
                                    },
                                    fontSize = 11.sp,
                                ),
                            )
                        }
                    }
                }
            }
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
                MyVMark(favorite = item.userData?.isFavorite == true)
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
private fun MobileNavDock(
    active: MobileTab,
    onSelect: (MobileTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val highlighted = when (active) {
        MobileTab.MOVIES, MobileTab.TV, MobileTab.MYV -> MobileTab.VIDEO
        else -> active
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 72.dp)
                .clip(RoundedCornerShape(29.dp))
                .background(Color(0xE51B1C2A))
                .border(1.dp, Color(0x554C4D7B), RoundedCornerShape(29.dp))
                .padding(horizontal = 7.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            primaryMobileTabs.forEach { tab ->
                val selected = tab == highlighted
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            if (selected) Color(0x443D2D9B)
                            else Color.Transparent
                        )
                        .clickable { onSelect(tab) }
                        .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BasicText(
                        tab.icon,
                        style = TextStyle(
                            color = if (selected) Color(0xFFA891FF) else Color(0xFFC3C8D2),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Spacer(Modifier.height(7.dp))
                    BasicText(
                        tab.label,
                        style = TextStyle(
                            color = if (selected) Color(0xFFA891FF) else Color(0xFFB4BBC6),
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(58.dp)
                .clip(RoundedCornerShape(29.dp))
                .background(
                    if (active == MobileTab.SEARCH) Color(0xFF476DFF)
                    else Color(0xE52B2D3C)
                )
                .border(
                    1.dp,
                    if (active == MobileTab.SEARCH) Color(0xFF87B5FF) else Color(0x665A5D75),
                    RoundedCornerShape(29.dp),
                )
                .clickable { onSelect(MobileTab.SEARCH) },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                "⌕",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Normal,
                ),
            )
        }
    }
}


@Composable
private fun MyVMark(
    favorite: Boolean,
    modifier: Modifier = Modifier,
    withLabel: Boolean = false,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(24.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (favorite) Color(0xFF6C4AE8) else Color(0x22161B28))
                .border(
                    1.dp,
                    if (favorite) Color(0xFFB6A5FF) else Color(0xFF8D95A6),
                    RoundedCornerShape(7.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                "V",
                style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black),
            )
            BasicText(
                if (favorite) "✓" else "+",
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 1.dp),
                style = TextStyle(
                    color = if (favorite) Color.White else Color(0xFFC4B5FD),
                    fontSize = 9.sp, fontWeight = FontWeight.Bold,
                ),
            )
        }
        if (withLabel) {
            Spacer(Modifier.width(9.dp))
            BasicText(
                if (favorite) "In MyV" else "Add to MyV",
                style = TextStyle(
                    color = if (favorite) Color(0xFFC1ACFF) else Color.White,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}

@Composable
private fun MyVBrowser(
    videos: List<BaseItemDto>,
    tracks: List<MaMediaItem>,
    musicPlayers: List<MaPlayer>,
    selectedSection: String,
    onSectionChange: (String) -> Unit,
    onLoadTrackGenres: suspend (MaMediaItem) -> List<String>,
    api: ApiClient,
    onSelectVideo: (BaseItemDto) -> Unit,
    onToggleVideo: (BaseItemDto) -> Unit,
    onToggleMusic: (MaMediaItem) -> Unit,
    onPlayMusic: (MaMediaItem, List<MaPlayer>) -> Unit,
    popularity: PopularityState,
    popularityScope: PopularityScope,
    tmdbConfigured: Boolean,
    expanded: Boolean,
) {
    var pendingTrack by remember { mutableStateOf<MaMediaItem?>(null) }
    var selectedMusicGenre by remember { mutableStateOf<String?>(null) }
    var fetchedGenres by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }

    // Existing bookmarks predate stored genre tags. Enrich the screen without
    // changing bookmark ownership or making up a genre for unmatched tracks.
    LaunchedEffect(selectedSection, tracks.map { it.uri }) {
        if (selectedSection == "Music") {
            for (track in tracks) {
                if (track.genres.isNotEmpty() || fetchedGenres.containsKey(track.uri)) continue
                val found = try {
                    onLoadTrackGenres(track)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    emptyList()
                }
                fetchedGenres = fetchedGenres + (track.uri to found)
            }
        }
    }
    fun genresOf(track: MaMediaItem): List<String> =
        track.genres.ifEmpty { fetchedGenres[track.uri].orEmpty() }

    val musicGenres = tracks.flatMap(::genresOf)
        .map(String::trim).filter(String::isNotBlank).distinct().sorted()
    val hasUncategorised = tracks.any { genresOf(it).isEmpty() }
    val filteredTracks = when (val genre = selectedMusicGenre) {
        null -> tracks
        "Uncategorised" -> tracks.filter { genresOf(it).isEmpty() }
        else -> tracks.filter { item ->
            genresOf(item).any { it.equals(genre, ignoreCase = true) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(start = 20.dp, end = 18.dp, top = 16.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            listOf("Video", "Music").forEach { section ->
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(13.dp))
                        .background(if (selectedSection == section) Color(0xFF6951C5) else Color(0xFF171925))
                        .clickable { onSectionChange(section) }
                        .padding(horizontal = 22.dp, vertical = 11.dp),
                ) {
                    BasicText(section,
                        style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                }
            }
        }
        Box(Modifier.weight(1f)) {
            if (selectedSection == "Video") {
                LibraryBrowse(
                    title = "MyV · Video",
                    media = videos,
                    api = api,
                    onSelect = onSelectVideo,
                    onToggleFavorite = onToggleVideo,
                    popularity = popularity,
                    popularityScope = popularityScope,
                    tmdbConfigured = tmdbConfigured,
                    expanded = expanded,
                    showVideoTypeFilter = true,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 160.dp),
                ) {
                    item {
                        BasicText(
                            "MyV · Music",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 17.dp),
                            style = TextStyle(color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                    if (tracks.isNotEmpty()) {
                        item {
                            LazyRow(
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                item {
                                    GenreChip(label = "All", selected = selectedMusicGenre == null) {
                                        selectedMusicGenre = null
                                    }
                                }
                                items(musicGenres, key = { it }) { genre ->
                                    GenreChip(
                                        label = genre,
                                        selected = selectedMusicGenre == genre,
                                    ) { selectedMusicGenre = if (selectedMusicGenre == genre) null else genre }
                                }
                                if (hasUncategorised) item {
                                    GenreChip(
                                        label = "Uncategorised",
                                        selected = selectedMusicGenre == "Uncategorised",
                                    ) {
                                        selectedMusicGenre = if (selectedMusicGenre == "Uncategorised") null
                                            else "Uncategorised"
                                    }
                                }
                            }
                        }
                        item {
                            BasicText(
                                if (selectedMusicGenre == null) "${tracks.size} saved tracks"
                                else "${filteredTracks.size} tracks · ${selectedMusicGenre}",
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 9.dp),
                                style = TextStyle(color = Color(0xFF8794A4), fontSize = 12.sp),
                            )
                        }
                    }
                    if (tracks.isEmpty()) {
                        item {
                            BasicText(
                                "No favourite songs yet. Tap V+ beside a song to save it here.",
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                                style = TextStyle(color = Color(0xFF9CA4B0), fontSize = 14.sp),
                            )
                        }
                    } else if (filteredTracks.isEmpty()) {
                        item {
                            BasicText(
                                "No favourite tracks in this genre.",
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                                style = TextStyle(color = Color(0xFF9CA4B0), fontSize = 14.sp),
                            )
                        }
                    } else {
                        items(filteredTracks, key = { it.uri }) { track ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { pendingTrack = track }
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(11.dp))
                                        .background(Color(0xFF191A28)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (!track.imageUrl.isNullOrBlank()) {
                                        AsyncImage(modifier = Modifier.fillMaxSize(), url = track.imageUrl,
                                            scaleType = ImageView.ScaleType.CENTER_CROP)
                                    } else {
                                        BasicText("♫", style = TextStyle(
                                            color = Color(0xFFA98CFF), fontSize = 22.sp))
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    BasicText(track.name, maxLines = 1,
                                        style = TextStyle(color = Color.White, fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold))
                                    BasicText(track.subtitle, maxLines = 1,
                                        style = TextStyle(color = Color(0xFF9CA4B0), fontSize = 11.sp))
                                }
                                MyVMark(favorite = true,
                                    modifier = Modifier.clickable { onToggleMusic(track) }
                                        .padding(horizontal = 8.dp, vertical = 8.dp))
                            }
                        }
                    }
                }
            }
            pendingTrack?.let { track ->
                MusicRoomPicker(
                    item = track,
                    players = musicPlayers,
                    onDismiss = { pendingTrack = null },
                    onPlay = { selectedPlayers ->
                        pendingTrack = null
                        onPlayMusic(track, selectedPlayers)
                    },
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
        MyVMark(favorite = favorite, withLabel = true)
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
