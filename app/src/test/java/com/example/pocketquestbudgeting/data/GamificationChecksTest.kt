package com.example.pocketquestbudgeting.data

import org.junit.Assert.*
import org.junit.Test

class GamificationChecksTest {
    private fun item() = CustomWorthItemEntity(userId = 1, name = "Bicycle", valueCents = Long.MAX_VALUE,
        category = "CUSTOM", cloudId = "11111111-1111-4111-8111-111111111111")

    @Test fun validItemKeepsExactCentsAndBusinessIdentity() {
        val row = item()
        GamificationMapping.requireItem(row)
        assertEquals(Long.MAX_VALUE, GamificationMapping.fields(row)["valueCents"])
        assertEquals(row.copy(id = 7), GamificationMapping.item(1, row.cloudId!!, GamificationMapping.fields(row), id = 7))
    }

    @Test fun invalidItemsAreRejectedBeforeQueueing() {
        for (row in listOf(item().copy(name = " "), item().copy(name = " x"), item().copy(name = "x".repeat(41)),
            item().copy(valueCents = 0), item().copy(valueCents = -1), item().copy(category = "FOOD"),
            item().copy(userId = 0), item().copy(cloudId = "42"))) {
            try { GamificationMapping.requireItem(row); fail("Invalid item accepted") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun activityIsImmutableAndRequiresARealMatchingDate() {
        assertTrue(SyncMapping.immutable(GamificationMapping.ACTIVITY))
        assertFalse(SyncMapping.immutable(GamificationMapping.ITEMS))
        SyncMapping.checkIdentity(GamificationMapping.ACTIVITY, "2024-02-29", mapOf("date" to "2024-02-29"))
        for (date in listOf("2025-02-29", "2026-13-01", "not-a-date")) {
            try { GamificationMapping.validate(GamificationMapping.ACTIVITY, mapOf("date" to date)); fail("Bad date accepted") }
            catch (_: IllegalArgumentException) { }
        }
        try { SyncMapping.checkIdentity(GamificationMapping.ACTIVITY, "2026-10-20", mapOf("date" to "2026-10-19")); fail("Mismatched date accepted") }
        catch (_: IllegalArgumentException) { }
    }
}
