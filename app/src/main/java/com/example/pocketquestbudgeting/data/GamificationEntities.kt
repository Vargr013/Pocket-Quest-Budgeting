package com.example.pocketquestbudgeting.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

// A row means the user logged or reviewed expenses on that YYYY-MM-DD date.
@Entity(tableName = "activity_days", primaryKeys = ["userId", "date"])
data class ActivityDayEntity(
    val userId: Long,
    val date: String,
)

@Entity(tableName = "custom_worth_items", indices = [Index("userId"), Index(value = ["userId", "cloudId"], unique = true)])
data class CustomWorthItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val name: String,
    // I stored the estimate in cents, matching expenses.
    val valueCents: Long,
    val category: String,
    val cloudId: String? = null,
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean = false,
)
