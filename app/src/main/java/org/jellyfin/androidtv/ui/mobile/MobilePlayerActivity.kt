package org.jellyfin.androidtv.ui.mobile

import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.player.base.PlayerSubtitles
import org.jellyfin.androidtv.ui.player.base.PlayerSurface
import org.jellyfin.androidtv.ui.composable.rememberQueueEntry
import org.jellyfin.androidtv.ui.mobile.cast.VesperCastButton
import org.jellyfin.androidtv.ui.mobile.cast.VesperCastManager
import org.jellyfin.androidtv.ui.playback.segment.MediaSegmentRepository
import org.jellyfin.androidtv.ui.playback.rewrite.RewriteMediaManager
import org.jellyfin.playback.core.PlaybackManager
import org.jellyfin.playback.core.model.PlayState
import org.jellyfin.playback.core.queue.queue
import org.jellyfin.playback.jellyfin.queue.baseItem
import org.jellyfin.playback.jellyfin.queue.baseItemFlow
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.androidtv.util.sdk.start
import org.jellyfin.androidtv.util.sdk.end
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

class MobilePlayerActivity : FragmentActivity() {
    companion object {
        const val EXTRA_ITEM_ID = "item_id"
    }

    private val api by inject<ApiClient>()
    private val playbackManager by inject<PlaybackManager>()
    private val sessionRepository by inject<SessionRepository>()
    private val serverRepository by inject<ServerRepository>()
    private val userRepository by inject<UserRepository>()
    private lateinit var castManager: VesperCastManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val itemId = intent.getStringExtra(EXTRA_ITEM_ID)?.let(UUID::fromString)
        if (itemId == null) {
            finish()
            return
        }

        castManager = VesperCastManager(
            activity = this,
            api = api,
            sessionRepository = sessionRepository,
            serverRepository = serverRepository,
        )
        castManager.updateReceiverApplicationId(
            userRepository.currentUser.value?.configuration?.castReceiverId
        )
        castManager.onRemotePlaybackStarted = {
            runOnUiThread {
                playbackManager.state.stop()
                finish()
            }
        }

        setContent {
            MobilePlayer(
                playbackManager = playbackManager,
                onClose = ::closePlayer,
                onPrepareCast = { item, startPositionTicks ->
                    castManager.prepareHandoff(item, startPositionTicks)
                },
            )
        }

        lifecycleScope.launch {
            prepare(itemId)
        }
    }

    private suspend fun prepare(itemId: UUID) {
        val item = withContext(Dispatchers.IO) {
            api.userLibraryApi.getItem(itemId).content
        }

        val items = when (item.type) {
            BaseItemKind.SERIES -> withContext(Dispatchers.IO) {
                api.tvShowsApi.getEpisodes(
                    seriesId = item.id,
                    isMissing = false,
                    fields = ItemRepository.itemFields,
                    limit = 500,
                ).content.items
            }
            BaseItemKind.SEASON -> withContext(Dispatchers.IO) {
                api.tvShowsApi.getEpisodes(
                    seriesId = requireNotNull(item.seriesId),
                    seasonId = item.id,
                    isMissing = false,
                    fields = ItemRepository.itemFields,
                    limit = 500,
                ).content.items
            }
            BaseItemKind.EPISODE -> withContext(Dispatchers.IO) {
                val seriesId = item.seriesId
                if (seriesId == null) listOf(item)
                else api.tvShowsApi.getEpisodes(
                    seriesId = seriesId,
                    isMissing = false,
                    fields = ItemRepository.itemFields,
                    limit = 500,
                ).content.items
            }
            else -> listOf(item)
        }

        if (items.isEmpty()) {
            finish()
            return
        }

        val selected = when (item.type) {
            BaseItemKind.SERIES, BaseItemKind.SEASON ->
                items.firstOrNull { it.userData?.played != true } ?: items.first()
            BaseItemKind.EPISODE ->
                items.firstOrNull { it.id == item.id } ?: item
            else -> item
        }
        val startIndex = items.indexOfFirst { it.id == selected.id }.coerceAtLeast(0)
        val resume = selected.userData?.playbackPositionTicks?.ticks

        playbackManager.state.stop()
        playbackManager.queue.clear()
        playbackManager.queue.addSupplier(
            RewriteMediaManager.BaseItemQueueSupplier(api, items, false)
        )
        playbackManager.queue.setIndex(startIndex)

        if (resume != null && resume.inWholeMilliseconds > 0) {
            playbackManager.state.seek(resume)
        }
        playbackManager.state.play()
    }

    private fun closePlayer() {
        playbackManager.state.stop()
        finish()
    }

    override fun onPause() {
        super.onPause()
        if (!isFinishing) playbackManager.state.pause()
    }

    override fun onResume() {
        super.onResume()
        if (playbackManager.state.playState.value == PlayState.PAUSED) {
            playbackManager.state.unpause()
        }
    }

    override fun onDestroy() {
        if (::castManager.isInitialized) castManager.destroy()
        if (isFinishing) playbackManager.state.stop()
        super.onDestroy()
    }
}

