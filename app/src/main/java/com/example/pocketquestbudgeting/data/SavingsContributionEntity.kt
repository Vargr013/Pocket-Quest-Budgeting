package com.example.pocketquestbudgeting.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "savings_contributions",
    foreignKeys = [ForeignKey(
        entity = SavingsGoalEntity::class, parentColumns = ["userId", "id"], childColumns = ["userId", "goalId"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [
        Index(value = ["userId", "businessId"], unique = true), Index(value = ["userId", "goalId"]),
        Index(value = ["userId", "reversesBusinessId"], unique = true),
    ],
)
data class SavingsContributionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val goalId: Long,
    val businessId: String,
    val kind: String,
    val amountCents: Long,
    val effectiveDate: String,
    val reversesBusinessId: String? = null,
    val note: String? = null,
)
