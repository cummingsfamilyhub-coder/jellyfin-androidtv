package org.jellyfin.vesper.sendspin

import android.content.Context
import android.os.Process
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.music_assistant.sendspin.SendspinPlayer
import io.music_assistant.sendspin.api.LocalPlayerConfig
import io.music_assistant.sendspin.api.SendspinDeps
import io.music_assistant.sendspin.api.SendspinKeyStore
import io.music_assistant.sendspin.api.SendspinPlayer as SendspinPlayerApi
import io.music_assistant.sendspin.api.SystemMonotonicClock
import java.io.Closeable
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow

/**
 * Android-owned shell around the official Music Assistant Sendspin engine.
 *
 * Vesper owns platform audio and lifecycle; the vendored MA module owns the
 * encrypted Sendspin session, pairing state, buffering and clock sync.
 */
class VesperSendspinEngine(
    context: Context,
    config: StateFlow<LocalPlayerConfig?>,
    keyStore: SendspinKeyStore,
    online: StateFlow<Boolean>,
    approvePairing: suspend (pairingToken: String) -> Unit,
    scope: CoroutineScope,
) : Closeable {
    private val audioDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(
            {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                runnable.run()
            },
            "VesperSendspinAudio",
        )
    }.asCoroutineDispatcher()

    private val httpClient = HttpClient(OkHttp)

    val player: SendspinPlayerApi = SendspinPlayer(
        config = config,
        deps = SendspinDeps(
            sink = VesperAudioTrackSink(context.applicationContext, SystemMonotonicClock),
            decoders = VesperAndroidDecoderFactory(),
            keyStore = keyStore,
            httpClient = httpClient,
            online = online,
            approvePairing = approvePairing,
            audioDispatcher = audioDispatcher,
        ),
        scope = scope,
    )

    override fun close() {
        httpClient.close()
        audioDispatcher.close()
    }
}
