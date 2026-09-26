package org.jellyfin.androidtv.ui.mobile

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import org.jellyfin.androidtv.R
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

class MobileMainActivity : FragmentActivity() {
    private val api by inject<ApiClient>()
    private val sessionRepository by inject<SessionRepository>()
    private val userRepository by inject<UserRepository>()
    private val itemMutationRepository by inject<ItemMutationRepository>()

    private var state by mutableStateOf(MobileHomeState())
    private var selected by mutableStateOf<BaseItemDto?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (sessionRepository.currentSession.value == null || userRepository.currentUser.value == null) {
            startActivity(Intent(this, MobileStartupActivity::class.java))
            finish()
            return
        }

        setContent {
            VesperMobile(
                state = state,
                selected = selected,
                userName = userRepository.currentUser.value?.name ?: "Vesper",
                api = api,
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
                            fields = ItemRepository.itemFields,
                            imageTypeLimit = 1,
                            limit = 20,
                            mediaTypes = listOf(MediaType.VIDEO),
                            includeItemTypes = listOf(BaseItemKind.EPISODE, BaseItemKind.MOVIE),
                            excludeActiveSessions = true,
                        ).content.items
                    }
                    val favorites = async {
                        api.itemsApi.getItems(
                            fields = ItemRepository.itemFields,
                            includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                            recursive = true,
                            filters = setOf(ItemFilter.IS_FAVORITE),
                            imageTypeLimit = 1,
                            limit = 20,
                            sortBy = setOf(ItemSortBy.SORT_NAME),
                        ).content.items
                    }
                    val movies = async {
                        api.itemsApi.getItems(
                            fields = ItemRepository.itemFields,
                            includeItemTypes = setOf(BaseItemKind.MOVIE),
                            recursive = true,
                            imageTypeLimit = 1,
                            limit = 24,
                            sortBy = setOf(ItemSortBy.DATE_CREATED),
                            sortOrder = setOf(SortOrder.DESCENDING),
                        ).content.items
                    }
                    val shows = async {
                        api.itemsApi.getItems(
                            fields = ItemRepository.itemFields,
                            includeItemTypes = setOf(BaseItemKind.SERIES),
                            recursive = true,
                            imageTypeLimit = 1,
                            limit = 24,
                            sortBy = setOf(ItemSortBy.DATE_CREATED),
                            sortOrder = setOf(SortOrder.DESCENDING),
                        ).content.items
                    }
                    val boxSets = async {
                        api.itemsApi.getItems(
                            fields = ItemRepository.itemFields,
                            includeItemTypes = setOf(BaseItemKind.BOX_SET),
                            recursive = true,
                            imageTypeLimit = 1,
                            limit = 100,
                            sortBy = setOf(ItemSortBy.SORT_NAME),
                        ).content.items
                    }

                    val allCollections = boxSets.await()
                    MobileHomeState(
                        continueWatching = resume.await(),
                        myV = favorites.await(),
                        movies = movies.await(),
                        shows = shows.await(),
                        services = allCollections.filter(::isServiceCollection),
                        collections = allCollections.filterNot(::isServiceCollection),
                    )
                }
            }.getOrElse { error ->
                MobileHomeState(error = error.message ?: error::class.java.simpleName)
            }
        }
    }

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

    private fun openSettings() {
        startActivity(Intent(this, PreferencesActivity::class.java))
    }

    private fun playItem(item: BaseItemDto) {
        startActivity(
            Intent(this, MobilePlayerActivity::class.java)
                .putExtra(MobilePlayerActivity.EXTRA_ITEM_ID, item.id.toString())
        )
    }
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
    "amazon prime video",
    "max",
    "hbo max",
    "apple tv+",
    "apple tv plus",
    "paramount+",
    "paramount plus",
    "peacock",
    "hulu",
    "bbc iplayer",
    "itvx",
)

private fun isServiceCollection(item: BaseItemDto): Boolean {
    val name = item.name?.trim()?.lowercase() ?: return false
    return name in serviceCollectionAliases
}

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
    selected: BaseItemDto?,
    userName: String,
    api: ApiClient,
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
            MobileDetails(selected, api, onBack, onPlay, onToggleFavorite)
        } else {
            BackHandler(enabled = tab != MobileTab.HOME) { tab = MobileTab.HOME }

            when (tab) {
                MobileTab.HOME -> MobileHome(
                    state = state,
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
                    expanded = expanded,
                )
                MobileTab.TV -> LibraryBrowse(
                    title = "TV Shows",
                    media = state.shows,
                    api = api,
                    onSelect = onSelect,
                    onToggleFavorite = onToggleFavorite,
                    expanded = expanded,
                )
                MobileTab.MYV -> LibraryBrowse(
                    title = "MyV",
                    media = state.myV,
                    api = api,
                    onSelect = onSelect,
                    onToggleFavorite = onToggleFavorite,
                    expanded = expanded,
                )
                MobileTab.SEARCH -> SearchBrowse(
                    state = state,
                    api = api,
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
            if (state.myV.isNotEmpty()) item { MediaRow("MyV", state.myV, api, onSelect, onToggleFavorite) }
            if (state.movies.isNotEmpty()) item { MediaRow("Movies", state.movies, api, onSelect, onToggleFavorite) }
            if (state.shows.isNotEmpty()) item { MediaRow("TV Shows", state.shows, api, onSelect, onToggleFavorite) }
            if (state.services.isNotEmpty()) item {
                MediaRow("Services", state.services, api, onSelect, onToggleFavorite, landscape = true)
            }
            if (state.collections.isNotEmpty()) item {
                MediaRow("Collections", state.collections, api, onSelect, onToggleFavorite, landscape = true)
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
) {
    Column(Modifier.padding(top = 14.dp)) {
        BasicText(
            title,
            style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(media, key = { it.id }) { item ->
                MediaCard(item, api, landscape, onSelect, onToggleFavorite)
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
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                url = image?.getUrl(api),
                blurHash = image?.blurHash,
                scaleType = ImageView.ScaleType.CENTER_CROP,
            )

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

        Spacer(Modifier.height(7.dp))
        BasicText(
            item.name ?: "Untitled",
            style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
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
            BasicText(
                secondary,
                style = TextStyle(color = Color(0xFF8F9CAA), fontSize = 12.sp),
                maxLines = 1,
            )
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
    expanded: Boolean,
) {
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
                "${media.size} items",
                style = TextStyle(color = Color(0xFF81909E), fontSize = 13.sp),
            )
        }

        Spacer(Modifier.height(6.dp))

        if (media.isEmpty()) {
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
                gridItems(media, key = { it.id }) { item ->
                    GridMediaCard(item, api, onSelect, onToggleFavorite)
                }
            }
        }
    }
}

@Composable
private fun SearchBrowse(
    state: MobileHomeState,
    api: ApiClient,
    onSelect: (BaseItemDto) -> Unit,
    onToggleFavorite: (BaseItemDto) -> Unit,
    expanded: Boolean,
) {
    var query by remember { mutableStateOf("") }
    val all = remember(state) {
        (state.continueWatching + state.myV + state.movies + state.shows)
            .distinctBy { it.id }
    }
    val results = if (query.isBlank()) all else all.filter { item ->
        val haystack = listOfNotNull(
            item.name,
            item.seriesName,
            item.productionYear?.toString(),
        ).joinToString(" ").lowercase()
        haystack.contains(query.trim().lowercase())
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
                    "Movies and TV shows",
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
            gridItems(results, key = { it.id }) { item ->
                GridMediaCard(item, api, onSelect, onToggleFavorite)
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
                    fields = ItemRepository.itemFields,
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
