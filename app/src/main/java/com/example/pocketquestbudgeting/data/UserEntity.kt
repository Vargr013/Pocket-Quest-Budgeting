package com.example.pocketquestbudgeting.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(tableName = "users", indices = [Index(value = ["firebaseUid"], unique = true)])
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    @ColumnInfo(defaultValue = "0") val categoriesInitialized: Boolean = false,
    val firebaseUid: String? = null,
    val legacyPasswordSalt: String? = null,
    val legacyPasswordHash: String? = null,
    @ColumnInfo(defaultValue = "0") val importPending: Boolean = false,
)
