package com.example.pocketquestbudgeting.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseNetworkException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountSessionTest {
    private lateinit var db: AppDatabase
    private lateinit var auth: FakeCloudAuth
    private lateinit var store: MemorySessionStore
    private lateinit var session: AccountSession

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        auth = FakeCloudAuth()
        store = MemorySessionStore()
        session = newSession()
    }

    private fun newSession() = AccountSession(db, auth, store, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
    @After fun tearDown() { session.close(); db.close() }

    @Test fun newReturningAndSwitchingAccountsKeptLocalOwners() = runBlocking {
        session.login("Alice", "Synthetic-cloud-only!", true, register = true).join()
        val alice = requireNotNull(session.state.value.account)
        db.categoryDao().insert(CategoryEntity(userId = alice.localUserId, name = "Alice category"))
        session.logout()
        session.login("Bob", "Synthetic-cloud-only!", true).join()
        val bob = requireNotNull(session.state.value.account)
        assertNotEquals(alice.localUserId, bob.localUserId)
        assertTrue(db.categoryDao().getForUser(bob.localUserId).isEmpty())
        session.logout()
        session.login("Alice", "Synthetic-cloud-only!", true).join()
        val returned = requireNotNull(session.state.value.account)
        assertEquals(alice.localUserId, returned.localUserId)
        assertNotEquals(alice.sessionGeneration, returned.sessionGeneration)
        assertFalse(session.isCurrent(alice))
        assertEquals("Alice category", db.categoryDao().getForUser(returned.localUserId).single().name)
    }

    @Test fun matchingUsernameDidNotClaimLegacyDataAndLinkingNeededOldProof() = runBlocking {
        val salt = LegacyPassword.newSalt()
        val id = db.userDao().insert(UserEntity(username = "Alice", legacyPasswordSalt = salt,
            legacyPasswordHash = LegacyPassword.hash("Synthetic-local-only!", salt), categoriesInitialized = true))
        db.categoryDao().insert(CategoryEntity(userId = id, name = "Preserved"))
        session.login("Alice", "Synthetic-cloud-only!", true).join()
        assertNull(session.state.value.account)
        assertNotNull(session.state.value.cloud)
        assertNull(db.userDao().getById(id)?.firebaseUid)
        session.link(id, "incorrect-local-proof").join()
        assertNull(session.state.value.account)
        assertNull(db.userDao().getById(id)?.firebaseUid)
        session.link(id, "Synthetic-local-only!").join()
        assertEquals(id, session.state.value.account?.localUserId)
        val linked = requireNotNull(db.userDao().getById(id))
        assertEquals("uid-Alice", linked.firebaseUid)
        assertNull(linked.legacyPasswordHash)
        assertNull(linked.legacyPasswordSalt)
        assertTrue(linked.importPending)
        assertEquals(session.state.value.account, session.pendingLegacyImport(requireNotNull(session.state.value.account)))
        assertTrue(linked.categoriesInitialized)
        assertEquals("Preserved", db.categoryDao().getForUser(id).single().name)
    }

    @Test fun passwordlessLegacyRowsStayedUnclaimedAndSkipCreatedAnotherId() = runBlocking {
        val id = db.userDao().insert(UserEntity(username = "Alice"))
        session.login("Alice", "Synthetic-cloud-only!", true).join()
        assertFalse(session.state.value.localAccounts.single().canLink)
        session.link(id, "any-synthetic-proof").join()
        assertNull(session.state.value.account)
        session.useNewLocalAccount().join()
        assertNotEquals(id, session.state.value.account?.localUserId)
        assertNull(db.userDao().getById(id)?.firebaseUid)
    }

    @Test fun failedLoginCreatedNoMapping() = runBlocking {
        auth.failure = IllegalArgumentException("Synthetic login rejected")
        session.login("Nobody", "wrong-synthetic-password", true).join()
        assertNull(session.state.value.account)
        assertNotNull(session.state.value.message)
        assertNull(db.userDao().getByFirebaseUid("uid-Nobody"))
        assertNull(auth.currentUid)
    }

    @Test fun restoredOnlineAndPreviouslyAuthenticatedOfflineAccount() = runBlocking {
        val id = db.userDao().createCloudUser("uid-Alice", "Alice")
        store.save("uid-Alice", id, true)
        auth.current = CloudIdentity("uid-Alice", "Alice")
        session.restore().join()
        assertEquals(id, session.state.value.account?.localUserId)
        assertFalse(session.state.value.offline)
        auth.failure = FirebaseNetworkException("Synthetic offline")
        val offline = newSession()
        try {
            offline.restore().join()
            assertEquals(id, offline.state.value.account?.localUserId)
            assertTrue(offline.state.value.offline)
        } finally { offline.close() }
    }

    @Test fun unknownRestoredUidAndDisabledAccountCouldNotOpenFinancialScreens() = runBlocking {
        auth.current = CloudIdentity("uid-Unknown", "Unknown")
        store.save("uid-Unknown", 9876, true)
        session.restore().join()
        assertNull(session.state.value.account)
        assertNull(auth.currentUid)
        val id = db.userDao().createCloudUser("uid-Alice", "Alice")
        store.save("uid-Alice", id, true)
        auth.current = CloudIdentity("uid-Alice", "Alice")
        auth.failure = IllegalStateException("Synthetic invalid session")
        val invalid = newSession()
        try { invalid.restore().join(); assertNull(invalid.state.value.account); assertNull(auth.currentUid) }
        finally { invalid.close() }
    }

    @Test fun unrememberedSessionDidNotRestoreAndOfflineFirstLoginFailed() = runBlocking {
        val id = db.userDao().createCloudUser("uid-Alice", "Alice")
        auth.current = CloudIdentity("uid-Alice", "Alice")
        store.save("uid-Alice", id, false)
        session.restore().join()
        assertNull(session.state.value.account)
        auth.failure = FirebaseNetworkException("Synthetic offline")
        session.login("Alice", "Synthetic-cloud-only!", true).join()
        assertNull(session.state.value.account)
    }

    @Test fun externalUidChangeImmediatelyClosedTheFinancialSession() = runBlocking {
        session.login("Alice", "Synthetic-cloud-only!", true).join()
        val previous = requireNotNull(session.state.value.account)
        auth.changeUidOutsideSession()
        assertNull(session.state.value.account)
        assertFalse(session.isCurrent(previous))
        assertFalse(store.shouldRestore())
    }

    @Test fun logoutCancelledAccountWorkAndDetachedLateAuthentication() = runBlocking {
        session.login("Alice", "Synthetic-cloud-only!", true).join()
        val account = requireNotNull(session.state.value.account)
        val workStarted = CompletableDeferred<Unit>()
        val work = requireNotNull(session.launchAccountWork(account) { workStarted.complete(Unit); awaitCancellation() })
        workStarted.await()
        session.logout()
        work.join()
        assertTrue(work.isCancelled)
        assertNull(session.launchAccountWork(account) { fail("Stale work must not start") })
        auth.gate = CompletableDeferred()
        auth.loginStarted = false
        val login = session.login("Delayed", "Synthetic-cloud-only!", true)
        withTimeout(5000) { while (!auth.loginStarted) delay(10) }
        session.logout()
        auth.gate?.complete(Unit)
        login.join()
        assertNull(session.state.value.account)
        assertNull(auth.currentUid)
        assertNull(db.userDao().getByFirebaseUid("uid-Delayed"))
    }
}