@Composable
private fun MobilePlayer(
    playbackManager: PlaybackManager,
    onClose: () -> Unit,
    onPrepareCast: (BaseItemDto, Long) -> Unit,
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var seekWidth by remember { mutableStateOf(1) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubMs by remember { mutableLongStateOf(0L) }
    val playState by playbackManager.state.playState.collectAsState()
    val videoSize by playbackManager.state.videoSize.collectAsState()
    val queueIndex by playbackManager.queue.entryIndex.collectAsState()
    val queueEntry by rememberQueueEntry(playbackManager)
    val currentItem = queueEntry?.run { baseItemFlow.collectAsState(baseItem).value }
    val mediaSegmentRepository = koinInject<MediaSegmentRepository>()
    val scope = rememberCoroutineScope()
    var segments by remember { mutableStateOf<List<MediaSegmentDto>>(emptyList()) }
    var nextEpisodeLoading by remember { mutableStateOf(false) }
    val hasNextEpisode =
        currentItem?.type == BaseItemKind.EPISODE &&
            queueIndex >= 0 &&
            queueIndex < playbackManager.queue.estimatedSize - 1
    val currentIntro = segments.firstOrNull { segment ->
        segment.type == MediaSegmentType.INTRO &&
            positionMs >= segment.start.inWholeMilliseconds &&
            positionMs < segment.end.inWholeMilliseconds
    }
    val aspectRatio = videoSize.aspectRatio.takeIf { !it.isNaN() && it > 0f } ?: (16f / 9f)

    BackHandler(onBack = onClose)

    LaunchedEffect(currentItem?.id) {
        nextEpisodeLoading = false
        segments = currentItem?.let { mediaSegmentRepository.getSegmentsForItem(it) }.orEmpty()
    }

    LaunchedEffect(playbackManager) {
        while (true) {
            val info = playbackManager.state.positionInfo
            if (!scrubbing) positionMs = info.active.inWholeMilliseconds.coerceAtLeast(0)
            durationMs = info.duration.inWholeMilliseconds.coerceAtLeast(0)
            delay(250)
        }
    }

    LaunchedEffect(controlsVisible, playState) {
        if (controlsVisible && playState == PlayState.PLAYING) {
            delay(5000)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible },
                    onDoubleTap = { offset ->
                        if (offset.x < size.width / 2f) {
                            playbackManager.state.rewind(10_000.milliseconds)
                        } else {
                            playbackManager.state.fastForward(30_000.milliseconds)
                        }
                        controlsVisible = true
                    },
                )
            },
    ) {
        PlayerSurface(
            playbackManager = playbackManager,
            modifier = Modifier
                .aspectRatio(aspectRatio, videoSize.height < videoSize.width)
                .fillMaxSize()
                .align(Alignment.Center),
        )

        PlayerSubtitles(
            playbackManager = playbackManager,
            modifier = Modifier
                .aspectRatio(aspectRatio, videoSize.height < videoSize.width)
                .fillMaxSize()
                .align(Alignment.Center),
        )

        if (controlsVisible) {
            Box(
                Modifier.fillMaxSize().background(Color(0x55000000))
            )

            MobileCloseButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopStart).padding(18.dp),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(18.dp)
                    .size(46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(Color(0x99000000)),
                contentAlignment = Alignment.Center,
            ) {
                VesperCastButton(
                    modifier = Modifier.size(42.dp),
                    onBeforeShowDialog = {
                        currentItem?.let { item ->
                            val handoffMs = if (scrubbing) scrubMs else positionMs
                            onPrepareCast(item, handoffMs.coerceAtLeast(0L) * 10_000L)
                        }
                    },
                )
            }

            if (currentIntro != null) {
                PlayerButton(
                    "Skip Intro",
                    {
                        playbackManager.state.seek(currentIntro.end)
                        controlsVisible = true
                    },
                    primary = true,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 18.dp, bottom = 120.dp),
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xCC05080C))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerButton(
                        "↺ Restart",
                        {
                            playbackManager.state.seek(0.milliseconds)
                            if (playState != PlayState.PLAYING) playbackManager.state.unpause()
                            controlsVisible = true
                        },
                    )

                    Spacer(Modifier.width(14.dp))

                    PlayerButton(
                        if (playState == PlayState.PLAYING) "❚❚" else "▶",
                        {
                            when (playState) {
                                PlayState.PLAYING -> playbackManager.state.pause()
                                PlayState.PAUSED -> playbackManager.state.unpause()
                                PlayState.STOPPED, PlayState.ERROR -> playbackManager.state.play()
                            }
                        },
                        primary = true,
                    )

                    if (hasNextEpisode) {
                        Spacer(Modifier.width(14.dp))
                        PlayerButton(
                            if (nextEpisodeLoading) "Loading…" else "Next Episode ›",
                            {
                                if (!nextEpisodeLoading) {
                                    nextEpisodeLoading = true
                                    scope.launch {
                                        playbackManager.queue.next()
                                        controlsVisible = true
                                    }
                                }
                            },
                            enabled = !nextEpisodeLoading,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        formatTime(if (scrubbing) scrubMs else positionMs),
                        style = TextStyle(color = Color.White, fontSize = 12.sp),
                    )
                    Spacer(Modifier.width(10.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(18.dp)
                            .onSizeChanged { seekWidth = it.width.coerceAtLeast(1) }
                            .pointerInput(durationMs, seekWidth) {
                                detectTapGestures { offset: Offset ->
                                    if (durationMs > 0) {
                                        val fraction = (offset.x / seekWidth.toFloat()).coerceIn(0f, 1f)
                                        val target = (durationMs * fraction).toLong()
                                        positionMs = target
                                        playbackManager.state.seek(target.milliseconds)
                                        controlsVisible = true
                                    }
                                }
                            }
                            .pointerInput(durationMs, seekWidth) {
                                detectHorizontalDragGestures(
                                    onDragStart = { offset ->
                                        if (durationMs > 0) {
                                            scrubbing = true
                                            playbackManager.state.setScrubbing(true)
                                            val fraction = (offset.x / seekWidth.toFloat()).coerceIn(0f, 1f)
                                            scrubMs = (durationMs * fraction).toLong()
                                            controlsVisible = true
                                        }
                                    },
                                    onHorizontalDrag = { change, _ ->
                                        if (durationMs > 0) {
                                            val fraction = (change.position.x / seekWidth.toFloat()).coerceIn(0f, 1f)
                                            scrubMs = (durationMs * fraction).toLong()
                                        }
                                    },
                                    onDragEnd = {
                                        if (scrubbing) {
                                            positionMs = scrubMs
                                            playbackManager.state.seek(scrubMs.milliseconds)
                                            playbackManager.state.setScrubbing(false)
                                            scrubbing = false
                                            controlsVisible = true
                                        }
                                    },
                                    onDragCancel = {
                                        playbackManager.state.setScrubbing(false)
                                        scrubbing = false
                                    },
                                )
                            },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Box(
                            Modifier.fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0x55FFFFFF))
                        )
                        val visiblePosition = if (scrubbing) scrubMs else positionMs
                        val fraction = if (durationMs > 0) visiblePosition.toFloat() / durationMs.toFloat() else 0f
                        Box(
                            Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f))
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF8BD8FF))
                        )
                    }

                    Spacer(Modifier.width(10.dp))
                    BasicText(
                        formatTime(durationMs),
                        style = TextStyle(color = Color.White, fontSize = 12.sp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerButton(
    label: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                when {
                    !enabled -> Color(0x1FFFFFFF)
                    primary -> Color.White
                    else -> Color(0x33FFFFFF)
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (primary) 24.dp else 17.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label,
            style = TextStyle(
                color = when {
                    !enabled -> Color(0xFF87919A)
                    primary -> Color.Black
                    else -> Color.White
                },
                fontSize = if (primary) 18.sp else 14.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun MobileCloseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0x99000000))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 10.dp),
    ) {
        BasicText("✕", style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold))
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
