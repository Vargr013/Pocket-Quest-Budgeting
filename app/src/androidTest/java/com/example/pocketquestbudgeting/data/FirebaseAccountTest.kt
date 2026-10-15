package com.example.pocketquestbudgeting.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.*
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FirebaseAccountTest {
    private lateinit var db: AppDatabase
    private lateinit var auth: FirebaseAuth
    private lateinit var backend: FirebaseCloudAuth
    private lateinit var session: AccountSession

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        auth = sharedAuth
        auth.signOut()
        backend = FirebaseCloudAuth(auth)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        session = AccountSession(db, backend, MemorySessionStore())
    }

    @After fun tearDown() {
        if (::session.isInitialized) session.close()
        if (::db.isInitialized) db.close()
    }

    companion object {
        private lateinit var app: FirebaseApp
        private lateinit var sharedAuth: FirebaseAuth

        @BeforeClass @JvmStatic fun startAuth() {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            // I isolated Auth tests from the live client and configured the emulator once (Google, 2026ah).
            app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
                .setProjectId("demo-pocket-quest")
                .setApplicationId("1:123456789012:android:1234567890abcdef123456")
                .setApiKey("AIzaSyDemoPocketQuestNotARealFirebaseKey000")
                .build(), "slice3-${UUID.randomUUID()}")
            sharedAuth = FirebaseAuth.getInstance(app)
            sharedAuth.useEmulator("10.0.2.2", 9099)
        }

        @AfterClass @JvmStatic fun stopAuth() {
            if (::sharedAuth.isInitialized) sharedAuth.signOut()
            if (::app.isInitialized) app.delete()
        }
    }

    @Test fun registeredReturnedRejectedWrongPasswordAndSwitchedOwners() = runBlocking {
        val aliceName = "A${UUID.randomUUID().toString().take(12)}"
        val bobName = "B${UUID.randomUUID().toString().take(12)}"
        val syntheticPassword = "Synthetic-emulator-only-7311!"
        session.login(aliceName, syntheticPassword, true, register = true).join()
        assertNull(session.state.value.message)
        val alice = requireNotNull(session.state.value.account)
        db.categoryDao().insert(CategoryEntity(userId = alice.localUserId, name = "Synthetic private category"))
        session.logout()
        session.login(aliceName, "Synthetic-incorrect-proof!", true).join()
        assertNull(session.state.value.account)
        assertNotNull(session.state.value.message)
        session.login(aliceName, syntheticPassword, true).join()
        assertEquals(alice.uid, session.state.value.account?.uid)
        assertEquals(alice.localUserId, session.state.value.account?.localUserId)
        session.logout()
        session.login(bobName, syntheticPassword, true, register = true).join()
        val bob = requireNotNull(session.state.value.account)
        assertNotEquals(alice.uid, bob.uid)
        assertNotEquals(alice.localUserId, bob.localUserId)
        assertTrue(db.categoryDao().getForUser(bob.localUserId).isEmpty())
        assertFalse(session.isCurrent(alice))
        assertEquals(backend.cachedIdentity(), backend.validate())
    }

    @Test fun duplicateRegistrationFailedAndCaseSensitiveNamesStayedDistinct() = runBlocking {
        val name = "Case${UUID.randomUUID().toString().take(10)}"
        val password = "Synthetic-emulator-only-7311!"
        val first = backend.register(name, password)
        backend.signOut()
        var rejected = false
        try { backend.register(name, password) } catch (_: FirebaseAuthException) { rejected = true }
        assertTrue("Duplicate registration must be rejected by Auth", rejected)
        val second = backend.register(name.lowercase(), password)
        assertNotEquals(first.uid, second.uid)
        assertEquals(name.lowercase(), second.username)
    }

    @Test fun restoredAValidatedFirebaseUserThroughItsSavedLocalMapping() = runBlocking {
        session.close()
        val identity = backend.register("Restore${UUID.randomUUID().toString().take(10)}", "Synthetic-emulator-only-7311!")
        val id = db.userDao().createCloudUser(identity.uid, identity.username)
        val saved = MemorySessionStore().also { it.save(identity.uid, id, true) }
        session = AccountSession(db, backend, saved)
        session.restore().join()
        assertNull(session.state.value.message)
        assertEquals(id, session.state.value.account?.localUserId)
        assertEquals(identity.uid, session.state.value.account?.uid)
        assertFalse(session.state.value.offline)
        session.logout()
        assertNull(auth.currentUser)
        assertFalse(saved.shouldRestore())
    }
}
