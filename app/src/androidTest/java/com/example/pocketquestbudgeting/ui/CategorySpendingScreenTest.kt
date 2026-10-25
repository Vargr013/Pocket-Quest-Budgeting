package com.example.pocketquestbudgeting.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pocketquestbudgeting.data.*
import com.example.pocketquestbudgeting.ui.theme.PocketQuestTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CategorySpendingScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun periodSelectionKeepsBudgetUsageSeparateFromSpendingShare(): Unit = runBlocking {
        val db = DatabaseProvider.get(ApplicationProvider.getApplicationContext<Context>())
        val owner = db.userDao().insert(UserEntity(username = "Summary${UUID.randomUUID().toString().take(8)}"))
        val groceries = db.categoryDao().insert(CategoryEntity(userId = owner, name = "Groceries", maxMonthlyBudget = 100000))
        val transport = db.categoryDao().insert(CategoryEntity(userId = owner, name = "Transport"))
        val previous = previousMonthRange()
        var showing by mutableStateOf(true)
        try {
            for ((category, amount, date) in listOf(Triple(groceries, 50000L, todayIsoDate()),
                Triple(groceries, 150000L, previous.start), Triple(transport, 200000L, todayIsoDate()))) {
                db.expenseDao().insert(ExpenseEntity(userId = owner, categoryId = category, amount = amount,
                    date = date, startTime = "10:00", endTime = "10:15", description = "Summary fixture"))
            }
            compose.setContent { PocketQuestTheme { if (showing) CategorySpendingSummaryScreen(owner, {}) } }
            showRow("Groceries, R2000.00, 50.0% of spending in this period")
            selectPeriod("Current month")
            showRow("Groceries, R500.00 of R1000.00, 50.0% of monthly limit")
            showRow("Transport, R2000.00, 80.0% of spending in this period")
            saveScreenshot("integration-summary-month")
            selectPeriod("Current week")
            showRow("Groceries, R500.00, 20.0% of spending in this period")
            selectPeriod("All dates")
            showRow("Groceries, R2000.00, 50.0% of spending in this period")
        } finally {
            compose.runOnIdle { showing = false }; compose.waitForIdle()
            val sql = db.openHelper.writableDatabase
            sql.execSQL("DELETE FROM expenses WHERE userId = ?", arrayOf(owner))
            sql.execSQL("DELETE FROM categories WHERE userId = ?", arrayOf(owner))
            sql.execSQL("DELETE FROM users WHERE id = ?", arrayOf(owner))
        }
    }

    @Test fun periodLabelsRemainReadableOnANarrowScreenWithLargeText(): Unit = runBlocking {
        val db = DatabaseProvider.get(ApplicationProvider.getApplicationContext<Context>())
        val owner = db.userDao().insert(UserEntity(username = "Labels${UUID.randomUUID().toString().take(8)}"))
        var showing by mutableStateOf(true)
        try {
            compose.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                    PocketQuestTheme {
                        if (showing) Box(Modifier.requiredSize(320.dp, 640.dp)) { CategorySpendingSummaryScreen(owner, {}) }
                    }
                }
            }
            for (label in listOf("All dates", "Current week", "Current month")) {
                compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label) and hasClickAction())
                compose.onNode(hasText(label) and hasClickAction()).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                saveScreenshot("integration-summary-large-text")
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNode(hasText(label) and hasAnyAncestor(hasClickAction()), useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
                val layout = layouts.single()
                assertFalse("Clipped period label height: $label", layout.didOverflowHeight)
                for (line in 0 until layout.lineCount) {
                    assertFalse("Ellipsized period label: $label", layout.isLineEllipsized(line))
                    // Paragraph widths can round by a fraction of a physical pixel.
                    assertTrue("Clipped period label width: $label", layout.getLineRight(line) <= layout.size.width + 1f)
                }
            }
            saveScreenshot("integration-summary-large-text")
        } finally {
            compose.runOnIdle { showing = false }; compose.waitForIdle()
            db.openHelper.writableDatabase.execSQL("DELETE FROM users WHERE id = ?", arrayOf(owner))
        }
    }

    private fun selectPeriod(label: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label) and hasClickAction())
        compose.onNode(hasText(label) and hasClickAction()).performClick()
    }

    private fun showRow(description: String) {
        compose.waitUntil(10000) {
            runCatching {
                compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription(description))
                compose.onNodeWithContentDescription(description).assertIsDisplayed()
            }.isSuccess
        }
    }

    private fun saveScreenshot(name: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
