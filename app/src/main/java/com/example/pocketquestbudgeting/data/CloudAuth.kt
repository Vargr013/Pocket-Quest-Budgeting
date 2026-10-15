package com.example.pocketquestbudgeting.data

import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class CloudIdentity(val uid: String, val username: String)

interface CloudAuth {
    val currentUid: String?
    fun cachedIdentity(): CloudIdentity?
    suspend fun register(username: String, password: String): CloudIdentity
    suspend fun login(username: String, password: String): CloudIdentity
    suspend fun validate(): CloudIdentity
    fun signOut()
    fun observeUid(onChange: (String?) -> Unit): () -> Unit
}

class FirebaseCloudAuth(private val auth: FirebaseAuth) : CloudAuth {
    override val currentUid: String? get() = auth.currentUser?.uid

    override fun cachedIdentity(): CloudIdentity? = auth.currentUser?.let {
        CloudIdentity(it.uid, UsernameIdentity.fromEmail(requireNotNull(it.email)))
    }

    override suspend fun register(username: String, password: String): CloudIdentity {
        auth.createUserWithEmailAndPassword(UsernameIdentity.email(username), password).finish()
        return requireNotNull(cachedIdentity())
    }

    override suspend fun login(username: String, password: String): CloudIdentity {
        auth.signInWithEmailAndPassword(UsernameIdentity.email(username), password).finish()
        return requireNotNull(cachedIdentity())
    }

    override suspend fun validate(): CloudIdentity {
        // I reloaded the cached user before opening the account (Google, 2026ag).
        val user = requireNotNull(auth.currentUser) { "Please log in again." }
        user.reload().finish()
        user.getIdToken(true).finish()
        check(auth.currentUser?.uid == user.uid) { "The account changed. Please log in again." }
        return requireNotNull(cachedIdentity())
    }

    override fun signOut() = auth.signOut()

    override fun observeUid(onChange: (String?) -> Unit): () -> Unit {
        val listener = FirebaseAuth.AuthStateListener { onChange(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        return { auth.removeAuthStateListener(listener) }
    }
}

private suspend fun <T> Task<T>.finish(): T = withContext(NonCancellable) {
    // I waited for uncancellable Auth tasks so a late sign-in could be detached before another login.
    suspendCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(task.exception ?: IllegalStateException("Authentication did not finish."))
        }
    }
}

fun accountError(error: Exception): String = when (error) {
    is FirebaseNetworkException -> "Could not connect. First login, registration and linking need internet. Try again when connected."
    is FirebaseTooManyRequestsException -> "Too many attempts. Wait a little before trying again."
    is FirebaseAuthException -> when (error.errorCode) {
        "ERROR_EMAIL_ALREADY_IN_USE" -> "That username is already registered. Log in or choose another username."
        "ERROR_WEAK_PASSWORD" -> "Choose a stronger password that meets the Firebase project's password policy."
        "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Wait a little before trying again."
        "ERROR_USER_DISABLED" -> "This cloud account is disabled. Contact the project owner."
        "ERROR_OPERATION_NOT_ALLOWED" -> "Password login is not enabled for this Firebase project."
        "ERROR_INVALID_API_KEY", "ERROR_APP_NOT_AUTHORIZED", "ERROR_CONFIG_NOT_FOUND" -> "Firebase client setup is unavailable. Check the selected project configuration."
        else -> "Username or password is incorrect, or the session expired. Please log in again."
    }
    is IllegalArgumentException, is IllegalStateException -> error.message ?: "Account unavailable. Please log in again."
    else -> "Could not open the account. Please try again."
}
