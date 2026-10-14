package org.jellyfin.androidtv

import android.app.Application
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.acra.ACRA
import org.jellyfin.androidtv.data.eventhandling.SocketHandler
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.integration.LeanbackChannelWorker
import org.jellyfin.androidtv.telemetry.TelemetryService
import org.jellyfin.androidtv.ui.playback.MediaSessionChangeHandler
import org.jellyfin.androidtv.ui.playback.PlaybackControllerContainer
import org.jellyfin.androidtv.util.isTvDevice
import org.koin.android.ext.android.inject
import java.util.concurrent.TimeUnit
import org.jellyfin.playback.core.PlaybackManager as CorePlaybackManager

@Suppress("unused")
class JellyfinApplication : Application() {
	override fun onCreate() {
		super.onCreate()

		// Don't run in ACRA service
		if (ACRA.isACRASenderServiceProcess()) return

		val notificationsRepository by inject<NotificationsRepository>()
		notificationsRepository.addDefaultNotifications()

		val sessionRepository by inject<SessionRepository>()
		val controllerContainer by inject<PlaybackControllerContainer>()
		val playbackManager by inject<CorePlaybackManager>()
		val mediaSessionChangeHandler = MediaSessionChangeHandler {
			controllerContainer.playbackController?.endPlayback(true)
			playbackManager.state.stop()
		}
		ProcessLifecycleOwner.get().lifecycleScope.launch {
			sessionRepository.state.collect(mediaSessionChangeHandler::onState)
		}
		ProcessLifecycleOwner.get().lifecycleScope.launch {
			sessionRepository.currentSession.collect(mediaSessionChangeHandler::onSession)
		}
	}

	/**
	 * Called from the StartupActivity when the user session is started.
	 */
	suspend fun onSessionStart() = withContext(Dispatchers.IO) {
		val workManager by inject<WorkManager>()
		val socketListener by inject<SocketHandler>()

		// Update background worker only on Android TV devices.
		launch {
			workManager.cancelAllWork().await()

			if (isTvDevice()) {
				workManager.enqueueUniquePeriodicWork(
					LeanbackChannelWorker.PERIODIC_UPDATE_REQUEST_NAME,
					ExistingPeriodicWorkPolicy.UPDATE,
					PeriodicWorkRequestBuilder<LeanbackChannelWorker>(1, TimeUnit.HOURS)
						.setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
						.build()
				).await()
			}
		}

		// Update WebSockets
		launch { socketListener.updateSession() }
	}

	override fun attachBaseContext(base: Context?) {
		super.attachBaseContext(base)

		TelemetryService.init(this)
	}
}
