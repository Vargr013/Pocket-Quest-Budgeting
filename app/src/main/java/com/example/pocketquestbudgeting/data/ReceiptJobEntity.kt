package com.example.pocketquestbudgeting.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

enum class ReceiptChange { KEEP, REPLACE, REMOVE }

@Entity(tableName = "receipt_jobs", indices = [Index(value = ["uid", "expenseCloudId"])])
data class ReceiptJob(
    @PrimaryKey val jobId: String,
    val uid: String,
    val userId: Long,
    val expenseCloudId: String,
    val sourceLocalPath: String?,
    val targetObjectPath: String,
    val expectedAcceptedPath: String?,
    val remove: Boolean = false,
    val selected: Boolean = true,
    val phase: String = "WAITING_FOR_EXPENSE",
    val sourceSha256: String? = null,
    val byteCount: Long? = null,
    val mimeType: String? = null,
    val publicationMutationId: String? = null,
    val attemptCount: Long = 0,
    val lastError: String? = null,
    val blocked: Boolean = false,
)

@Entity(tableName = "receipt_cache", primaryKeys = ["uid", "expenseCloudId", "objectPath"])
data class ReceiptCache(val uid: String, val userId: Long, val expenseCloudId: String,
    val objectPath: String, val localPath: String?, val state: String = "CACHED",
    val lastError: String? = null, val blocked: Boolean = false)

@Dao
interface ReceiptJobDao {
    @Upsert suspend fun put(job: ReceiptJob)
    @Upsert suspend fun cache(cache: ReceiptCache)
    @Query("SELECT * FROM receipt_jobs WHERE uid = :uid ORDER BY rowid")
    suspend fun jobs(uid: String): List<ReceiptJob>
    @Query("SELECT * FROM receipt_jobs WHERE uid = :uid ORDER BY rowid")
    fun observe(uid: String): Flow<List<ReceiptJob>>
    @Query("SELECT * FROM receipt_jobs WHERE jobId = :id AND uid = :uid")
    suspend fun get(uid: String, id: String): ReceiptJob?
    @Query("SELECT * FROM receipt_jobs WHERE uid = :uid AND expenseCloudId = :expense AND selected = 1 LIMIT 1")
    suspend fun selected(uid: String, expense: String): ReceiptJob?
    @Query("UPDATE receipt_jobs SET selected = 0 WHERE uid = :uid AND expenseCloudId = :expense")
    suspend fun deselect(uid: String, expense: String)
    @Query("SELECT * FROM receipt_cache WHERE uid = :uid AND expenseCloudId = :expense AND objectPath = :path")
    suspend fun cached(uid: String, expense: String, path: String): ReceiptCache?
    @Query("SELECT * FROM receipt_cache WHERE uid = :uid")
    fun observeCaches(uid: String): Flow<List<ReceiptCache>>
    @Query("SELECT * FROM receipt_cache WHERE uid = :uid")
    suspend fun caches(uid: String): List<ReceiptCache>
    @Query("UPDATE receipt_cache SET blocked = 0, lastError = NULL WHERE uid = :uid")
    suspend fun retryDownloads(uid: String)
    @Query("UPDATE receipt_cache SET state = 'SUPERSEDED', lastError = NULL, blocked = 1 WHERE uid = :uid AND expenseCloudId = :expense AND (:path IS NULL OR objectPath != :path)")
    suspend fun supersedeDownloads(uid: String, expense: String, path: String?)
    @Query("UPDATE receipt_jobs SET blocked = 0, lastError = NULL WHERE uid = :uid AND phase != 'CONFLICT'")
    suspend fun retry(uid: String)
}
