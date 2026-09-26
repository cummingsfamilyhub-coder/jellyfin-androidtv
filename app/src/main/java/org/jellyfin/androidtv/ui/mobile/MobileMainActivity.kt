package org.jellyfin.androidtv.ui.mobile

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
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
import org.jellyfin.androidtv.ui.browsing.MainActivity
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.startup.StartupActivity
import org.jellyfin.androidtv.util.PlaybackHelper
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
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
    private val playbackHelper by inject<PlaybackHelper>()

    private var state by mutableStateOf(MobileHomeState())
    private var selected by mutableStateOf<BaseItemDto?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (sessionRepository.currentSession.value == null || userRepository.currentUser.value == null) {
            startActivity(Intent(this, StartupActivity::class.java))
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
                    val services = async {
                        api.itemsApi.getItems(
                            fields = ItemRepository.itemFields,
                            includeItemTypes = setOf(BaseItemKind.BOX_SET),
                            recursive = true,
                            imageTypeLimit = 1,
                            limit = 20,
                            sortBy = setOf(ItemSortBy.SORT_NAME),
                        ).content.items
                    }

                    MobileHomeState(
                        continueWatching = resume.await(),
                        myV = favorites.await(),
                        movies = movies.await(),
                        shows = shows.await(),
                        services = services.await(),
                    )
                }
            }.getOrElse { error ->
                MobileHomeState(error = error.message ?: error::class.java.simpleName)
            }
        }
    }

    private fun playItem(item: BaseItemDto) {
        // Existing Jellyfin playback UI still lives in MainActivity for this smoke-test build.
        // Launch it only when playback is requested; the mobile home never enters the TV browsing stack.
        startActivity(Intent(this, MainActivity::class.java))
        playbackHelper.retrieveAndPlay(item.id, false, null, this)
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
)

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
) {
    val bg = Color(0xFF05080C)
    Box(Modifier.fillMaxSize().background(bg)) {
        if (selected != null) {
            BackHandler(onBack = onBack)
            MobileDetails(selected, api, onBack, onPlay)
        } else {
            MobileHome(state, userName, api, onSelect, onRetry)
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
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 42.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    "V",
                    style = TextStyle(color = Color(0xFFBDEBFF), fontSize = 27.sp, fontWeight = FontWeight.Black),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(Color(0xFF13222E))
                        .padding(start = 15.dp, top = 8.dp),
                )
                Spacer(Modifier.width(13.dp))
                Column {
                    BasicText("Vesper", style = TextStyle(color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold))
                    BasicText(userName, style = TextStyle(color = Color(0xFF8D99A7), fontSize = 13.sp))
                }
            }
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
                    BasicText("Couldn't load your library", style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(7.dp))
                    BasicText(state.error, style = TextStyle(color = Color(0xFF9CA9B7), fontSize = 14.sp))
                    Spacer(Modifier.height(16.dp))
                    VesperButton("Try again", onRetry)
                }
            }
        } else {
            if (state.continueWatching.isNotEmpty()) item { MediaRow("Continue Watching", state.continueWatching, api, onSelect, landscape = true) }
            if (state.myV.isNotEmpty()) item { MediaRow("MyV", state.myV, api, onSelect) }
            if (state.movies.isNotEmpty()) item { MediaRow("Movies", state.movies, api, onSelect) }
            if (state.shows.isNotEmpty()) item { MediaRow("TV Shows", state.shows, api, onSelect) }
            if (state.services.isNotEmpty()) item { MediaRow("Services", state.services, api, onSelect, landscape = true) }

            if (
                state.continueWatching.isEmpty() &&
                state.myV.isEmpty() &&
                state.movies.isEmpty() &&
                state.shows.isEmpty() &&
                state.services.isEmpty()
            ) {
                item {
                    BasicText(
                        "Connected, but Jellyfin returned no video items.",
                        style = TextStyle(color = Color(0xFF9CA9B7), fontSize = 16.sp),
                        modifier = Modifier.padding(20.dp),
                    )
                }
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
    landscape: Boolean = false,
) {
    Column(Modifier.padding(top = 17.dp)) {
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
                MediaCard(item, api, landscape, onSelect)
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
) {
    val w = if (landscape) 210.dp else 132.dp
    val h = if (landscape) 122.dp else 198.dp
    val image = item.itemImages[ImageType.PRIMARY]

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
            if (item.userData?.isFavorite == true) {
                BasicText(
                    "♥",
                    style = TextStyle(color = Color.White, fontSize = 16.sp),
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        BasicText(
            item.name ?: "Untitled",
            style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
        )
        val secondary = when (item.type) {
            BaseItemKind.EPISODE -> listOfNotNull(item.seriesName, item.parentIndexNumber?.let { "S$it" }, item.indexNumber?.let { "E$it" }).joinToString(" • ")
            else -> item.productionYear?.toString().orEmpty()
        }
        if (secondary.isNotBlank()) {
            BasicText(secondary, style = TextStyle(color = Color(0xFF8F9CAA), fontSize = 12.sp), maxLines = 1)
        }
    }
}

@Composable
private fun MobileDetails(
    item: BaseItemDto,
    api: ApiClient,
    onBack: () -> Unit,
    onPlay: (BaseItemDto) -> Unit,
) {
    val image = item.itemImages[ImageType.PRIMARY]
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth().height(390.dp).background(Color(0xFF101820)),
            ) {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    url = image?.getUrl(api),
                    blurHash = image?.blurHash,
                    scaleType = ImageView.ScaleType.CENTER_CROP,
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(125.dp)
                        .background(Color(0xCC05080C))
                )
                VesperButton("‹ Back", onBack, Modifier.align(Alignment.TopStart).padding(18.dp))
            }
        }
        item {
            Column(Modifier.padding(20.dp)) {
                BasicText(
                    item.name ?: "Untitled",
                    style = TextStyle(color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.height(8.dp))
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
                Spacer(Modifier.height(18.dp))
                VesperButton(
                    if ((item.userData?.playbackPositionTicks ?: 0L) > 0L) "▶ Resume" else "▶ Play",
                    { onPlay(item) },
                )
                Spacer(Modifier.height(20.dp))
                if (!item.overview.isNullOrBlank()) {
                    BasicText(
                        item.overview.orEmpty(),
                        style = TextStyle(color = Color(0xFFD0D7DE), fontSize = 16.sp, lineHeight = 23.sp),
                    )
                }
            }
        }
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
