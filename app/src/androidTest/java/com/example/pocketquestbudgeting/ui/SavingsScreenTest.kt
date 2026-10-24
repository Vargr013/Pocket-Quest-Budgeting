package com.example.pocketquestbudgeting.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pocketquestbudgeting.data.*
import com.example.pocketquestbudgeting.ui.theme.PocketQuestTheme
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun createRestoreUpdateAndRemoveKeepOneOpeningAndTheRetainedHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = DatabaseProvider.get(context)
        val username = "SavingsUI${UUID.randomUUID().toString().take(8)}"
        val owner = db.userDao().createCloudUser("uid-$username", username)
        val session = AccountSession(db, FakeCloudAuth(), MemorySessionStore())
        session.login(username, "Synthetic-ui-only!", false).join()
        val account = requireNotNull(session.state.value.account)
        assertEquals(owner, account.localUserId)
        val cloud = object : SyncCloud {
            override suspend fun send(operation: SyncOperation, stillCurrent: () -> Boolean): SendResult = error("This screen fixture stays local.")
            override suspend fun download(account: SyncAccount, type: String) = emptyList<SyncRecord>()
        }
        val sync = SavingsSync(db, PocketQuestSyncCoordinator(db, cloud, { session.isCurrent(it) }, {}))
        var route by mutableIntStateOf(0)
        var created = 0
        var updated = 0
        val restore = StateRestorationTester(compose)
        try {
            restore.setContent {
                PocketQuestTheme {
                    when (route) {
                        0 -> AddSavingGoalScreen(owner, onBack = { created++; if (created == 2) route = 1 }, session = session, sync = sync)
                        1 -> UpdateSavingGoalScreen(owner, onBack = { updated++; if (updated == 2) route = 2 }, session = session, sync = sync)
                        2 -> RemoveSavingGoalScreen(owner, onBack = { route = 3 }, session = session, sync = sync)
                        else -> Text("Finished saving goal")
                    }
                }
            }
            compose.onAllNodes(hasSetTextAction())[0].performScrollTo().performTextInput("Holiday")
            compose.onAllNodes(hasSetTextAction())[1].performScrollTo().performTextInput("1000")
            compose.onAllNodes(hasSetTextAction())[2].performScrollTo().performTextInput("250")
            compose.onAllNodes(hasSetTextAction())[4].performScrollTo().performTextInput("29/02/2028")
            restore.emulateSavedInstanceStateRestore()
            compose.onNodeWithText("＋  Add Saving Goal").performScrollTo().performClick()
            compose.waitUntil(10000) { created == 1 }
            restore.emulateSavedInstanceStateRestore()
            compose.onNodeWithText("＋  Add Saving Goal").performScrollTo().performClick()
            compose.waitUntil(10000) { route == 1 }
            var goal = db.savingsDao().getForUser(owner).single()
            assertEquals(25000L, db.savingsDao().savedCents(owner, goal.id))
            assertEquals("2028-02-29", goal.targetDate)
            compose.onNodeWithText("Select a saving goal").performScrollTo().performClick()
            compose.onNodeWithText("Holiday — R1 000,00 target").performClick()
            compose.onAllNodes(hasSetTextAction())[0].performScrollTo().performTextReplacement("Holiday fund")
            compose.onAllNodes(hasSetTextAction())[1].performScrollTo().performTextReplacement("2000")
            compose.onAllNodes(hasSetTextAction())[2].performScrollTo().performTextInput("100")
            restore.emulateSavedInstanceStateRestore()
            compose.onNodeWithText("＋  Update Saving Goal").performScrollTo().performClick()
            compose.waitUntil(10000) { updated == 1 }
            restore.emulateSavedInstanceStateRestore()
            compose.onNodeWithText("＋  Update Saving Goal").performScrollTo().performClick()
            compose.waitUntil(10000) { route == 2 }
            goal = db.savingsDao().getForUser(owner).single()
            assertEquals("Holiday fund", goal.name)
            assertEquals(35000L, db.savingsDao().savedCents(owner, goal.id))
            assertEquals(2, db.savingsDao().contributions(owner, goal.id).size)
            compose.onNodeWithText("Select a saving goal").performScrollTo().performClick()
            compose.onNodeWithText("Holiday fund — R2 000,00 target").performClick()
            compose.onNodeWithText("＋  Remove Saving Goal").performScrollTo().performClick()
            compose.onNodeWithText("This hides the goal. Its saved amounts and history are kept.").assertExists()
            compose.onNodeWithText("Remove goal").performClick()
            compose.waitUntil(10000) { route == 3 }
            assertTrue(db.savingsDao().getForUser(owner).isEmpty())
            assertTrue(db.savingsDao().getForUser(owner, true).single().isDeleted)
            assertEquals(35000L, db.savingsDao().savedCents(owner, goal.id))
        } finally {
            session.close()
            val sql = db.openHelper.writableDatabase
            for (table in listOf("sync_queue", "sync_records", "savings_contributions", "savings_goals")) {
                sql.execSQL("DELETE FROM $table WHERE userId = ?", arrayOf(owner))
            }
            sql.execSQL("DELETE FROM users WHERE id = ?", arrayOf(owner))
        }
    }
}
