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

    @Test fun customItemsSaveAcrossNavigationAndEarnedBadgesOpenTheirDetails() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = DatabaseProvider.get(context)
        val owner = db.userDao().insert(UserEntity(username = "Gamification UI ${UUID.randomUUID()}"))
        var showAchievements by mutableStateOf(false)
        try {
            val category = db.categoryDao().insert(CategoryEntity(userId = owner, name = "Synthetic food"))
            db.expenseDao().insert(ExpenseEntity(userId = owner, categoryId = category, amount = 100,
                date = "2026-09-10", startTime = "09:00", endTime = "09:30", description = "Synthetic"))
            compose.setContent {
                PocketQuestTheme {
                    if (showAchievements) AchievementsScreen(owner, onBack = { showAchievements = false })
                    else WhatItWorthScreen(owner, onBack = {}, onAchievements = { showAchievements = true })
                }
            }
            compose.waitUntil(10000) { compose.onAllNodesWithText("Custom").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Custom").performScrollTo().performClick()
            compose.onNodeWithText("Item name").performScrollTo().performTextInput("Bicycle")
            compose.onNodeWithText("Estimated value (R)").performScrollTo().performTextInput("4500")
            compose.onNodeWithText("Add custom item").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Bicycle").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(450000L, db.gamificationDao().getCustomItems(owner).single().valueCents)
            compose.onNodeWithText("View all achievements").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("First Steps").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("First Steps").performScrollTo().performClick()
            compose.onNodeWithText("Log your first expense.").assertExists()
            compose.onNodeWithText("Close").performClick()
            compose.onNodeWithText("Back").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Bicycle").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Remove").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("No custom items yet.").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(db.gamificationDao().getCustomItems(owner).isEmpty())
            assertEquals("first_expense", db.earnedAchievementDao().getForUser(owner).single().badgeId)
        } finally {
            // I removed only this test owner's synthetic rows on the dedicated test AVD.
            val sql = db.openHelper.writableDatabase
            for (table in listOf("earned_achievements", "activity_days", "custom_worth_items", "expenses", "categories")) {
                sql.execSQL("DELETE FROM $table WHERE userId = ?", arrayOf(owner))
            }
            sql.execSQL("DELETE FROM users WHERE id = ?", arrayOf(owner))
            context.getSharedPreferences("pocket_quest_worth", android.content.Context.MODE_PRIVATE)
                .edit().remove("category_$owner").commit()
        }
    }
}
