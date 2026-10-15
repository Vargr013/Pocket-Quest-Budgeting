package com.example.pocketquestbudgeting.data

import org.junit.Assert.*
import org.junit.Test

class UsernameIdentityTest {
    @Test fun keptCaseAndTrimmedOnlyTheOutside() {
        assertEquals("416c6578@${UsernameIdentity.ALIAS_DOMAIN}", UsernameIdentity.email(" Alex "))
        assertNotEquals(UsernameIdentity.email("Alex"), UsernameIdentity.email("alex"))
        assertEquals("A lex", UsernameIdentity.fromEmail(UsernameIdentity.email(" A lex ")))
    }

    @Test fun usedUtf8BytesWithoutNormalisingUnicode() {
        for (name in listOf("é", "e\u0301", "用户", "😀", "é".repeat(16))) {
            assertEquals(name, UsernameIdentity.fromEmail(UsernameIdentity.email(name)))
        }
        assertNotEquals(UsernameIdentity.email("é"), UsernameIdentity.email("e\u0301"))
        assertEquals(64, UsernameIdentity.email("a".repeat(32)).substringBefore('@').length)
    }

    @Test fun rejectedBlankLongMalformedAndForeignIdentities() {
        for (name in listOf(" ", "a".repeat(33), "é".repeat(17), "a\nb", "\uD800")) {
            assertThrows(Exception::class.java) { UsernameIdentity.email(name) }
        }
        for (email in listOf("alex@example.com", "zz@${UsernameIdentity.ALIAS_DOMAIN}", "41A@${UsernameIdentity.ALIAS_DOMAIN}", "ff@${UsernameIdentity.ALIAS_DOMAIN}")) {
            assertThrows(Exception::class.java) { UsernameIdentity.fromEmail(email) }
        }
    }

    @Test fun saltedLegacyProofDidNotAcceptTheWrongPassword() {
        val salt = LegacyPassword.newSalt()
        val otherSalt = LegacyPassword.newSalt()
        assertNotEquals(salt, otherSalt)
        val proof = LegacyPassword.hash("Synthetic-only-pass!", salt)
        assertTrue(LegacyPassword.matches("Synthetic-only-pass!", salt, proof))
        assertFalse(LegacyPassword.matches("wrong-synthetic-pass", salt, proof))
        assertFalse(LegacyPassword.matches("", salt, proof))
        assertFalse(LegacyPassword.matches("Synthetic-only-pass!", null, null))
    }
}
