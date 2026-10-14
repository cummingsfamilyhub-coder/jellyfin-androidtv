package org.jellyfin.androidtv.ui.playback

import org.jellyfin.androidtv.auth.repository.Session
import org.jellyfin.androidtv.auth.repository.SessionRepositoryState

/** Stops an old media load before a different Jellyfin user or token can be used. */
internal class MediaSessionChangeHandler(private val stopPlayback: () -> Unit) {
	private var previousSession: Session? = null

	fun onState(state: SessionRepositoryState) {
		if (state == SessionRepositoryState.SWITCHING_SESSION) stopPlayback()
	}

	fun onSession(session: Session?) {
		if (previousSession != null && previousSession != session) stopPlayback()
		previousSession = session
	}
}
