package org.jellyfin.androidtv.ui.mobile

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.music_assistant.sendspin.api.AudioCodec
import io.music_assistant.sendspin.api.Endpoint

class VesperSendspinEngineTests : FunSpec({
    test("maps canonical HTTPS Music Assistant endpoint to authenticated Sendspin proxy") {
        val config = VesperSendspinEngine.createConfig(
            rawMusicAssistantUrl = "https://music.example.test",
            token = "secret-token",
        )

        val endpoint = config.endpoint as Endpoint.WebSocket
        endpoint.url shouldBe "wss://music.example.test/sendspin"
        endpoint.authToken shouldBe "secret-token"
        config.deviceName shouldBe "This Device"
        config.codecPreference.shouldContainExactly(
            AudioCodec.OPUS,
            AudioCodec.FLAC,
            AudioCodec.PCM,
        )
        config.bufferCapacityBytes shouldBe 15_000_000
        config.userDelayMs shouldBe 0
    }

    test("rejects an insecure Music Assistant endpoint for This Device playback") {
        shouldThrow<IllegalArgumentException> {
            VesperSendspinEngine.createConfig(
                rawMusicAssistantUrl = "http://192.168.1.34:8095",
                token = "secret-token",
            )
        }
    }
})
