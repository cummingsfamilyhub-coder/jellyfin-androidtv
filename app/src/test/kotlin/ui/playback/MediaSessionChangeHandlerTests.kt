package org.jellyfin.androidtv.ui.playback

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.auth.repository.Session
import org.jellyfin.androidtv.auth.repository.SessionRepositoryState
import java.util.UUID

class MediaSessionChangeHandlerTests : FunSpec({
	test("switching or logging out stops old playback") {
		var stopCount = 0
		val handler = MediaSessionChangeHandler { stopCount++ }
		val first = Session(UUID.randomUUID(), UUID.randomUUID(), "first-token")
		val second = first.copy(userId = UUID.randomUUID(), accessToken = "second-token")

		handler.onSession(null)
		handler.onSession(first)
		handler.onState(SessionRepositoryState.READY)
		stopCount shouldBe 0

		handler.onState(SessionRepositoryState.SWITCHING_SESSION)
		stopCount shouldBe 1
		handler.onSession(second)
		stopCount shouldBe 2
		handler.onSession(null)
		stopCount shouldBe 3
	}

	test("token rotation stops playback even for the same user") {
		var stopCount = 0
		val handler = MediaSessionChangeHandler { stopCount++ }
		val session = Session(UUID.randomUUID(), UUID.randomUUID(), "old-token")
		handler.onSession(session)
		handler.onSession(session.copy(accessToken = "new-token"))
		stopCount shouldBe 1
	}
})
