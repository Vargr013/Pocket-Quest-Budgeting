package com.example.pocketquestbudgeting.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "categories", indices = [Index("userId")])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val name: String,
    // I stored both monthly limits in cents. 0 means the limit is not set.
    @ColumnInfo(defaultValue = "0") val minMonthlyBudget: Long = 0,
    @ColumnInfo(defaultValue = "0") val maxMonthlyBudget: Long = 0,
)