internal class MemorySessionStore : SessionStore {
    private var uid: String? = null
    private var id: Long? = null
    private var remember = false
    override fun save(uid: String, localUserId: Long, remember: Boolean) { this.uid = uid; id = localUserId; this.remember = remember }
    override fun matches(uid: String, localUserId: Long) = this.uid == uid && id == localUserId
    override fun shouldRestore() = remember
    override fun clear() { uid = null; id = null; remember = false }
}

internal class FakeCloudAuth : CloudAuth {
    private val observers = mutableListOf<(String?) -> Unit>()
    var current: CloudIdentity? = null
    var failure: Exception? = null
    var gate: CompletableDeferred<Unit>? = null
    @Volatile var loginStarted = false
    override val currentUid: String? get() = current?.uid
    override fun cachedIdentity() = current
    override suspend fun register(username: String, password: String) = login(username, password)
    override suspend fun login(username: String, password: String): CloudIdentity {
        loginStarted = true
        gate?.await()
        failure?.let { throw it }
        return CloudIdentity("uid-$username", username).also { current = it }
    }
    override suspend fun validate(): CloudIdentity { failure?.let { throw it }; return requireNotNull(current) }
    override fun signOut() { current = null }
    override fun observeUid(onChange: (String?) -> Unit): () -> Unit {
        observers += onChange
        return { observers -= onChange }
    }
    fun changeUidOutsideSession() {
        current = CloudIdentity("synthetic-external-uid", "External")
        observers.toList().forEach { it(currentUid) }
    }
}
