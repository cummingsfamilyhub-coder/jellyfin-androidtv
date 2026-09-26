package org.jellyfin.androidtv.ui.mobile.cast

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.SessionManagerListener
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI

class VesperCastManager(
    private val activity: FragmentActivity,
    private val api: ApiClient,
    private val sessionRepository: SessionRepository,
    private val serverRepository: ServerRepository,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var receiverReady = false
    private var pendingItem: BaseItemDto? = null
    private var pendingStartPositionTicks: Long? = null
    private var lastRequestedItem: BaseItemDto? = null
    private var lastRequestedStartPositionTicks: Long? = null
    private var compatibilityRetryCount = 0

    var onRemotePlaybackStarted: (() -> Unit)? = null

    private val castContext: CastContext by lazy {
        CastContext.getSharedInstance(activity)
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            receiverReady = false
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            receiverReady = false
            registerMessageListener(session)
            warmReceiver(session)
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            receiverReady = false
            pendingItem = null
            showToast("Couldn't start the Chromecast session.")
        }

        override fun onSessionEnding(session: CastSession) = Unit

        override fun onSessionEnded(session: CastSession, error: Int) {
            receiverReady = false
            pendingItem = null
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) {
            receiverReady = false
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            receiverReady = false
            registerMessageListener(session)
            warmReceiver(session)
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            receiverReady = false
            pendingItem = null
            showToast("Couldn't reconnect to the Chromecast.")
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            receiverReady = false
        }
    }

    init {
        castContext.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
        castContext.sessionManager.currentCastSession
            ?.takeIf { it.isConnected }
            ?.let {
                registerMessageListener(it)
                warmReceiver(it)
            }
    }

    fun updateReceiverApplicationId(receiverId: String?) {
        val value = receiverId?.takeIf { it.isNotBlank() } ?: return
        VesperCastOptionsProvider.setReceiverApplicationId(value)
        runCatching { castContext.setReceiverApplicationId(value) }
    }

    /**
     * Attempts to play on Cast when a route is connected or still connecting.
     *
     * Returning true means Cast owns this play request, so callers must not
     * silently fall back to local playback while the receiver is starting.
     */
    fun play(item: BaseItemDto, startPositionTicks: Long? = null): Boolean {
        lastRequestedItem = item
        lastRequestedStartPositionTicks = startPositionTicks
        compatibilityRetryCount = 0

        val castSession = castContext.sessionManager.currentCastSession
        val castIsStarting = castContext.castState == CastState.CONNECTING

        if (castSession == null && !castIsStarting) {
            return false
        }

        if (castSession?.isConnected != true) {
            pendingItem = item
            pendingStartPositionTicks = startPositionTicks
            return true
        }

        if (!receiverReady) {
            pendingItem = item
            pendingStartPositionTicks = startPositionTicks
            warmReceiver(castSession)
            return true
        }

        return sendPlay(castSession, item, startPositionTicks)
    }

    fun prepareHandoff(item: BaseItemDto, startPositionTicks: Long) {
        lastRequestedItem = item
        lastRequestedStartPositionTicks = startPositionTicks
        compatibilityRetryCount = 0
        pendingItem = item
        pendingStartPositionTicks = startPositionTicks

        castContext.sessionManager.currentCastSession
            ?.takeIf { it.isConnected }
            ?.let { session ->
                if (receiverReady) {
                    pendingItem = null
                    pendingStartPositionTicks = null
                    sendPlay(session, item, startPositionTicks)
                } else {
                    warmReceiver(session)
                }
            }
    }

    fun sendCommand(command: String, options: JSONObject = JSONObject()): Boolean {
        val castSession = castContext.sessionManager.currentCastSession
            ?.takeIf { it.isConnected }
            ?: return false
        val currentSession = sessionRepository.currentSession.value
        val currentServer = serverRepository.currentServer.value

        if (currentSession == null || currentServer == null) {
            showToast("Vesper lost its Jellyfin session. Reconnect before casting.")
            return true
        }

        val message = createEnvelope(
            castSession = castSession,
            command = command,
            options = options,
            userId = currentSession.userId.toString().replace("-", ""),
            accessToken = currentSession.accessToken,
            serverAddress = serverAddressForCast(currentServer),
            serverId = currentServer.id.toString().replace("-", ""),
            serverVersion = currentServer.version.orEmpty(),
        )

        sendWithRetry(
            session = castSession,
            message = message.toString(),
            label = command,
            attemptsLeft = 2,
        )
        return true
    }

    private fun warmReceiver(session: CastSession) {
        sendIdentify(session)

        // A Cast session can report connected slightly before the Jellyfin
        // receiver has opened its custom namespace. Give it a short warm-up,
        // then honour any play request that arrived while connecting.
        handler.postDelayed({
            if (!session.isConnected) return@postDelayed

            receiverReady = true
            val item = pendingItem ?: return@postDelayed
            val startPositionTicks = pendingStartPositionTicks
            pendingItem = null
            pendingStartPositionTicks = null
            sendPlay(session, item, startPositionTicks)
        }, RECEIVER_WARMUP_MS)
    }

    private fun sendPlay(
        castSession: CastSession,
        item: BaseItemDto,
        startPositionTicks: Long? = null,
    ): Boolean {
        val currentSession = sessionRepository.currentSession.value
        val currentServer = serverRepository.currentServer.value

        if (currentSession == null || currentServer == null) {
            showToast("Vesper lost its Jellyfin session. Reconnect before casting.")
            return true
        }

        val serverId = item.serverId
            ?.takeIf { it.isNotBlank() }
            ?.replace("-", "")
            ?: currentServer.id.toString().replace("-", "")

        val itemStub = JSONObject()
            .put("Id", item.id.toString().replace("-", ""))
            .put("ServerId", serverId)
            .put("Name", item.name.orEmpty())
            .put("Type", item.type.serialName)
            .put("MediaType", "Video")
            .put("IsFolder", item.isFolder == true)

        val options = JSONObject()
            .put("items", JSONArray().put(itemStub))

        val resumeTicks = startPositionTicks ?: item.userData?.playbackPositionTicks ?: 0L
        if (resumeTicks > 0L) {
            options.put("startPositionTicks", resumeTicks)
        }

        val message = createEnvelope(
            castSession = castSession,
            command = "PlayNow",
            options = options,
            userId = currentSession.userId.toString().replace("-", ""),
            accessToken = currentSession.accessToken,
            serverAddress = serverAddressForCast(currentServer),
            serverId = currentServer.id.toString().replace("-", ""),
            serverVersion = currentServer.version.orEmpty(),
        )

        sendWithRetry(
            session = castSession,
            message = message.toString(),
            label = item.name ?: "this title",
            attemptsLeft = 3,
        )
        return true
    }

    private fun sendIdentify(session: CastSession) {
        val currentSession = sessionRepository.currentSession.value ?: return
        val currentServer = serverRepository.currentServer.value ?: return

        val message = createEnvelope(
            castSession = session,
            command = "Identify",
            options = JSONObject(),
            userId = currentSession.userId.toString().replace("-", ""),
            accessToken = currentSession.accessToken,
            serverAddress = serverAddressForCast(currentServer),
            serverId = currentServer.id.toString().replace("-", ""),
            serverVersion = currentServer.version.orEmpty(),
        )

        session.sendMessage(NAMESPACE, message.toString()).setResultCallback { result ->
            if (!result.isSuccess) {
                receiverReady = false
            }
        }
    }

    private fun registerMessageListener(session: CastSession) {
        runCatching {
            session.setMessageReceivedCallbacks(NAMESPACE) { _, _, rawMessage ->
                val message = runCatching { JSONObject(rawMessage) }.getOrNull()
                    ?: return@setMessageReceivedCallbacks

                when (message.optString("type")) {
                    "connectionerror" -> {
                        receiverReady = false
                        showToast("Chromecast couldn't reach your Jellyfin server.")
                    }
                    "playbackerror" -> {
                        val detail = receiverMessage(message)
                        if (
                            detail.equals("NoCompatibleStream", ignoreCase = true) &&
                            compatibilityRetryCount < MAX_COMPATIBILITY_RETRIES
                        ) {
                            compatibilityRetryCount++
                            receiverReady = false
                            val item = lastRequestedItem
                            if (item != null) {
                                handler.postDelayed({
                                    val activeSession = castContext.sessionManager.currentCastSession
                                        ?.takeIf { it.isConnected }
                                        ?: return@postDelayed
                                    receiverReady = true
                                    sendPlay(activeSession, item, lastRequestedStartPositionTicks)
                                }, COMPATIBILITY_RETRY_DELAY_MS)
                            }
                        } else {
                            showToast("Chromecast playback error: $detail")
                        }
                    }
                    "playbackstart" -> onRemotePlaybackStarted?.invoke()
                    "error" -> showToast(
                        "Chromecast error: " + receiverMessage(message)
                    )
                }
            }
        }.onFailure {
            if (it !is IOException) {
                showToast("Couldn't open the Jellyfin Cast message channel.")
            }
        }
    }

    private fun receiverMessage(message: JSONObject): String {
        val direct = message.optString("message")
        if (direct.isNotBlank()) return direct

        val data = message.opt("data")
        return data?.toString()?.takeIf { it.isNotBlank() } ?: "unknown error"
    }

    private fun sendWithRetry(
        session: CastSession,
        message: String,
        label: String,
        attemptsLeft: Int,
    ) {
        if (!session.isConnected) {
            showToast("Chromecast disconnected before $label could start.")
            return
        }

        session.sendMessage(NAMESPACE, message).setResultCallback { result ->
            if (result.isSuccess) {
                return@setResultCallback
            }

            if (attemptsLeft > 1) {
                receiverReady = false
                handler.postDelayed({
                    sendIdentify(session)
                    handler.postDelayed({
                        sendWithRetry(session, message, label, attemptsLeft - 1)
                    }, RETRY_DELAY_MS)
                }, RETRY_DELAY_MS)
            } else {
                showToast("Couldn't send $label to the Jellyfin Cast receiver.")
            }
        }
    }

    private fun serverAddressForCast(server: Server): String {
        val address = server.address
        val host = runCatching { URI(address).host }.getOrNull()
            ?.trim('[', ']')
            ?.lowercase()

        val loopback = host == "localhost" ||
            host == "::1" ||
            host?.startsWith("127.") == true

        if (!loopback) return address

        return serverRepository.discoveredServers.value
            .firstOrNull { discovered ->
                discovered.id == server.id &&
                    discovered.address.isNotBlank() &&
                    discovered.address != address
            }
            ?.address
            ?: address
    }

    private fun showToast(message: String) {
        activity.runOnUiThread {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun createEnvelope(
        castSession: CastSession,
        command: String,
        options: JSONObject,
        userId: String,
        accessToken: String,
        serverAddress: String,
        serverId: String,
        serverVersion: String,
    ): JSONObject = JSONObject()
        .put("options", options)
        .put("command", command)
        .put("userId", userId)
        .put("deviceId", api.deviceInfo.id)
        .put("accessToken", accessToken)
        .put("serverAddress", serverAddress)
        .put("serverId", serverId)
        .put("serverVersion", serverVersion)
        .put("receiverName", castSession.castDevice?.friendlyName.orEmpty())

    fun destroy() {
        onRemotePlaybackStarted = null
        handler.removeCallbacksAndMessages(null)
        runCatching {
            castContext.sessionManager.currentCastSession
                ?.removeMessageReceivedCallbacks(NAMESPACE)
        }
        runCatching {
            castContext.sessionManager.removeSessionManagerListener(sessionListener, CastSession::class.java)
        }
    }

    companion object {
        const val NAMESPACE = "urn:x-cast:com.connectsdk"
        private const val RECEIVER_WARMUP_MS = 2500L
        private const val RETRY_DELAY_MS = 500L
        private const val COMPATIBILITY_RETRY_DELAY_MS = 2200L
        private const val MAX_COMPATIBILITY_RETRIES = 2
    }
}
