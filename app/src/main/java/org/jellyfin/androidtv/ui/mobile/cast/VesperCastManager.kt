package org.jellyfin.androidtv.ui.mobile.cast

import androidx.fragment.app.FragmentActivity
import java.io.IOException
import android.widget.Toast
import android.os.Looper
import android.os.Handler
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.json.JSONArray
import org.json.JSONObject

class VesperCastManager(
    private val activity: FragmentActivity,
    private val api: ApiClient,
    private val sessionRepository: SessionRepository,
    private val serverRepository: ServerRepository,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var receiverReady = false

    private val castContext: CastContext by lazy {
        CastContext.getSharedInstance(activity)
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) = Unit

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            receiverReady = false
            registerMessageListener(session)
            sendIdentify(session)
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) = Unit
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionEnded(session: CastSession, error: Int) {
            receiverReady = false
        }
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            receiverReady = false
            registerMessageListener(session)
            sendIdentify(session)
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
        override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
    }

    init {
        castContext.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
        castContext.sessionManager.currentCastSession
            ?.takeIf { it.isConnected }
            ?.let {
                registerMessageListener(it)
                sendIdentify(it)
            }
    }

    fun updateReceiverApplicationId(receiverId: String?) {
        val value = receiverId?.takeIf { it.isNotBlank() } ?: return
        VesperCastOptionsProvider.setReceiverApplicationId(value)
        runCatching { castContext.setReceiverApplicationId(value) }
    }

    fun isConnected(): Boolean =
        castContext.sessionManager.currentCastSession?.isConnected == true

    fun play(item: BaseItemDto): Boolean {
        val castSession = castContext.sessionManager.currentCastSession
            ?.takeIf { it.isConnected }
            ?: return false

        val currentSession = sessionRepository.currentSession.value ?: return false
        val currentServer = serverRepository.currentServer.value ?: return false

        val serverId = item.serverId
            ?.takeIf { it.isNotBlank() }
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

        val resumeTicks = item.userData?.playbackPositionTicks ?: 0L
        if (resumeTicks > 0L) {
            options.put("startPositionTicks", resumeTicks)
        }

        val message = createEnvelope(
            castSession = castSession,
            command = "PlayNow",
            options = options,
            userId = currentSession.userId.toString().replace("-", ""),
            accessToken = currentSession.accessToken,
            serverAddress = currentServer.address,
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

    fun sendCommand(command: String, options: JSONObject = JSONObject()): Boolean {
        val castSession = castContext.sessionManager.currentCastSession
            ?.takeIf { it.isConnected }
            ?: return false
        val currentSession = sessionRepository.currentSession.value ?: return false
        val currentServer = serverRepository.currentServer.value ?: return false

        val message = createEnvelope(
            castSession = castSession,
            command = command,
            options = options,
            userId = currentSession.userId.toString().replace("-", ""),
            accessToken = currentSession.accessToken,
            serverAddress = currentServer.address,
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

    private fun sendIdentify(session: CastSession) {
        val currentSession = sessionRepository.currentSession.value ?: return
        val currentServer = serverRepository.currentServer.value ?: return

        val message = createEnvelope(
            castSession = session,
            command = "Identify",
            options = JSONObject(),
            userId = currentSession.userId.toString().replace("-", ""),
            accessToken = currentSession.accessToken,
            serverAddress = currentServer.address,
            serverId = currentServer.id.toString().replace("-", ""),
            serverVersion = currentServer.version.orEmpty(),
        )
        session.sendMessage(NAMESPACE, message.toString()).setResultCallback { result ->
            receiverReady = result.isSuccess
        }
    }

    private fun registerMessageListener(session: CastSession) {
        runCatching {
            session.setMessageReceivedCallbacks(NAMESPACE) { _, _, rawMessage ->
                val message = runCatching { JSONObject(rawMessage) }.getOrNull() ?: return@setMessageReceivedCallbacks
                when (message.optString("type")) {
                    "connectionerror" -> showToast("Chromecast couldn't reach your Jellyfin server.")
                    "playbackerror" -> showToast(
                        "Chromecast playback error: " + message.optString("message", "unknown error")
                    )
                    "error" -> showToast(
                        "Chromecast error: " + message.optString("message", "unknown error")
                    )
                }
            }
        }.onFailure {
            if (it !is IOException) {
                showToast("Couldn't open the Jellyfin Cast message channel.")
            }
        }
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
                receiverReady = true
            } else if (attemptsLeft > 1) {
                handler.postDelayed({
                    if (!receiverReady) sendIdentify(session)
                    handler.postDelayed({
                        sendWithRetry(session, message, label, attemptsLeft - 1)
                    }, 350)
                }, 350)
            } else {
                showToast("Couldn't send $label to the Jellyfin Cast receiver.")
            }
        }
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
    }
}
