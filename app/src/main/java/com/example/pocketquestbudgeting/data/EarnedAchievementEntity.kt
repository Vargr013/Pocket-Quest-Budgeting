package com.example.pocketquestbudgeting.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "earned_achievements",
    foreignKeys = [ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["userId", "awardKey"], unique = true), Index(value = ["userId", "badgeId"])],
)
data class EarnedAchievementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val awardKey: String,
    val badgeId: String,
    val criteriaVersion: Int,
    val earnedAtEpochMillis: Long? = null,
    val earnedDate: String? = null,
    val earnedZoneId: String? = null,
    val evidenceRecordId: String,
)
