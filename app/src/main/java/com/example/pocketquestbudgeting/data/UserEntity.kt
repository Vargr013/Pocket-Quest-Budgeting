package com.example.pocketquestbudgeting.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    @ColumnInfo(defaultValue = "0") val categoriesInitialized: Boolean = false,
)
