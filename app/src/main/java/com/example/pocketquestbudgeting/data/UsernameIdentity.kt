package com.example.pocketquestbudgeting.data

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

object UsernameIdentity {
    const val ALIAS_DOMAIN = "pocket-quest-c7127.firebaseapp.com"

    fun canonical(username: String): String {
        val name = username.trim()
        require(name.isNotBlank()) { "Enter a username." }
        require(!name.any { Character.isISOControl(it) }) { "Username cannot contain control characters." }
        val encoder = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
        val bytes = encoder.encode(java.nio.CharBuffer.wrap(name))
        require(bytes.remaining() in 1..32) { "Username must use 1–32 UTF-8 bytes. Letters with accents may use more than one byte." }
        return name
    }

    fun email(username: String): String {
        // I kept case in the encoded name because Firebase signs in with email addresses (Google, 2026n).
        return canonical(username).toByteArray(Charsets.UTF_8).toHex() + "@" + ALIAS_DOMAIN
    }

    fun fromEmail(email: String): String {
        require(email.endsWith("@$ALIAS_DOMAIN")) { "This account does not use Pocket Quest username login." }
        val localPart = email.substringBefore('@')
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        val name = decoder.decode(ByteBuffer.wrap(localPart.hexBytes())).toString()
        require(email(name) == email) { "Account username is invalid." }
        return name
    }
}
