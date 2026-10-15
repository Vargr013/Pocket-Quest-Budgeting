package com.example.pocketquestbudgeting.data

import android.content.Context
import androidx.room.withTransaction
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class SyncAccount(val localUserId: Long, val uid: String, val sessionGeneration: Long)
data class LocalAccountChoice(val id: Long, val username: String, val canLink: Boolean)
data class AccountState(
    val checked: Boolean = false,
    val busy: Boolean = false,
    val account: SyncAccount? = null,
    val cloud: CloudIdentity? = null,
    val localAccounts: List<LocalAccountChoice> = emptyList(),
    val offline: Boolean = false,
    val message: String? = null,
)

class AccountSession(
    private val db: AppDatabase,
    private val auth: CloudAuth,
    private val store: SessionStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    private val mutableState = MutableStateFlow(AccountState())
    val state = mutableState.asStateFlow()
    private val authLock = Mutex()
    @Volatile private var generation = 0L
    private var started = false
    private var rememberSession = true
    private var accountWork: CoroutineScope? = null
    private val removeListener = auth.observeUid { uid ->
        val active = mutableState.value.account
        if (active != null && active.uid != uid) logout("The account changed. Please log in again.")
    }

    fun restore(): Job = scope.launch {
        if (started) return@launch
        started = true
        val request = generation
        authLock.withLock {
            try {
                withContext(Dispatchers.IO) {
                    // I migrated signed-out installations too, then truncated the old WAL (Google, 2026u; SQLite, 2026a).
                    db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { result ->
                        check(result.moveToFirst() && result.getInt(0) == 0) { "Local account storage is busy. Please restart the app." }
                    }
                }
                val cached = auth.cachedIdentity()
                if (cached == null || !store.shouldRestore()) {
                    if (request == generation) logout()
                    return@withLock
                }
                val row = db.userDao().getByFirebaseUid(cached.uid)
                check(row != null && store.matches(cached.uid, row.id)) { "Please log in to connect this device again." }
                val offline = try {
                    check(auth.validate() == cached) { "The account changed. Please log in again." }
                    false
                } catch (_: FirebaseNetworkException) {
                    // I allowed offline restoration only for an already authenticated local mapping (Google, 2026ag).
                    true
                }
                requireCurrent(request, cached.uid)
                open(row.id, cached, offline, request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (request == generation) logout(accountError(error))
            }
        }
    }

    fun login(username: String, password: String, remember: Boolean, register: Boolean = false): Job {
        logout()
        rememberSession = remember
        return request {
            val name = UsernameIdentity.canonical(username)
            require(password.isNotBlank()) { "Enter a password." }
            require(password.length <= 4096) { "Password is too long." }
            if (register) require(password.length >= 6) { "Use at least 6 characters for your cloud password." }
            val identity = if (register) auth.register(name, password) else auth.login(name, password)
            requireCurrent(it, identity.uid)
            val existing = db.userDao().getByFirebaseUid(identity.uid)
            if (existing != null) {
                open(existing.id, identity, false, it)
            } else {
                val choices = db.userDao().getUnlinked().map { row ->
                    LocalAccountChoice(row.id, row.username, row.legacyPasswordHash != null)
                }
                requireCurrent(it, identity.uid)
                if (choices.isEmpty()) createLocal(identity, it)
                else mutableState.value = AccountState(checked = true, cloud = identity, localAccounts = choices)
            }
        }
    }

    fun useNewLocalAccount(): Job = request { request ->
        val identity = requireNotNull(state.value.cloud) { "Please log in again." }
        check(auth.validate() == identity) { "The account changed. Please log in again." }
        requireCurrent(request, identity.uid)
        createLocal(identity, request)
    }

    fun link(localUserId: Long, oldPassword: String): Job = request { request ->
        val identity = requireNotNull(state.value.cloud) { "Please log in again." }
        val row = requireNotNull(db.userDao().getById(localUserId)) { "Local account unavailable." }
        check(row.firebaseUid == null) { "This local account is already connected." }
        check(row.legacyPasswordHash != null) { "This old account has no password proof. Its data is kept for recovery." }
        val verified = withContext(Dispatchers.Default) {
            LegacyPassword.matches(oldPassword, row.legacyPasswordSalt, row.legacyPasswordHash)
        }
        require(verified) { "The old local password is incorrect." }
        check(auth.validate() == identity) { "The account changed. Please log in again." }
        requireCurrent(request, identity.uid)
        db.withTransaction {
            requireCurrent(request, identity.uid)
            check(db.userDao().getByFirebaseUid(identity.uid) == null) { "This cloud account is already connected to another local account." }
            check(db.userDao().linkUnclaimed(row.id, identity.uid) == 1) { "The local account changed. Try again." }
        }
        requireCurrent(request, identity.uid)
        open(row.id, identity, false, request)
    }

    private suspend fun createLocal(identity: CloudIdentity, request: Long) {
        val id = db.withTransaction {
            requireCurrent(request, identity.uid)
            db.userDao().createCloudUser(identity.uid, identity.username)
        }
        requireCurrent(request, identity.uid)
        // I left cloud defaults to slice 5 so a returning empty account stays empty.
        open(id, identity, false, request)
    }

    private fun open(id: Long, identity: CloudIdentity, offline: Boolean, request: Long) {
        requireCurrent(request, identity.uid)
        if (!offline) store.save(identity.uid, id, rememberSession)
        accountWork?.cancel()
        accountWork = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        mutableState.value = AccountState(checked = true, account = SyncAccount(id, identity.uid, request), offline = offline)
    }

    private fun request(action: suspend (Long) -> Unit): Job {
        val request = generation
        mutableState.value = state.value.copy(busy = true, message = null)
        return scope.launch {
            authLock.withLock {
                if (request != generation) return@withLock
                try {
                    requireCurrent(request)
                    action(request)
                } catch (cancelled: CancellationException) {
                    if (request != generation) auth.signOut()
                    throw cancelled
                } catch (error: Exception) {
                    if (request == generation) {
                        if (state.value.cloud == null) auth.signOut()
                        mutableState.value = state.value.copy(checked = true, message = accountError(error))
                    }
                    else auth.signOut()
                } finally {
                    if (request == generation) mutableState.value = state.value.copy(busy = false)
                }
            }
        }
    }

    private fun requireCurrent(request: Long, uid: String? = null) {
        check(request == generation && (uid == null || auth.currentUid == uid)) { "The session changed. Please log in again." }
    }

    fun isCurrent(account: SyncAccount): Boolean = state.value.account == account && auth.currentUid == account.uid

    suspend fun pendingLegacyImport(account: SyncAccount): SyncAccount? {
        if (!isCurrent(account)) return null
        val row = db.userDao().getById(account.localUserId)
        return if (isCurrent(account) && row?.firebaseUid == account.uid && row.importPending) account else null
    }

    fun launchAccountWork(account: SyncAccount, action: suspend CoroutineScope.() -> Unit): Job? {
        if (!isCurrent(account)) return null
        return accountWork?.launch { if (isCurrent(account)) action() }
    }

    fun logout(message: String? = null) {
        generation++
        accountWork?.cancel()
        accountWork = null
        mutableState.value = AccountState(checked = true, message = message)
        store.clear()
        auth.signOut()
    }

    fun close() { logout(); removeListener(); scope.cancel() }
}

object AccountSessions {
    private var instance: AccountSession? = null

    fun get(context: Context): AccountSession = instance ?: synchronized(this) {
        instance ?: AccountSession(
            DatabaseProvider.get(context.applicationContext),
            FirebaseCloudAuth(FirebaseAuth.getInstance()),
            LocalSessionStore(context.applicationContext),
        ).also { instance = it }
    }
}
