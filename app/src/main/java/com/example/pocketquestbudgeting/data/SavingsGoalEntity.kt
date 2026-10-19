package com.example.pocketquestbudgeting.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "savings_goals",
    foreignKeys = [ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["userId", "cloudId"], unique = true), Index(value = ["userId", "id"], unique = true)],
)
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val cloudId: String,
    val name: String,
    val targetCents: Long,
    val targetDate: String? = null,
    val note: String? = null,
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean = false,
)
