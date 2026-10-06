@file:Suppress("MagicNumber")

package org.jellyfin.androidtv.ui.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import androidx.core.app.ServiceCompat
import io.music_assistant.sendspin.api.PlayerEvent
import io.music_assistant.sendspin.api.PlayerState
import io.music_assistant.sendspin.api.StopCause
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R

/**
 * Owns Vesper's "This Device" player independently from the mobile activity.
 *
 * Music Assistant remains the control plane. The foreground service keeps the
 * encrypted Sendspin connection and Android audio sink alive when the UI is
 * backgrounded, and exposes system media controls through a platform MediaSession.
 */
internal class VesperMusicPlaybackService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var runtime: VesperSendspinRuntime? = null
    private var runtimeStateJob: Job? = null
    private var runtimeEventJob: Job? = null
    private var refreshJob: Job? = null
    private var musicAssistantClient: MusicAssistantClient? = null
    private var configuredBaseUrl: String? = null
    private var configuredToken: String? = null
    private var currentPlayerId: String? = null
    private var currentPlayer: MaPlayer? = null
    private lateinit var mediaSession: MediaSession

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        createNotificationChannel()

        mediaSession = MediaSession(this, "Vesper This Device").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = sendControl(Control.PLAY)
                override fun onPause() = sendControl(Control.PAUSE)
                override fun onSkipToNext() = sendControl(Control.NEXT)
                override fun onSkipToPrevious() = sendControl(Control.PREVIOUS)
            })
            isActive = true
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(null),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        configureFromPreferences()

        when (intent?.action) {
            ACTION_PLAY -> sendControl(Control.PLAY)
            ACTION_PAUSE -> sendControl(Control.PAUSE)
            ACTION_NEXT -> sendControl(Control.NEXT)
            ACTION_PREVIOUS -> sendControl(Control.PREVIOUS)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun configureFromPreferences() {
        val preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
        val baseUrl = preferences.getString("music_assistant_url", "").orEmpty().trim().trimEnd('/')
        val token = preferences.getString("music_assistant_token", "").orEmpty().trim()

        if (baseUrl.isBlank() || token.isBlank()) {
            stopSelf()
            return
        }
        if (baseUrl == configuredBaseUrl && token == configuredToken && runtime != null) return

        closeRuntime()
        configuredBaseUrl = baseUrl
        configuredToken = token
        musicAssistantClient = MusicAssistantClient(baseUrl, token)

        runtime = runCatching {
            VesperSendspinRuntime(
                context = applicationContext,
                musicAssistantBaseUrl = baseUrl,
                token = token,
                scope = serviceScope,
                onPlayerRefresh = {
                    playerRefreshEvents.tryEmit(Unit)
                    serviceScope.launch { refreshPlayer() }
                },
            )
        }.getOrElse {
            updateSession(null, "This Device unavailable")
            return
        }

        runtimeStateJob = serviceScope.launch {
            runtime?.state?.collect { state ->
                currentPlayerId = (state as? PlayerState.Connected)?.playerId ?: currentPlayerId
                when (state) {
                    is PlayerState.Connected -> {
                        playerRefreshEvents.tryEmit(Unit)
                        refreshPlayer()
                    }
                    is PlayerState.Failed -> updateSession(null, "This Device unavailable")
                    is PlayerState.Reconnecting -> updateSession(currentPlayer, "Reconnecting…")
                    else -> Unit
                }
            }
        }

        runtimeEventJob = serviceScope.launch {
            runtime?.events?.collect { event ->
                when (event) {
                    is PlayerEvent.PlaybackStarted,
                    is PlayerEvent.PlaybackStopped -> refreshPlayer()
                    is PlayerEvent.FocusRegained -> Unit
                    is PlayerEvent.ServerRefreshNeeded -> Unit
                    is PlayerEvent.Warning -> Unit
                }
                if (event is PlayerEvent.PlaybackStopped && event.cause == StopCause.FocusLost) {
                    currentPlayerId?.let { playerId ->
                        runCatching { runtime?.pause(playerId) }
                    }
                }
            }
        }

        refreshJob = serviceScope.launch {
            while (isActive) {
                refreshPlayer()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    private suspend fun refreshPlayer() {
        val client = musicAssistantClient ?: return
        val players = runCatching {
            withContext(Dispatchers.IO) { client.loadPlayers() }
        }.getOrNull() ?: return

        val playerId = (runtime?.state?.value as? PlayerState.Connected)?.playerId ?: currentPlayerId
        val player = playerId?.let { id -> players.firstOrNull { it.playerId == id } }
            ?: players.firstOrNull { it.name == DEVICE_NAME }

        if (player != null) currentPlayerId = player.playerId
        val changed = player != currentPlayer
        currentPlayer = player
        updateSession(player, null)
        if (changed) {
            playerRefreshEvents.tryEmit(Unit)
        }
    }

    private fun updateSession(player: MaPlayer?, statusOverride: String?) {
        val state = when (player?.playbackState) {
            "playing" -> PlaybackState.STATE_PLAYING
            "paused" -> PlaybackState.STATE_PAUSED
            else -> PlaybackState.STATE_STOPPED
        }
        val actions =
            PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SKIP_TO_NEXT

        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build()
        )
        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, player?.currentTitle ?: "This Device")
                .putString(
                    MediaMetadata.METADATA_KEY_ARTIST,
                    player?.currentArtist ?: statusOverride ?: "Vesper",
                )
                .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, player?.currentImageUrl)
                .build()
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(player, statusOverride))
    }

    private fun sendControl(control: Control) {
        serviceScope.launch {
            val client = musicAssistantClient ?: return@launch
            val playerId = resolvePlayerId(client) ?: return@launch
            val activeRuntime = runtime ?: return@launch
            runCatching {
                when (control) {
                    Control.PLAY -> activeRuntime.resume(playerId)
                    Control.PAUSE -> activeRuntime.pause(playerId)
                    Control.NEXT -> activeRuntime.next(playerId)
                    Control.PREVIOUS -> activeRuntime.previous(playerId)
                }
            }
            delay(CONTROL_REFRESH_DELAY_MS)
            refreshPlayer()
            playerRefreshEvents.tryEmit(Unit)
        }
    }

    private suspend fun playLocalMediaInternal(itemUri: String, playerId: String) {
        val deadline = System.currentTimeMillis() + LOCAL_PLAY_READY_TIMEOUT_MS
        var lastError: Throwable? = null

        while (System.currentTimeMillis() < deadline) {
            val activeRuntime = runtime
            if (activeRuntime != null && activeRuntime.state.value is PlayerState.Connected) {
                try {
                    activeRuntime.playMedia(itemUri, playerId)
                    return
                } catch (error: Throwable) {
                    lastError = error
                }
            }
            delay(LOCAL_PLAY_RETRY_DELAY_MS)
        }

        throw IllegalStateException(
            lastError?.message ?: "This Device is still connecting to Music Assistant.",
            lastError,
        )
    }

    private suspend fun resolvePlayerId(client: MusicAssistantClient): String? {
        currentPlayerId?.let { return it }
        (runtime?.state?.value as? PlayerState.Connected)?.playerId?.let {
            currentPlayerId = it
            return it
        }
        val discovered = withContext(Dispatchers.IO) {
            runCatching {
                client.loadPlayers().firstOrNull { it.name == DEVICE_NAME }?.playerId
            }.getOrNull()
        }
        currentPlayerId = discovered
        return discovered
    }

    private fun buildNotification(player: MaPlayer?, statusOverride: String? = null): Notification {
        val playing = player?.playbackState == "playing"
        val openApp = PendingIntent.getActivity(
            this,
            REQUEST_OPEN_APP,
            Intent(this, MobileStartupActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val previous = Notification.Action.Builder(
            android.R.drawable.ic_media_previous,
            "Previous",
            controlPendingIntent(ACTION_PREVIOUS, REQUEST_PREVIOUS),
        ).build()
        val playPause = Notification.Action.Builder(
            if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (playing) "Pause" else "Play",
            controlPendingIntent(if (playing) ACTION_PAUSE else ACTION_PLAY, REQUEST_PLAY_PAUSE),
        ).build()
        val next = Notification.Action.Builder(
            android.R.drawable.ic_media_next,
            "Next",
            controlPendingIntent(ACTION_NEXT, REQUEST_NEXT),
        ).build()

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.vesper_icon)
            .setContentTitle(player?.currentTitle ?: "This Device")
            .setContentText(player?.currentArtist ?: statusOverride ?: "Vesper music player ready")
            .setContentIntent(openApp)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(previous)
            .addAction(playPause)
            .addAction(next)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun controlPendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, VesperMusicPlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Vesper playback",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Background playback and media controls for This Device"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun closeRuntime() {
        runtimeStateJob?.cancel()
        runtimeEventJob?.cancel()
        refreshJob?.cancel()
        runtimeStateJob = null
        runtimeEventJob = null
        refreshJob = null
        runtime?.close()
        runtime = null
        currentPlayer = null
        currentPlayerId = null
    }

    override fun onDestroy() {
        if (activeInstance === this) activeInstance = null
        closeRuntime()
        mediaSession.isActive = false
        mediaSession.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private enum class Control {
        PLAY,
        PAUSE,
        NEXT,
        PREVIOUS,
    }

    companion object {
        @Volatile
        private var activeInstance: VesperMusicPlaybackService? = null

        const val ACTION_CONFIGURE = "app.vesper.mobile.music.CONFIGURE"
        private const val ACTION_PLAY = "app.vesper.mobile.music.PLAY"
        private const val ACTION_PAUSE = "app.vesper.mobile.music.PAUSE"
        private const val ACTION_NEXT = "app.vesper.mobile.music.NEXT"
        private const val ACTION_PREVIOUS = "app.vesper.mobile.music.PREVIOUS"

        val playerRefreshEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        suspend fun playLocalMedia(itemUri: String, playerId: String) {
            val deadline = System.currentTimeMillis() + SERVICE_READY_TIMEOUT_MS
            while (activeInstance == null && System.currentTimeMillis() < deadline) {
                delay(SERVICE_READY_RETRY_DELAY_MS)
            }
            val service = activeInstance
                ?: error("Vesper music service is not ready.")
            service.playLocalMediaInternal(itemUri, playerId)
        }

        private const val PREFERENCES_NAME = "vesper"
        private const val DEVICE_NAME = "This Device"
        private const val CHANNEL_ID = "vesper_music_playback"
        private const val NOTIFICATION_ID = 4207
        private const val REFRESH_INTERVAL_MS = 2_000L
        private const val CONTROL_REFRESH_DELAY_MS = 300L
        private const val LOCAL_PLAY_READY_TIMEOUT_MS = 10_000L
        private const val LOCAL_PLAY_RETRY_DELAY_MS = 400L
        private const val SERVICE_READY_TIMEOUT_MS = 3_000L
        private const val SERVICE_READY_RETRY_DELAY_MS = 100L

        private const val REQUEST_OPEN_APP = 1
        private const val REQUEST_PREVIOUS = 2
        private const val REQUEST_PLAY_PAUSE = 3
        private const val REQUEST_NEXT = 4
    }
}
