package com.example.pocketquestbudgeting.data

import org.junit.Assert.*
import org.junit.Test

class WorthCatalogTest {
    private val coffee = WorthCatalog.item(WorthCatalog.COFFEE)

    @Test fun comparisonRoundedDownAndKeptTheRemainder() {
        val comparison = WorthCatalog.compare(500_00, coffee)
        assertEquals(12L, comparison.count)
        assertEquals(20_00L, comparison.remainderCents)
        assertEquals(1L, WorthCatalog.compare(40_00, coffee).count)
        assertEquals(0L, WorthCatalog.compare(39_99, coffee).count)
    }

    @Test fun zeroAndNegativeSavingsCompareToNothing() {
        for (saved in listOf(0L, -500_00L)) {
            val comparison = WorthCatalog.compare(saved, coffee)
            assertEquals(0L, comparison.count)
            assertEquals(0L, comparison.remainderCents)
        }
    }

    @Test fun customItemsUsedTheirEnteredValue() {
        val bicycle = WorthCatalog.custom(CustomWorthItemEntity(id = 5, userId = 1, name = "Bicycle", valueCents = 4_500_00, category = "CUSTOM"))
        assertEquals("custom-5", bicycle.id)
        assertEquals(WorthCategory.CUSTOM, bicycle.category)
        assertEquals(2L, WorthCatalog.compare(9_000_00, bicycle).count)
        assertEquals("Bicycle", bicycle.label(2))
    }

    @Test fun zeroPricedItemsWereRejected() {
        val free = WorthItem("free", "freebie", "freebies", 0, WorthCategory.CUSTOM)
        try { WorthCatalog.compare(100_00, free); fail("Zero price accepted") } catch (_: IllegalArgumentException) { }
    }

    @Test fun everyBuiltInTypeHadUniquePricedItems() {
        for (category in WorthCategory.entries.filter { it != WorthCategory.CUSTOM })
            assertTrue(category.label, WorthCatalog.forCategory(category).isNotEmpty())
        assertTrue(WorthCatalog.forCategory(WorthCategory.CUSTOM).isEmpty())
        assertEquals(WorthCatalog.items.size, WorthCatalog.items.map { it.id }.toSet().size)
        assertTrue(WorthCatalog.items.all { it.priceCents > 0 })
    }

    @Test fun labelsUsedSingularForOne() {
        assertEquals("coffee", coffee.label(1))
        assertEquals("coffees", coffee.label(0))
        assertEquals("coffees", coffee.label(12))
    }

    @Test fun disclaimerExplainedThatPricesAreEstimates() {
        assertTrue(WorthCatalog.PRICE_DISCLAIMER.contains("estimates"))
    }
}
