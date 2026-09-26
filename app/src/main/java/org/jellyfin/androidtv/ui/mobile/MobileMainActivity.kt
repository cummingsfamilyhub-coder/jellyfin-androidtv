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
                        DetailCopy(item, api, onPlay, expanded = true)
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
                        DetailCopy(item, api, onPlay, expanded = false)
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
        VesperButton(
            if ((item.userData?.playbackPositionTicks ?: 0L) > 0L) "▶ Resume" else "▶ Play",
            { onPlay(item) },
        )
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
