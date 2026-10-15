package com.example.pocketquestbudgeting.data

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object LegacyPassword {
    private const val ITERATIONS = 1_400_000

    fun newSalt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()

    fun hash(password: String, salt: String): String {
        // I used PBKDF2's API-24-compatible variant with a random salt (Google, 2026ai; OWASP Foundation, n.d.).
        val chars = password.toCharArray()
        val spec = PBEKeySpec(chars, salt.hexBytes(), ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded.toHex()
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
    }

    fun matches(password: String, salt: String?, expected: String?): Boolean {
        if (salt == null || expected == null || password.isEmpty()) return false
        return MessageDigest.isEqual(hash(password, salt).hexBytes(), expected.hexBytes())
    }
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }

internal fun String.hexBytes(): ByteArray {
    require(length % 2 == 0)
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
