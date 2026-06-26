package com.payassistai.app.data

import java.security.MessageDigest
import java.security.SecureRandom


object PasswordUtils {
    private const val SALT_LENGTH_BYTES = 16

    fun hash(rawPassword: String): String {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val digest = sha256(salt + rawPassword.toByteArray(Charsets.UTF_8))
        return "${salt.toHex()}:${digest.toHex()}"
    }

    fun matches(rawPassword: String, storedHash: String): Boolean {
        val parts = storedHash.split(":")
        if (parts.size != 2) return false // not our salted format
        val salt = parts[0].hexToBytes() ?: return false
        val expectedHash = parts[1]
        val actualHash = sha256(salt + rawPassword.toByteArray(Charsets.UTF_8)).toHex()
        return constantTimeEquals(actualHash, expectedHash)
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray? {
        if (length % 2 != 0) return null
        return try {
            ByteArray(length / 2) { i ->
                ((Character.digit(this[i * 2], 16) shl 4) + Character.digit(this[i * 2 + 1], 16)).toByte()
            }
        } catch (e: Exception) {
            null
        }
    }

    // Avoids short-circuiting on the first differing character, which
    // matters for password/hash comparisons (timing side-channel).
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}