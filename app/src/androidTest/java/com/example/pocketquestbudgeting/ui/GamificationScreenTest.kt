package com.example.pocketquestbudgeting.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pocketquestbudgeting.data.*
import androidx.compose.ui.test.performTextReplacement
import com.example.pocketquestbudgeting.data.CategoryEntity
import com.example.pocketquestbudgeting.data.DatabaseProvider
import com.example.pocketquestbudgeting.data.ExpenseEntity
import com.example.pocketquestbudgeting.data.UserEntity
import com.example.pocketquestbudgeting.ui.theme.PocketQuestTheme
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GamificationScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun expandedGroupsKeepHiddenBadgesAndTheirDetailsReachable(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = DatabaseProvider.get(context)
        val owner = db.userDao().insert(UserEntity(username = "BadgeGroups${UUID.randomUUID().toString().take(8)}"))
        var showing by mutableStateOf(true)
        try {
            compose.setContent { PocketQuestTheme { if (showing) AchievementsScreen(owner, {}) } }
            compose.waitUntil(10000) { compose.onAllNodesWithText("First Steps").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Dedicated Tracker").assertDoesNotExist()
            compose.onAllNodesWithText("View more")[0].performScrollTo().performClick()
            compose.onNodeWithText("Dedicated Tracker").performScrollTo().performClick()
            compose.onNodeWithText("Log or review your expenses on 50 different days.").assertExists()
            compose.onNodeWithText("Close").performClick()
            compose.onNodeWithText("View less").performScrollTo().performClick()
            compose.onNodeWithText("Dedicated Tracker").assertDoesNotExist()
        } finally {
            compose.runOnIdle { showing = false }
            compose.waitForIdle()
            db.openHelper.writableDatabase.execSQL("DELETE FROM users WHERE id = ?", arrayOf(owner))
        }
    }

    @Test fun customItemsSaveAcrossNavigationAndEarnedBadgesOpenTheirDetails() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = DatabaseProvider.get(context)
        val username = "GamificationUI${UUID.randomUUID().toString().take(8)}"
        val owner = db.userDao().createCloudUser("uid-$username", username)
        val session = AccountSession(db, FakeCloudAuth(), MemorySessionStore())
        session.login(username, "Synthetic-ui-only!", false).join()
        requireNotNull(session.state.value.account) { session.state.value.message ?: "Fixture login failed" }
        val cloud = object : SyncCloud {
            override suspend fun send(operation: SyncOperation, stillCurrent: () -> Boolean): SendResult = error("This screen fixture stays local.")
            override suspend fun download(account: SyncAccount, type: String) = emptyList<SyncRecord>()
        }
        val sync = GamificationSync(db, PocketQuestSyncCoordinator(db, cloud, { session.isCurrent(it) }, {}))
        var showAchievements by mutableStateOf(false)
        try {
            val category = db.categoryDao().insert(CategoryEntity(userId = owner, name = "Synthetic food"))
            db.expenseDao().insert(ExpenseEntity(userId = owner, categoryId = category, amount = 100,
                date = "2026-09-10", startTime = "09:00", endTime = "09:30", description = "Synthetic"))
            compose.setContent {
                PocketQuestTheme {
                    if (showAchievements) AchievementsScreen(owner, onBack = { showAchievements = false })
                    else WhatItWorthScreen(owner, onBack = {}, onAchievements = { showAchievements = true }, session = session, sync = sync)
                }
            }
            compose.waitUntil(10000) { compose.onAllNodesWithText("Custom").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Custom").performScrollTo().performClick()
            compose.onNodeWithText("Item name").performScrollTo().performTextInput("Bicycle")
            compose.onNodeWithText("Estimated value (R)").performScrollTo().performTextInput("4500")
            compose.onNodeWithText("Add custom item").performScrollTo().performClick()
            compose.waitUntil(10000) { runBlocking { db.gamificationDao().getCustomItems(owner).isNotEmpty() } }
            assertEquals(450000L, db.gamificationDao().getCustomItems(owner).single().valueCents)
            compose.onNodeWithText("Edit").performScrollTo().performClick()
            compose.onNodeWithText("4500.00").performScrollTo().performTextReplacement("5000")
            compose.onNodeWithText("Save item changes").performScrollTo().performClick()
            compose.waitUntil(10000) { runBlocking { db.gamificationDao().getCustomItems(owner).singleOrNull()?.valueCents == 500000L } }
            compose.onNodeWithText("View all achievements").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("First Steps").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("First Steps").performScrollTo().performClick()
            compose.onNodeWithText("Log your first expense.").assertExists()
            compose.onNodeWithText("Close").performClick()
            db.budgetGoalDao().insert(BudgetGoalEntity(userId = owner, minimumAmount = 0, maximumAmount = 10000,
                startDate = "2026-11-01", endDate = "2026-11-30"))
            compose.waitUntil(10000) { runBlocking { db.earnedAchievementDao().getForUser(owner).any { it.badgeId == "first_budget" } } }
            compose.onNodeWithText("Planner").performScrollTo().performClick()
            compose.onNodeWithText("Earned on", substring = true).assertExists()
            compose.onNodeWithText("Close").performClick()
            compose.onNodeWithText("Back").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Bicycle").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Remove").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("No custom items yet.").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(db.gamificationDao().getCustomItems(owner).isEmpty())
            assertEquals(setOf("first_expense", "first_budget"), db.earnedAchievementDao().getForUser(owner).map { it.badgeId }.toSet())
        } finally {
            session.close()
            // I removed only this test owner's synthetic rows on the dedicated test AVD.
            val sql = db.openHelper.writableDatabase
            for (table in listOf("sync_queue", "sync_records", "earned_achievements", "activity_days", "custom_worth_items", "budget_goals", "expenses", "categories")) {
                sql.execSQL("DELETE FROM $table WHERE userId = ?", arrayOf(owner))
            }
            sql.execSQL("DELETE FROM users WHERE id = ?", arrayOf(owner))
            context.getSharedPreferences("pocket_quest_worth", android.content.Context.MODE_PRIVATE)
                .edit().remove("category_$owner").commit()
        }
    }
}
