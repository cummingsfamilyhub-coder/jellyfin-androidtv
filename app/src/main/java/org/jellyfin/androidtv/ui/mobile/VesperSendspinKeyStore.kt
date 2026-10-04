package org.jellyfin.androidtv.ui.mobile

import android.content.Context
import android.util.Base64
import io.music_assistant.sendspin.api.SendspinKeyStore

/**
 * Persistent Sendspin identity/trust storage.
 *
 * Values are opaque protocol blobs. They are never committed or logged.
 */
internal class VesperSendspinKeyStore(context: Context) : SendspinKeyStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        "vesper_sendspin_identity",
        Context.MODE_PRIVATE,
    )

    override fun read(key: String): ByteArray? {
        val encoded = preferences.getString(key, null) ?: return null
        return runCatching {
            Base64.decode(encoded, Base64.NO_WRAP)
        }.getOrNull()
    }

    override fun write(key: String, value: ByteArray) {
        preferences.edit()
            .putString(key, Base64.encodeToString(value, Base64.NO_WRAP))
            .apply()
    }

    override fun delete(key: String) {
        preferences.edit().remove(key).apply()
    }
}
