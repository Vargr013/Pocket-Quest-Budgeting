package com.example.pocketquestbudgeting.data

import org.junit.Assert.*
import org.junit.Test

class AchievementsTest {
    private val today = "2026-10-20"

    private fun expense(id: Long, date: String, amount: Long, categoryId: Long = 1) = ExpenseEntity(
        id = id, userId = 1, categoryId = categoryId, amount = amount, date = date,
        paymentTime = "09:00", description = "Test",
    )

    private fun goal(id: Long, month: String, maximum: Long, deleted: Boolean = false) = BudgetGoalEntity(
        id = id, userId = 1, minimumAmount = 0, maximumAmount = maximum,
        startDate = "$month-01", endDate = "$month-28", isDeleted = deleted,
    )

    private fun category(id: Long, maximum: Long) = CategoryEntity(id = id, userId = 1, name = "Category $id", maxMonthlyBudget = maximum)

    private fun evaluate(
        expenses: List<ExpenseEntity> = emptyList(),
        goals: List<BudgetGoalEntity> = emptyList(),
        categories: List<CategoryEntity> = emptyList(),
        activeDays: List<String> = emptyList(),
        stored: List<EarnedAchievementEntity> = emptyList(),
    ) = Achievements.evaluate(AchievementInput(expenses, goals, categories, activeDays, today), stored, 1_000L)

    private fun AchievementReport.badge(id: String) = progress.first { it.definition.id == id }

    @Test fun streakCountedConsecutiveDaysEndingTodayOrYesterday() {
        assertEquals(3, Achievements.currentStreak(listOf("2026-10-18", "2026-10-19", "2026-10-20"), today))
        assertEquals(2, Achievements.currentStreak(listOf("2026-10-18", "2026-10-19"), today))
        assertEquals(0, Achievements.currentStreak(listOf("2026-10-16", "2026-10-18"), today))
        assertEquals(0, Achievements.currentStreak(emptyList(), today))
    }

    @Test fun gapResetTheStreak() {
        assertEquals(2, Achievements.currentStreak(listOf("2026-10-15", "2026-10-16", "2026-10-17", "2026-10-19", "2026-10-20"), today))
        assertEquals(3, Achievements.longestStreak(listOf("2026-10-15", "2026-10-16", "2026-10-17", "2026-10-19", "2026-10-20")))
    }

    @Test fun streakCrossedMonthYearAndLeapDayBoundaries() {
        assertEquals(2, Achievements.currentStreak(listOf("2025-12-31", "2026-01-01"), "2026-01-01"))
        assertEquals(3, Achievements.longestStreak(listOf("2024-02-28", "2024-02-29", "2024-03-01")))
        assertEquals(1, Achievements.longestStreak(listOf("2026-10-20", "2026-10-20", "not-a-date")))
    }

    @Test fun bestStreakKeptStreakBadgeWhileProgressShowedLiveStreak() {
        val report = evaluate(activeDays = listOf("2026-09-01", "2026-09-02", "2026-09-03"))
        assertTrue(report.badge("streak_3").earned)
        assertFalse(report.badge("streak_7").earned)
        assertEquals(0L, report.badge("streak_7").current)
        assertEquals(0, report.currentStreak)
        assertEquals(3, report.longestStreak)
    }

    @Test fun consecutiveMonthsUnderBudgetEarnedRunBadges() {
        val months = listOf("2026-07", "2026-08", "2026-09")
        val report = evaluate(
            expenses = months.mapIndexed { i, month -> expense(i + 1L, "$month-10", 1_000_00) },
            goals = months.mapIndexed { i, month -> goal(i + 1L, month, 2_000_00) },
        )
        assertTrue(report.badge("budget_1").earned)
        assertTrue(report.badge("budget_3").earned)
        assertFalse(report.badge("budget_6").earned)
        assertEquals(3L, report.badge("budget_6").current)
    }

    @Test fun overspentMonthBrokeTheRun() {
        val months = listOf("2026-07", "2026-08", "2026-09")
        val report = evaluate(
            expenses = listOf(expense(1, "2026-07-10", 100_00), expense(2, "2026-08-10", 3_000_00), expense(3, "2026-09-10", 100_00)),
            goals = months.mapIndexed { i, month -> goal(i + 1L, month, 2_000_00) },
        )
        assertTrue(report.badge("budget_1").earned)
        assertEquals(1L, report.badge("budget_3").current)
        assertEquals(3_800_00L, report.totalSavedCents)
    }

    @Test fun monthsWithoutGoalOrWithoutLoggingWereSkipped() {
        val report = evaluate(
            expenses = listOf(expense(1, "2026-09-10", 100_00)),
            goals = listOf(goal(1, "2026-08", 2_000_00)),
        )
        assertFalse(report.badge("budget_1").earned)
        assertEquals(0L, report.totalSavedCents)
        assertTrue(report.badge("first_budget").earned)
    }

    @Test fun deletedNewestGoalHidTheMonth() {
        val report = evaluate(
            expenses = listOf(expense(1, "2026-09-10", 100_00)),
            goals = listOf(goal(1, "2026-09", 2_000_00), goal(2, "2026-09", 2_000_00, deleted = true)),
        )
        assertFalse(report.badge("first_budget").earned)
        assertFalse(report.badge("budget_1").earned)
    }

