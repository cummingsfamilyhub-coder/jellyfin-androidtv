package org.jellyfin.androidtv.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.navigation.ActivityDestinations
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
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

private data class VesperTvHomeState(
    val loading: Boolean = true,
    val error: String? = null,
    val continueWatching: List<BaseItemDto> = emptyList(),
    val myV: List<BaseItemDto> = emptyList(),
    val movies: List<BaseItemDto> = emptyList(),
    val shows: List<BaseItemDto> = emptyList(),
)

class VesperTvHomeFragment : Fragment() {
    private val api by inject<ApiClient>()
    private val navigationRepository by inject<NavigationRepository>()
    private val sessionRepository by inject<SessionRepository>()
    private val userRepository by inject<UserRepository>()

    private var state by mutableStateOf(VesperTvHomeState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadHome()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = content {
        VesperTvHome(
            state = state,
            api = api,
            userName = userRepository.currentUser.value?.name ?: "Vesper",
            onSelect = { item ->
                navigationRepository.navigate(Destinations.itemDetails(item.id))
            },
            onSearch = {
                navigationRepository.navigate(Destinations.search())
            },
            onSettings = {
                val activity = requireActivity()
                activity.startActivity(ActivityDestinations.userPreferences(activity))
            },
            onProfile = {
                val activity = requireActivity()
                sessionRepository.destroyCurrentSession()
                activity.startActivity(ActivityDestinations.startup(activity))
                activity.finishAfterTransition()
            },
            onRetry = ::loadHome,
        )
    }

    private fun loadHome() {
        state = VesperTvHomeState(loading = true)
        lifecycleScope.launch {
            state = runCatching {
                withContext(Dispatchers.IO) {
                    val resume = async {
                        api.itemsApi.getResumeItems(
                            fields = ItemRepository.browseFields,
                            imageTypeLimit = 1,
                            limit = 16,
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
                            limit = 30,
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
                            limit = 30,
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
                            limit = 30,
                            sortBy = setOf(ItemSortBy.PLAY_COUNT),
                            sortOrder = setOf(SortOrder.DESCENDING),
                        ).content.items
                    }

                    VesperTvHomeState(
                        loading = false,
                        continueWatching = resume.await(),
                        myV = favorites.await(),
                        movies = movies.await(),
                        shows = shows.await(),
                    )
                }
            }.getOrElse { error ->
                VesperTvHomeState(
                    loading = false,
                    error = error.message ?: "Couldn't load Vesper.",
                )
            }
        }
    }
}

@Composable
private fun VesperTvHome(
    state: VesperTvHomeState,
    api: ApiClient,
    userName: String,
    onSelect: (BaseItemDto) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onProfile: () -> Unit,
    onRetry: () -> Unit,
) {
    var highlighted by remember(state.continueWatching, state.movies) {
        mutableStateOf(state.continueWatching.firstOrNull() ?: state.movies.firstOrNull())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05080C)),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 56.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            item {
                VesperTvTopBar(
                    userName = userName,
                    onSearch = onSearch,
                    onSettings = onSettings,
                    onProfile = onProfile,
                )
            }

