package org.jellyfin.androidtv.ui.playback

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Adds the current Jellyfin user's credential to Media3 requests. This is a network interceptor so
 * redirects are checked again before OkHttp sends a follow-up request.
 */
internal class JellyfinMediaAuthInterceptor(
	trustedBaseUrl: String,
	private val currentAuthorization: () -> String?,
) : Interceptor {
	private val trustedOrigin = trustedBaseUrl.toHttpUrl()

	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()
		val url = request.url
		if (url.scheme != trustedOrigin.scheme || url.host != trustedOrigin.host || url.port != trustedOrigin.port) {
			throw IOException("Media request left the trusted Jellyfin origin")
		}

		// A user credential must never be carried in a URL, including a redirect or playlist segment.
		if (url.queryParameterNames.any { it.lowercase() in URL_CREDENTIAL_PARAMETERS }) {
			throw IOException("Media URL contains a credential parameter")
		}

		val authorization = currentAuthorization()
			?: throw IOException("No active Jellyfin user session for media request")
		return chain.proceed(request.newBuilder().header("Authorization", authorization).build())
	}

	private companion object {
		val URL_CREDENTIAL_PARAMETERS = setOf("api_key", "access_token", "token", "x-emby-token")
	}
}
