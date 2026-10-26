package com.example.pocketquestbudgeting.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pocketquestbudgeting.data.CategoryEntity
import com.example.pocketquestbudgeting.data.DatabaseProvider
import com.example.pocketquestbudgeting.data.ExpenseEntity
import com.example.pocketquestbudgeting.data.SavingsGoalEntity
import com.example.pocketquestbudgeting.data.UserEntity
import com.example.pocketquestbudgeting.data.todayIsoDate
import com.example.pocketquestbudgeting.ui.theme.PocketQuestTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MobileUiPassTest {
    @get:Rule val compose = createComposeRule()

    @Test fun menuOptionsStayReachableInAShortWindowWithLargeText() {
        var loggedOut = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                PocketQuestTheme {
                    Box(Modifier.requiredSize(320.dp, 360.dp)) {
                        MenuScreen({}, {}, {}, {}, {}, {}, {}, onLogout = { loggedOut = true })
                    }
                }
            }
        }
        compose.onNodeWithText("Log out").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(loggedOut)
        saveScreenshot("mobile-menu-large-text")
        compose.onNodeWithContentDescription("Back").performScrollTo().assertIsDisplayed()
    }

    @Test fun loginKeyboardMovesToPasswordAndDoneClearsFocusWithoutSubmitting() {
        var submissions = 0
        compose.setContent {
            PocketQuestTheme { LoginScreen(onLogin = { _, _, _ -> submissions++ }) }
        }
        val username = compose.onNode(hasContentDescription("Username") and hasSetTextAction())
        val password = compose.onNode(hasContentDescription("Password") and hasSetTextAction())
        username.performScrollTo().performClick().performTextInput("MobileUser")
        username.assertHeightIsAtLeast(48.dp)
        username.performImeAction()
        password.assertIsFocused().performTextInput("Synthetic-only!")
        password.performImeAction()
        password.assertIsNotFocused()
        assertEquals(0, submissions)
        compose.onNodeWithText("Remember Me").performScrollTo().assertIsOn().performClick().assertIsOff()
        compose.onNodeWithText("Pocket Quest").performScrollTo()
        saveScreenshot("mobile-login")
    }

    @Test fun loginLinksAndButtonStayReachableOnANarrowWindowWithLargeText() {
        var helped = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                PocketQuestTheme {
                    Box(Modifier.requiredSize(320.dp, 560.dp)) {
                        LoginScreen(onLogin = { _, _, _ -> }, onNeedHelp = { helped = true })
                    }
                }
            }
        }
        compose.onNodeWithText("Login").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Need help?").performScrollTo().performClick()
        assertTrue(helped)
        compose.onNodeWithText("Pocket Quest").performScrollTo()
        saveScreenshot("mobile-login-large-text")
    }

    @Test fun dashboardRefreshesLocalRecordsAndClearsThemWhenTheAccountChanges() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = DatabaseProvider.get(context)
        val suffix = UUID.randomUUID().toString().take(8)
        val alice = db.userDao().insert(UserEntity(username = "DashboardUIA$suffix"))
        val bob = db.userDao().insert(UserEntity(username = "DashboardUIB$suffix"))
        val category = db.categoryDao().insert(CategoryEntity(userId = alice, name = "Groceries"))
        var owner by mutableLongStateOf(alice)
        var showing by mutableStateOf(true)
        try {
            compose.setContent {
                PocketQuestTheme {
                    Box(Modifier.requiredSize(320.dp, 640.dp)) {
                        if (showing) DashboardScreen(owner, {}, {}) else Text("Finished")
                    }
                }
            }
            compose.waitUntil(10000) { compose.onAllNodesWithText("No expenses yet.").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Menu").assertHasClickAction()
            compose.onNodeWithContentDescription("Add expense").assertHasClickAction()
            db.expenseDao().insert(ExpenseEntity(userId = alice, categoryId = category, amount = 12345,
                date = todayIsoDate(), startTime = "10:00", endTime = "10:01", description = "Weekly groceries for the family"))
            val goal = db.savingsDao().createGoal(SavingsGoalEntity(userId = alice, cloudId = UUID.randomUUID().toString(),
                name = "Holiday", targetCents = 100000), openingCents = 25001, openingDate = todayIsoDate())
            compose.waitUntil(10000) { compose.onAllNodesWithText("Weekly groceries for the family").fetchSemanticsNodes().isNotEmpty() }
            compose.waitUntil(10000) { compose.onAllNodesWithText("R123.45").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("R123,45").performScrollTo().assertIsDisplayed()
            compose.waitUntil(10000) { compose.onAllNodesWithText("R250,01 of R1 000,00").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Holiday").performScrollTo().assertIsDisplayed()
            saveScreenshot("mobile-dashboard")

            db.savingsDao().removeGoal(alice, goal)
            compose.waitUntil(10000) { compose.onAllNodesWithText("No active saving goals yet.").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Holiday").assertDoesNotExist()
            compose.runOnIdle { owner = bob }
            compose.waitUntil(10000) { compose.onAllNodesWithText("No expenses yet.").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Weekly groceries for the family").assertDoesNotExist()
        } finally {
            compose.runOnIdle { showing = false }
            compose.waitForIdle()
            val sql = db.openHelper.writableDatabase
            for (id in listOf(alice, bob)) {
                for (table in listOf("savings_contributions", "savings_goals", "expenses", "categories")) {
                    sql.execSQL("DELETE FROM $table WHERE userId = ?", arrayOf(id))
                }
                sql.execSQL("DELETE FROM users WHERE id = ?", arrayOf(id))
            }
        }
    }

    private fun saveScreenshot(name: String) {
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
