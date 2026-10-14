package org.jellyfin.androidtv.ui.playback

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class JellyfinMediaAuthInterceptorTests : FunSpec({
	fun request(url: String) = Request.Builder().url(url).build()
	fun rejects(block: () -> Unit) {
		var rejected = false
		try {
			block()
		} catch (_: IOException) {
			rejected = true
		}
		rejected shouldBe true
	}

	test("uses the current credential for each range or segment request") {
		var credential = "MediaBrowser Token=first-user"
		val interceptor = JellyfinMediaAuthInterceptor("https://jellyfin.example.test") { credential }
		val forwarded = mutableListOf<Request>()
		val response = mockk<Response>()
		val chain = mockk<Interceptor.Chain>()
		every { chain.proceed(any()) } answers {
			forwarded += firstArg<Request>()
			response
		}

		every { chain.request() } returns request("https://jellyfin.example.test/Videos/item/stream")
		interceptor.intercept(chain)
		credential = "MediaBrowser Token=second-user"
		every { chain.request() } returns request("https://jellyfin.example.test/Videos/item/hls/segment.ts")
		interceptor.intercept(chain)

		forwarded.map { it.header("Authorization") } shouldBe listOf(
			"MediaBrowser Token=first-user",
			"MediaBrowser Token=second-user",
		)
	}

	test("rejects a request when the active session is missing") {
		val interceptor = JellyfinMediaAuthInterceptor("https://jellyfin.example.test") { null }
		val chain = mockk<Interceptor.Chain>()
		every { chain.request() } returns request("https://jellyfin.example.test/Videos/item/stream")

		rejects { interceptor.intercept(chain) }
		verify(exactly = 0) { chain.proceed(any()) }
	}

	test("does not send credentials to another origin or a credential-bearing URL") {
		val interceptor = JellyfinMediaAuthInterceptor("https://jellyfin.example.test") {
			"MediaBrowser Token=synthetic-user"
		}
		val chain = mockk<Interceptor.Chain>()

		for (url in listOf(
			"https://other.example.test/Videos/item/stream",
			"http://jellyfin.example.test/Videos/item/stream",
			"https://jellyfin.example.test:8443/Videos/item/stream",
			"https://jellyfin.example.test/Videos/item/stream?api_key=synthetic-user",
		)) {
			every { chain.request() } returns request(url)
			rejects { interceptor.intercept(chain) }
		}
		verify(exactly = 0) { chain.proceed(any()) }
	}
})
