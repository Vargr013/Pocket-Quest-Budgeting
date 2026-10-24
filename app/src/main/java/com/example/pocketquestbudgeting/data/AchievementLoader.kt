package com.example.pocketquestbudgeting.data

import android.content.Context
import androidx.room.withTransaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException

fun todayIsoDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

// Reviewing counts toward the streak so users never need to spend money to keep it going.
suspend fun markActiveToday(context: Context, userId: Long) {
    try {
        val account = AccountSessions.get(context).state.value.account?.takeIf { it.localUserId == userId } ?: return
        GamificationSyncs.get(context).markActiveDay(account, todayIsoDate())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // A missed review day only affects the streak, so the screen keeps working.
    }
}

object AchievementLoader {
    suspend fun load(context: Context, userId: Long): AchievementReport {
        val report = load(DatabaseProvider.get(context), userId)
        if (report.newlyEarned.isNotEmpty()) {
            AccountSessions.get(context).state.value.account?.takeIf { it.localUserId == userId }?.let {
                SyncWork.scheduleRetry(context, it)
            }
        }
        return report
    }

    // I evaluated and saved new badges in one transaction so a badge is stored once with its first date (Google, 2026j).
    suspend fun load(
        db: AppDatabase,
        userId: Long,
        today: String = todayIsoDate(),
        nowMillis: Long = System.currentTimeMillis(),
    ): AchievementReport = db.withTransaction {
        val gamification = db.gamificationDao()
        val input = AchievementInput(
            expenses = db.expenseDao().getForUser(userId),
            budgetGoals = db.budgetGoalDao().getForUser(userId),
            categories = db.categoryDao().getForUser(userId),
            activeDays = gamification.getActiveDays(userId),
            today = today,
        )
        val awards = db.earnedAchievementDao()
        val report = Achievements.evaluate(input, awards.getForUser(userId), nowMillis)
        for (badgeId in report.newlyEarned) {
            // I reused the durable award identity so normal savings sync can publish each badge once.
            awards.recordEarned(EarnedAchievementEntity(
                userId = userId, awardKey = "gamification:v1:$badgeId", badgeId = badgeId, criteriaVersion = 1,
                earnedAtEpochMillis = nowMillis, evidenceRecordId = "gamification:v1:$badgeId",
            ))
        }
        report
    }
}
