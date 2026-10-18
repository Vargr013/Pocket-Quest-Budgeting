package com.example.pocketquestbudgeting.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface UserDao {
    @Query("UPDATE users SET categoriesInitialized = 1 WHERE id = :userId")
    suspend fun markCategoriesInitialized(userId: Long)

    @Query("UPDATE users SET importPending = 0 WHERE id = :userId AND firebaseUid = :uid AND importPending = 1")
    suspend fun finishRecordImport(userId: Long, uid: String)

    @Insert
    suspend fun insert(user: UserEntity): Long

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getById(id: Long): UserEntity?

    @Query("SELECT * FROM users WHERE firebaseUid = :uid")
    suspend fun getByFirebaseUid(uid: String): UserEntity?

    @Query("SELECT * FROM users WHERE firebaseUid IS NULL ORDER BY username, id")
    suspend fun getUnlinked(): List<UserEntity>

    @Query("""
        UPDATE users SET firebaseUid = :uid, legacyPasswordSalt = NULL,
            legacyPasswordHash = NULL, importPending = 1
        WHERE id = :localUserId AND firebaseUid IS NULL
    """)
    suspend fun linkUnclaimed(localUserId: Long, uid: String): Int

    @Transaction
    suspend fun createCloudUser(uid: String, username: String): Long {
        require(uid.isNotBlank())
        return getByFirebaseUid(uid)?.id ?: insert(UserEntity(username = username, firebaseUid = uid))
    }
}