    @Test fun categoriesWithoutLimitsWereIgnored() {
        val report = evaluate(
            expenses = listOf(expense(1, "2026-09-10", 50_00, categoryId = 1), expense(2, "2026-09-11", 9_000_00, categoryId = 2)),
            categories = listOf(category(1, 100_00), category(2, 0)),
        )
        assertTrue(report.badge("categories_1").earned)
        assertTrue(report.badge("first_limit").earned)
        assertEquals(50_00L, report.lastMonthCategorySavedCents)
    }

    @Test fun overLimitCategoryFailedTheMonth() {
        val report = evaluate(
            expenses = listOf(expense(1, "2026-09-10", 50_00, categoryId = 1), expense(2, "2026-09-11", 300_00, categoryId = 2)),
            categories = listOf(category(1, 100_00), category(2, 200_00)),
        )
        assertFalse(report.badge("categories_1").earned)
        assertEquals(50_00L, report.lastMonthCategorySavedCents)
    }

    @Test fun currentMonthWasNeverCountedAsSaved() {
        val report = evaluate(
            expenses = listOf(expense(1, "2026-10-05", 500_00)),
            goals = listOf(goal(1, "2026-10", 2_000_00)),
        )
        assertEquals(0L, report.totalSavedCents)
        assertFalse(report.badge("budget_1").earned)
        assertFalse(report.badge("worth_coffee").earned)
        assertEquals(1_500_00L, report.currentMonthUnderCents)
        assertTrue(report.finishedMonths.isEmpty())
    }

    @Test fun savingsUnlockedWorthBadges() {
        val report = evaluate(
            expenses = listOf(expense(1, "2026-09-10", 1_500_00)),
            goals = listOf(goal(1, "2026-09", 2_000_00)),
        )
        assertEquals(500_00L, report.totalSavedCents)
        assertTrue(report.badge("worth_coffee").earned)
        assertFalse(report.badge("worth_burger").earned)
        assertEquals(500_00L, report.badge("worth_burger").current)
        assertEquals(1_600_00L, report.badge("worth_burger").definition.target)
        assertEquals(20_000_00L, report.badge("worth_car").definition.target)
    }

    @Test fun storedBadgesStayedEarnedWithTheirFirstDate() {
        val stored = listOf(EarnedAchievementEntity(userId = 1, awardKey = "gamification:v1:first_expense",
            badgeId = "first_expense", criteriaVersion = 1, earnedAtEpochMillis = 42L, evidenceRecordId = "synthetic"))
        val report = evaluate(stored = stored)
        assertTrue(report.badge("first_expense").earned)
        assertEquals(42L, report.badge("first_expense").earnedAtMillis)
        assertTrue(report.newlyEarned.isEmpty())

        val again = evaluate(expenses = listOf(expense(1, "2026-10-01", 10_00)), stored = stored)
        assertEquals(listOf<String>(), again.newlyEarned)
        val fresh = evaluate(expenses = listOf(expense(1, "2026-10-01", 10_00)))
        assertEquals(listOf("first_expense"), fresh.newlyEarned)
        assertEquals(1_000L, fresh.badge("first_expense").earnedAtMillis)
    }

    @Test fun nextUnlockPickedTheBadgeClosestToCompletion() {
        val report = evaluate(
            expenses = listOf(expense(1, "2026-09-10", 1_500_00)),
            goals = listOf(goal(1, "2026-09", 2_000_00)),
        )
        // 500 of 875 for Movie Buff beats 1 of 3 months for Budget Pro.
        assertEquals("worth_movie", Achievements.nextUnlock(report.progress)?.definition?.id)
        assertEquals("budget_3", Achievements.nextUnlock(report.progress) { it.group == AchievementGroup.MONTHLY_BUDGET }?.definition?.id)
        val allEarned = report.progress.map { it.copy(earned = true) }
        assertNull(Achievements.nextUnlock(allEarned))
    }

    @Test fun existingSharedAwardsKeepTheirEvidenceAndDoNotGetAwardedAgain() {
        val first = EarnedAchievementEntity(userId = 1, awardKey = "existing:first", badgeId = "first_expense",
            criteriaVersion = 1, earnedAtEpochMillis = 42L, evidenceRecordId = "existing-evidence")
        val report = evaluate(stored = listOf(first, first.copy(awardKey = "later:first", earnedAtEpochMillis = 100L)))
        assertEquals(42L, report.badge("first_expense").earnedAtMillis)
        assertTrue(report.newlyEarned.isEmpty())

        val dateOnly = first.copy(earnedAtEpochMillis = null, earnedDate = "2026-09-01", earnedZoneId = "Africa/Johannesburg")
        val dated = evaluate(stored = listOf(dateOnly))
        assertTrue(dated.badge("first_expense").earned)
        assertNull(dated.badge("first_expense").earnedAtMillis)
        assertTrue(dated.newlyEarned.isEmpty())
    }
}
