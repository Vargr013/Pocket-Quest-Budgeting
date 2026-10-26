package com.example.pocketquestbudgeting.ui

import android.content.Context
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pocketquestbudgeting.data.*
import com.example.pocketquestbudgeting.ui.theme.PocketQuestTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MonthlyBudgetProgressTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectedMonthUsesItsOwnGoalsAndExpensesAndIncludesTheMaximum(): Unit = runBlocking {
        val db = DatabaseProvider.get(ApplicationProvider.getApplicationContext<Context>())
        val suffix = UUID.randomUUID().toString().take(8)
        val owner = db.userDao().insert(UserEntity(username = "MonthA$suffix"))
        val other = db.userDao().insert(UserEntity(username = "MonthB$suffix"))
        val category = db.categoryDao().insert(CategoryEntity(userId = owner, name = "Month fixture"))
        val otherCategory = db.categoryDao().insert(CategoryEntity(userId = other, name = "Other owner"))
        val current = historyShortcutRange("month")
        val previous = previousMonthRange()
        var showing by mutableStateOf(true)
        try {
            db.budgetGoalDao().insert(BudgetGoalEntity(userId = owner, minimumAmount = 50000,
                maximumAmount = 100000, startDate = current.start, endDate = current.end))
            db.budgetGoalDao().insert(BudgetGoalEntity(userId = owner, minimumAmount = 20000,
                maximumAmount = 80000, startDate = previous.start, endDate = previous.end))
            suspend fun expense(user: Long, categoryId: Long, amount: Long, date: String, deleted: Boolean = false) {
                db.expenseDao().insert(ExpenseEntity(userId = user, categoryId = categoryId, amount = amount,
                    date = date, paymentTime = "10:00", description = "Month fixture", isDeleted = deleted))
            }
            expense(owner, category, 100000, current.start)
            expense(owner, category, 10000, previous.start)
            expense(owner, category, 90000, previous.end)
            expense(owner, category, 999999, previous.start, deleted = true)
            expense(other, otherCategory, 999999, previous.start)
            compose.setContent {
                PocketQuestTheme {
                    if (showing) Column(Modifier.verticalScroll(rememberScrollState())) { MonthlySpendingGoals(owner) }
                }
            }
            compose.waitUntil(10000) { compose.onAllNodesWithText("Within the monthly range.").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Spent: ${formatBudgetAmount(100000)}").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Edit goals").assertExists()
            compose.onNodeWithText("Last month").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Over the monthly maximum.").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Month: ${previous.start.take(7)}").assertExists()
            compose.onNodeWithText("Spent: ${formatBudgetAmount(100000)}").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Maximum: ${formatBudgetAmount(80000)}").assertExists()
            compose.onNodeWithText("125% of monthly max").assertExists()
            compose.onNodeWithText("Completed month").assertExists()
            compose.onNodeWithText("Edit goals").assertDoesNotExist()
            compose.onNodeWithText("This month").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Within the monthly range.").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("100% of monthly max").assertExists()
        } finally {
            compose.runOnIdle { showing = false }
            compose.waitForIdle()
            db.openHelper.writableDatabase.execSQL("DELETE FROM expenses WHERE userId IN (?, ?)", arrayOf(owner, other))
            db.openHelper.writableDatabase.execSQL("DELETE FROM budget_goals WHERE userId = ?", arrayOf(owner))
            db.openHelper.writableDatabase.execSQL("DELETE FROM categories WHERE userId IN (?, ?)", arrayOf(owner, other))
            db.openHelper.writableDatabase.execSQL("DELETE FROM users WHERE id IN (?, ?)", arrayOf(owner, other))
        }
    }
}
