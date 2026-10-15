package com.example.pocketquestbudgeting.data

import android.content.Context

interface SessionStore {
    fun save(uid: String, localUserId: Long, remember: Boolean)
    fun matches(uid: String, localUserId: Long): Boolean
    fun shouldRestore(): Boolean
    fun clear()
}

class LocalSessionStore(context: Context) : SessionStore {
    private val preferences = context.applicationContext.getSharedPreferences("cloud-session", Context.MODE_PRIVATE)

    override fun save(uid: String, localUserId: Long, remember: Boolean) {
        check(preferences.edit().putString("uid", uid).putLong("localUserId", localUserId)
            .putBoolean("remember", remember).commit()) { "Could not save the local session. Try logging in again." }
    }

    override fun matches(uid: String, localUserId: Long): Boolean =
        preferences.getString("uid", null) == uid && preferences.getLong("localUserId", -1) == localUserId

    override fun shouldRestore(): Boolean = preferences.getBoolean("remember", false)

    override fun clear() { preferences.edit().clear().commit() }
}
