package com.example.pocketquestbudgeting.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class EarnedAchievementDao {
    @Query("SELECT * FROM earned_achievements WHERE userId = :userId ORDER BY id")
    abstract suspend fun getForUser(userId: Long): List<EarnedAchievementEntity>

    @Query("SELECT * FROM earned_achievements WHERE userId = :userId ORDER BY id")
    abstract fun observeForUser(userId: Long): Flow<List<EarnedAchievementEntity>>

    @Query("SELECT * FROM earned_achievements WHERE userId = :userId AND awardKey = :awardKey")
    abstract suspend fun getByAwardKey(userId: Long, awardKey: String): EarnedAchievementEntity?

    @Insert protected abstract suspend fun insertAward(award: EarnedAchievementEntity): Long

    // I stored supplied earned evidence without inventing a badge rule or historical time.
    @Transaction
    open suspend fun recordEarned(award: EarnedAchievementEntity): Long {
        require(award.id == 0L)
        EarnedAchievementChecks.requireValues(award)
        getByAwardKey(award.userId, award.awardKey)?.let { existing ->
            require(existing.copy(id = 0) == award) { "This award identity already has different evidence." }
            return existing.id
        }
        return insertAward(award)
    }

    @Query("UPDATE earned_achievements SET badgeId = :badgeId, criteriaVersion = :version, earnedAtEpochMillis = :instant, earnedDate = :date, earnedZoneId = :zone, evidenceRecordId = :evidence WHERE userId = :userId AND awardKey = :key")
    protected abstract suspend fun replaceEvidence(userId: Long, key: String, badgeId: String, version: Int, instant: Long?, date: String?, zone: String?, evidence: String): Int

    @Transaction
    open suspend fun acceptServer(award: EarnedAchievementEntity) {
        EarnedAchievementChecks.requireValues(award)
        val old = getByAwardKey(award.userId, award.awardKey)
        if (old == null) insertAward(award.copy(id = 0)) else check(replaceEvidence(award.userId, award.awardKey,
            award.badgeId, award.criteriaVersion, award.earnedAtEpochMillis, award.earnedDate, award.earnedZoneId, award.evidenceRecordId) == 1)
    }
}
