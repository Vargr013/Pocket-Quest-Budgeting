package com.example.pocketquestbudgeting.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SavingsDao {
    @Query("SELECT * FROM savings_goals WHERE userId = :userId AND isDeleted = 0 ORDER BY name COLLATE NOCASE, id")
    abstract fun observeForUser(userId: Long): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals WHERE userId = :userId AND (:includeDeleted = 1 OR isDeleted = 0) ORDER BY id")
    abstract suspend fun getForUser(userId: Long, includeDeleted: Boolean = false): List<SavingsGoalEntity>

    @Query("SELECT * FROM savings_goals WHERE userId = :userId AND id = :goalId")
    abstract suspend fun getForUserById(userId: Long, goalId: Long): SavingsGoalEntity?

    @Query("SELECT * FROM savings_goals WHERE userId = :userId AND cloudId = :cloudId")
    abstract suspend fun getByCloudId(userId: Long, cloudId: String): SavingsGoalEntity?

    @Query("SELECT * FROM savings_contributions WHERE userId = :userId AND goalId = :goalId ORDER BY id")
    abstract suspend fun contributions(userId: Long, goalId: Long): List<SavingsContributionEntity>

    @Query("SELECT * FROM savings_contributions WHERE userId = :userId AND goalId = :goalId ORDER BY id")
    abstract fun observeContributions(userId: Long, goalId: Long): Flow<List<SavingsContributionEntity>>

    @Query("SELECT * FROM savings_contributions WHERE userId = :userId AND businessId = :businessId")
    abstract suspend fun contribution(userId: Long, businessId: String): SavingsContributionEntity?

    @Insert protected abstract suspend fun insertGoal(goal: SavingsGoalEntity): Long
    @Insert protected abstract suspend fun insertContribution(row: SavingsContributionEntity): Long
    @Update protected abstract suspend fun updateDownloadedGoal(goal: SavingsGoalEntity): Int
    @Update protected abstract suspend fun updateDownloadedContribution(row: SavingsContributionEntity): Int

    @Query("UPDATE savings_goals SET name = :name, targetCents = :targetCents, targetDate = :targetDate, note = :note WHERE userId = :userId AND id = :goalId AND isDeleted = 0")
    protected abstract suspend fun updateDetails(userId: Long, goalId: Long, name: String, targetCents: Long, targetDate: String?, note: String?): Int

    @Query("UPDATE savings_goals SET isDeleted = 1 WHERE userId = :userId AND id = :goalId")
    abstract suspend fun removeGoal(userId: Long, goalId: Long): Int

    @Transaction
    open suspend fun createGoal(goal: SavingsGoalEntity, openingCents: Long = 0, openingDate: String? = null): Long {
        SavingsChecks.requireGoal(goal)
        require(goal.id == 0L && !goal.isDeleted && openingCents >= 0)
        require(if (openingCents == 0L) openingDate == null else openingDate != null && ExpenseChecks.validDate(openingDate))
        val existing = getByCloudId(goal.userId, goal.cloudId)
        val goalId = if (existing == null) insertGoal(goal) else {
            require(existing.copy(id = 0) == goal) { "This goal identity already has different details." }
            val opening = contribution(goal.userId, "opening:${goal.cloudId}")
            require(if (openingCents == 0L) opening == null else opening != null && opening.amountCents == openingCents && opening.effectiveDate == openingDate)
            existing.id
        }
        if (openingCents > 0) append(SavingsContributionEntity(
            userId = goal.userId, goalId = goalId, businessId = "opening:${goal.cloudId}",
            kind = SavingsChecks.OPENING, amountCents = openingCents, effectiveDate = requireNotNull(openingDate),
        ))
        return goalId
    }

    @Transaction
    open suspend fun editGoal(userId: Long, goalId: Long, name: String, targetCents: Long, targetDate: String?, note: String?): Int {
        val current = requireNotNull(getForUserById(userId, goalId)) { "Goal not found for this account." }
        require(!current.isDeleted) { "This goal was removed." }
        SavingsChecks.requireGoal(current.copy(name = name, targetCents = targetCents, targetDate = targetDate, note = note))
        return updateDetails(userId, goalId, name, targetCents, targetDate, note)
    }

    @Transaction
    open suspend fun recordContribution(row: SavingsContributionEntity): Long {
        require(row.kind != SavingsChecks.OPENING) { "Opening amounts belong to goal creation." }
        return append(row)
    }

    private suspend fun append(row: SavingsContributionEntity): Long {
        SavingsChecks.requireContribution(row)
        require(row.id == 0L)
        // I returned the original row for a retry, including after a goal was removed.
        contribution(row.userId, row.businessId)?.let { existing ->
            require(existing.copy(id = 0) == row) { "This contribution identity already has different values." }
            return existing.id
        }
        val goal = requireNotNull(getForUserById(row.userId, row.goalId)) { "Goal not found for this account." }
        require(!goal.isDeleted) { "This goal was removed." }
        val history = contributions(row.userId, row.goalId)
        if (row.kind == SavingsChecks.REVERSAL) {
            val original = requireNotNull(contribution(row.userId, requireNotNull(row.reversesBusinessId)))
            require(original.goalId == row.goalId && original.kind != SavingsChecks.REVERSAL)
            require(original.amountCents == row.amountCents) { "A reversal must match the full original amount." }
            require(history.none { it.reversesBusinessId == original.businessId }) { "This contribution was already reversed." }
        }
        SavingsChecks.balance(history + row)
        return insertContribution(row)
    }

    @Transaction
    open suspend fun savedCents(userId: Long, goalId: Long): Long {
        requireNotNull(getForUserById(userId, goalId)) { "Goal not found for this account." }
        return SavingsChecks.balance(contributions(userId, goalId))
    }

    @Transaction
    open suspend fun acceptServerGoal(goal: SavingsGoalEntity) {
        SavingsChecks.requireGoal(goal)
        val old = getByCloudId(goal.userId, goal.cloudId)
        check(old?.isDeleted != true || goal.isDeleted) { "A removed goal cannot be restored implicitly." }
        if (old == null) insertGoal(goal.copy(id = 0)) else check(updateDownloadedGoal(goal.copy(id = old.id)) == 1)
    }

    @Transaction
    open suspend fun acceptServerHistory(rows: List<SavingsContributionEntity>, reviewed: Boolean = false) {
        for (row in rows) {
            SavingsChecks.requireContribution(row)
            requireNotNull(getForUserById(row.userId, row.goalId))
            val old = contribution(row.userId, row.businessId)
            if (old == null) insertContribution(row.copy(id = 0)) else {
                check(old.goalId == row.goalId) { "A contribution cannot move between goals." }
                check(reviewed || old.copy(id = 0) == row.copy(id = 0)) { "Review this contribution's server evidence." }
                if (reviewed) check(updateDownloadedContribution(row.copy(id = old.id)) == 1)
            }
        }
        rows.map { it.userId to it.goalId }.distinct().forEach { (owner, goal) ->
            val history = contributions(owner, goal)
            history.filter { it.kind == SavingsChecks.REVERSAL }.forEach { reversal ->
                val original = requireNotNull(history.singleOrNull { it.businessId == reversal.reversesBusinessId })
                check(original.kind != SavingsChecks.REVERSAL && original.amountCents == reversal.amountCents)
            }
            SavingsChecks.balance(history)
        }
    }
}
