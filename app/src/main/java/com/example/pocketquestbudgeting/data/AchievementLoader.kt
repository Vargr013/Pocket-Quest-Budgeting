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
        DatabaseProvider.get(context).gamificationDao().markActiveDay(userId, todayIsoDate())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // A missed review day only affects the streak, so the screen keeps working.
    }
}

object AchievementLoader {
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
        val report = Achievements.evaluate(input, gamification.getEarned(userId), nowMillis)
        if (report.newlyEarned.isNotEmpty()) {
            gamification.insertEarned(report.newlyEarned.map { EarnedAchievementEntity(userId, it, nowMillis) })
        }
        report
    }
}
