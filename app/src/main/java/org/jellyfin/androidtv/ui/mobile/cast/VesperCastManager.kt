package org.jellyfin.androidtv.ui.mobile.cast

import androidx.fragment.app.FragmentActivity
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
    private val castContext: CastContext by lazy {
        CastContext.getSharedInstance(activity)
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) = Unit

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            sendIdentify(session)
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) = Unit
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionEnded(session: CastSession, error: Int) = Unit
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            sendIdentify(session)
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
        override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
    }

    init {
        castContext.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
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

        castSession.sendMessage(NAMESPACE, message.toString())
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

        castSession.sendMessage(NAMESPACE, message.toString())
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
        session.sendMessage(NAMESPACE, message.toString())
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
        runCatching {
            castContext.sessionManager.removeSessionManagerListener(sessionListener, CastSession::class.java)
        }
    }

    companion object {
        const val NAMESPACE = "urn:x-cast:com.connectsdk"
    }
}
