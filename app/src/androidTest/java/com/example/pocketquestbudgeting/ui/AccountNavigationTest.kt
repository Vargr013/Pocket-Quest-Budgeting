package com.example.pocketquestbudgeting.ui

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pocketquestbudgeting.data.AccountSession
import com.example.pocketquestbudgeting.data.CategoryEntity
import com.example.pocketquestbudgeting.data.DatabaseProvider
import com.example.pocketquestbudgeting.data.FakeCloudAuth
import com.example.pocketquestbudgeting.data.MemorySessionStore
import com.example.pocketquestbudgeting.data.UserEntity
import com.example.pocketquestbudgeting.ui.theme.PocketQuestTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AccountNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun financialRoutesUsedTheMappedIdAndDiscardedDraftsAcrossSessions() = runBlocking {
        val db = DatabaseProvider.get(ApplicationProvider.getApplicationContext())
        val suffix = UUID.randomUUID().toString().take(8)
        val aliceName = "UiAlice$suffix"
        val bobName = "UiBob$suffix"
        val aliceId = db.userDao().insert(UserEntity(username = aliceName, firebaseUid = "uid-$aliceName"))
        val bobId = db.userDao().insert(UserEntity(username = bobName, firebaseUid = "uid-$bobName"))
        db.categoryDao().insert(CategoryEntity(userId = aliceId, name = "Private A $suffix"))
        db.categoryDao().insert(CategoryEntity(userId = bobId, name = "Private B $suffix"))
        val session = AccountSession(db, FakeCloudAuth(), MemorySessionStore())
        try {
            compose.setContent { PocketQuestTheme { AccountContent(session) } }
            compose.waitUntil(5000) { session.state.value.checked }
            session.login(aliceName, "Synthetic-ui-only!", true).join()
            val first = requireNotNull(session.state.value.account)
            compose.waitUntil(10000) { compose.onAllNodesWithText("Private A $suffix").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Private B $suffix").assertDoesNotExist()
            compose.onNodeWithText("+").performClick()
            compose.onAllNodes(hasSetTextAction())[1].performTextInput("Private draft A $suffix")
            compose.onNodeWithText("Private draft A $suffix").assertExists()

            session.logout()
            session.login(bobName, "Synthetic-ui-only!", true).join()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Private B $suffix").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Private A $suffix").assertDoesNotExist()
            compose.onNodeWithText("Private draft A $suffix").assertDoesNotExist()
            compose.onNodeWithText("+").performClick()
            compose.onNodeWithText("Private draft A $suffix").assertDoesNotExist()

            session.logout()
            session.login(aliceName, "Synthetic-ui-only!", true).join()
            val returned = requireNotNull(session.state.value.account)
            assertNotEquals(first.sessionGeneration, returned.sessionGeneration)
            compose.waitUntil(10000) { compose.onAllNodesWithText("Private A $suffix").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("+").performClick()
            compose.onNodeWithText("Private draft A $suffix").assertDoesNotExist()
        } finally {
            session.close()
            // I removed only the synthetic rows created by this UI test.
            val sql = db.openHelper.writableDatabase
            for (id in listOf(aliceId, bobId)) {
                sql.execSQL("DELETE FROM categories WHERE userId = ?", arrayOf(id))
                sql.execSQL("DELETE FROM users WHERE id = ?", arrayOf(id))
            }
        }
    }
}
