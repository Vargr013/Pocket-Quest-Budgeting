package com.example.pocketquestbudgeting.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "sync_records", primaryKeys = ["uid", "recordType", "recordId"], indices = [Index("userId")])
data class SyncRecord(
    val uid: String,
    val userId: Long,
    val recordType: String,
    val recordId: String,
    val revision: Long = 0,
    val payload: String = "{}",
    val isDeleted: Boolean = false,
    val updatedAtMillis: Long? = null,
    val deletedAtMillis: Long? = null,
    val lastMutationId: String? = null,
)

@Entity(tableName = "sync_queue", indices = [Index(value = ["mutationId"], unique = true), Index(value = ["uid", "recordType", "recordId"])])
data class SyncOperation(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val mutationId: String,
    val uid: String,
    val userId: Long,
    val recordType: String,
    val recordId: String,
    val payload: String,
    val isDeleted: Boolean,
    val baseRevision: Long?,
    val state: String = "PENDING",
    val attemptCount: Long = 0,
    val lastError: String? = null,
    @ColumnInfo(defaultValue = "'FROZEN'") val receiptMode: String = "FROZEN",
)
