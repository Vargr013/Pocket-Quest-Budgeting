package com.example.pocketquestbudgeting.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    @Insert suspend fun insert(operation: SyncOperation): Long
    @Upsert suspend fun put(record: SyncRecord)

    @Query("SELECT * FROM sync_records WHERE uid = :uid AND recordType = :type AND recordId = :id")
    suspend fun record(uid: String, type: String, id: String): SyncRecord?

    @Query("SELECT * FROM sync_records WHERE uid = :uid")
    suspend fun records(uid: String): List<SyncRecord>

    @Query("SELECT * FROM sync_queue WHERE uid = :uid ORDER BY sequence")
    suspend fun pending(uid: String): List<SyncOperation>

    @Query("SELECT * FROM sync_queue WHERE uid = :uid ORDER BY sequence")
    fun observe(uid: String): Flow<List<SyncOperation>>

    @Query("SELECT * FROM sync_queue WHERE uid = :uid AND recordType = :type AND recordId = :id ORDER BY sequence")
    suspend fun forRecord(uid: String, type: String, id: String): List<SyncOperation>

    @Query("DELETE FROM sync_queue WHERE uid = :uid AND mutationId = :mutationId")
    suspend fun acknowledge(uid: String, mutationId: String): Int

    @Query("UPDATE sync_queue SET baseRevision = :revision WHERE sequence = :sequence AND baseRevision IS NULL")
    suspend fun setBase(sequence: Long, revision: Long)

    @Query("UPDATE sync_queue SET state = :state, lastError = :message, attemptCount = attemptCount + 1 WHERE uid = :uid AND mutationId = :mutationId")
    suspend fun mark(uid: String, mutationId: String, state: String, message: String?)

    @Query("UPDATE sync_queue SET state = 'PENDING', lastError = NULL WHERE uid = :uid AND state = 'FAILED'")
    suspend fun retryFailures(uid: String)

    @Query("DELETE FROM sync_queue WHERE uid = :uid AND recordType = :type AND recordId = :id")
    suspend fun removeForReview(uid: String, type: String, id: String)
}
