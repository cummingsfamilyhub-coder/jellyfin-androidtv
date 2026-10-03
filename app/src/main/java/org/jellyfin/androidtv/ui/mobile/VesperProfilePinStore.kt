package org.jellyfin.androidtv.ui.mobile

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal object VesperProfilePinStore {
    private const val PREFS = "vesper_profile_pins"
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    fun hasPin(context: Context, serverId: UUID, userId: UUID): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.contains(hashKey(serverId, userId)) &&
            prefs.contains(saltKey(serverId, userId))
    }

    fun setPin(context: Context, serverId: UUID, userId: UUID, pin: String) {
        require(pin.length == 4 && pin.all(Char::isDigit)) {
            "Vesper profile PIN must be exactly four digits."
        }

        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val hash = derive(pin, salt)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(saltKey(serverId, userId), encode(salt))
            .putString(hashKey(serverId, userId), encode(hash))
            .apply()
    }

    fun verifyPin(context: Context, serverId: UUID, userId: UUID, pin: String): Boolean {
        if (pin.length != 4 || pin.any { !it.isDigit() }) return false

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val salt = prefs.getString(saltKey(serverId, userId), null)?.let(::decode) ?: return false
        val expected = prefs.getString(hashKey(serverId, userId), null)?.let(::decode) ?: return false
        val actual = derive(pin, salt)

        if (expected.size != actual.size) return false
        var difference = 0
        expected.indices.forEach { index ->
            difference = difference or (expected[index].toInt() xor actual[index].toInt())
        }
        return difference == 0
    }

    fun removePin(context: Context, serverId: UUID, userId: UUID) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(saltKey(serverId, userId))
            .remove(hashKey(serverId, userId))
            .apply()
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray =
        Base64.decode(value, Base64.NO_WRAP)

    private fun prefix(serverId: UUID, userId: UUID) = "${serverId}_${userId}"
    private fun saltKey(serverId: UUID, userId: UUID) = "salt_${prefix(serverId, userId)}"
    private fun hashKey(serverId: UUID, userId: UUID) = "hash_${prefix(serverId, userId)}"
}