            when {
                state.loading -> item {
                    VesperTvStatus("Loading Vesper…")
                }

                state.error != null -> item {
                    VesperTvError(
                        message = state.error,
                        onRetry = onRetry,
                    )
                }

                else -> {
                    highlighted?.let { hero ->
                        item {
                            VesperTvHero(
                                item = hero,
                                api = api,
                                onClick = { onSelect(hero) },
                            )
                        }
                    }

                    if (state.continueWatching.isNotEmpty()) {
                        item {
                            VesperTvMediaRow(
                                title = "Continue Watching",
                                items = state.continueWatching,
                                api = api,
                                landscape = true,
                                requestInitialFocus = true,
                                onFocused = { highlighted = it },
                                onSelect = onSelect,
                            )
                        }
                    }

                    if (state.myV.isNotEmpty()) {
                        item {
                            VesperTvMediaRow(
                                title = "MyV",
                                items = state.myV,
                                api = api,
                                onFocused = { highlighted = it },
                                onSelect = onSelect,
                            )
                        }
                    }

                    if (state.movies.isNotEmpty()) {
                        item {
                            VesperTvMediaRow(
                                title = "Movies",
                                items = state.movies,
                                api = api,
                                onFocused = { highlighted = it },
                                onSelect = onSelect,
                            )
                        }
                    }

                    if (state.shows.isNotEmpty()) {
                        item {
                            VesperTvMediaRow(
                                title = "TV Shows",
                                items = state.shows,
                                api = api,
                                onFocused = { highlighted = it },
                                onSelect = onSelect,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VesperTvTopBar(
    userName: String,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onProfile: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 58.dp, vertical = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = "VESPER",
            style = TextStyle(
                color = Color.White,
                fontSize = 27.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
            ),
        )

        Spacer(Modifier.width(34.dp))

        BasicText(
            text = "Home",
            style = TextStyle(
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
        )

        Spacer(Modifier.weight(1f))

        VesperTvNavButton("Search", onSearch)
        Spacer(Modifier.width(12.dp))
        VesperTvNavButton("Settings", onSettings)
        Spacer(Modifier.width(12.dp))
        VesperTvNavButton(userName, onProfile)
    }
}

@Composable
private fun VesperTvNavButton(
    label: String,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "navScale")

    Box(
        modifier = Modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(999.dp))
            .background(if (focused) Color(0xFFEAF6FC) else Color(0xFF111820))
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 20.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = if (focused) Color(0xFF071017) else Color(0xFFD3DCE5),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun VesperTvHero(
    item: BaseItemDto,
    api: ApiClient,
    onClick: () -> Unit,
) {
    val primary = item.itemImages[ImageType.PRIMARY]
    val backdrop = item.itemBackdropImages.firstOrNull() ?: primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(370.dp),
    ) {
        AsyncImage(
            modifier = Modifier.fillMaxSize(),
            url = backdrop?.getUrl(api),
            blurHash = backdrop?.blurHash,
            scaleType = ImageView.ScaleType.CENTER_CROP,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xF705080C),
                            Color(0xB805080C),
                            Color(0x2205080C),
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(650.dp)
                .padding(start = 58.dp),
        ) {
            BasicText(
                text = item.name ?: "Untitled",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(12.dp))

            val metadata = listOfNotNull(
                item.productionYear?.toString(),
                item.officialRating,
                when (item.type) {
                    BaseItemKind.MOVIE -> "Movie"
                    BaseItemKind.SERIES -> "TV Series"
                    BaseItemKind.EPISODE -> item.seriesName
                    else -> null
                },
            ).distinct().joinToString("  •  ")

            if (metadata.isNotBlank()) {
                BasicText(
                    text = metadata,
                    style = TextStyle(
                        color = Color(0xFFCBD5DE),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                Spacer(Modifier.height(14.dp))
            }

            if (!item.overview.isNullOrBlank()) {
                BasicText(
                    text = item.overview.orEmpty(),
                    style = TextStyle(
                        color = Color(0xFFD7DEE5),
                        fontSize = 16.sp,
                        lineHeight = 23.sp,
                    ),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(20.dp))
            }

            VesperTvHeroButton("View details", onClick)
        }
    }
}

@Composable
private fun VesperTvHeroButton(
    label: String,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color.White else Color(0xE6EAF6FC))
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 26.dp, vertical = 12.dp),
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = Color(0xFF071017),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun VesperTvMediaRow(
    title: String,
    items: List<BaseItemDto>,
    api: ApiClient,
    landscape: Boolean = false,
    requestInitialFocus: Boolean = false,
    onFocused: (BaseItemDto) -> Unit,
    onSelect: (BaseItemDto) -> Unit,
) {
    Column {
        BasicText(
            text = title,
            style = TextStyle(
                color = Color.White,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier.padding(horizontal = 58.dp, vertical = 8.dp),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 58.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(items, key = { it.id }) { item ->
                VesperTvMediaCard(
                    item = item,
                    api = api,
                    landscape = landscape,
                    requestInitialFocus = requestInitialFocus && item.id == items.first().id,
                    onFocused = { onFocused(item) },
                    onClick = { onSelect(item) },
                )
            }
        }
    }
}

@Composable
private fun VesperTvMediaCard(
    item: BaseItemDto,
    api: ApiClient,
    landscape: Boolean,
    requestInitialFocus: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "cardScale")
    val focusRequester = remember { FocusRequester() }

    if (requestInitialFocus) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }

    val primary = item.itemImages[ImageType.PRIMARY]
    val backdrop = item.itemBackdropImages.firstOrNull() ?: primary
    val art = if (landscape) backdrop else primary ?: backdrop
    val width = if (landscape) 300.dp else 176.dp
    val height = if (landscape) 169.dp else 264.dp
    val shape = RoundedCornerShape(11.dp)

    Column(
        modifier = Modifier
            .width(width)
            .scale(scale)
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(onClick = onClick)
            .focusable(),
    ) {
        Box(
            modifier = Modifier
                .width(width)
                .height(height)
                .clip(shape)
                .background(Color(0xFF111820))
                .border(
                    width = if (focused) 3.dp else 0.dp,
                    color = if (focused) Color(0xFFEAF6FC) else Color.Transparent,
                    shape = shape,
                ),
        ) {
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                url = art?.getUrl(api),
                blurHash = art?.blurHash,
                scaleType = ImageView.ScaleType.CENTER_CROP,
            )

            if (landscape && (item.userData?.playbackPositionTicks ?: 0L) > 0L) {
                val runtime = item.runTimeTicks ?: 0L
                val position = item.userData?.playbackPositionTicks ?: 0L
                val progress = if (runtime > 0) {
                    (position.toFloat() / runtime.toFloat()).coerceIn(0f, 1f)
                } else 0f

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(Color(0x66000000)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(5.dp)
                            .background(Color(0xFFEAF6FC)),
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        BasicText(
            text = item.name ?: "Untitled",
            style = TextStyle(
                color = if (focused) Color.White else Color(0xFFD2DAE2),
                fontSize = 14.sp,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (item.type == BaseItemKind.EPISODE && !item.seriesName.isNullOrBlank()) {
            BasicText(
                text = item.seriesName.orEmpty(),
                style = TextStyle(
                    color = Color(0xFF8997A5),
                    fontSize = 12.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun VesperTvStatus(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = message,
            style = TextStyle(
                color = Color(0xFFB7C2CD),
                fontSize = 22.sp,
            ),
        )
    }
}

@Composable
private fun VesperTvError(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
            .padding(horizontal = 58.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(
            text = "Vesper couldn't load",
            style = TextStyle(
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(Modifier.height(10.dp))
        BasicText(
            text = message,
            style = TextStyle(
                color = Color(0xFFB7C2CD),
                fontSize = 16.sp,
            ),
        )
        Spacer(Modifier.height(18.dp))
        VesperTvHeroButton("Try again", onRetry)
    }
}
