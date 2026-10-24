package com.example.pocketquestbudgeting.ui

import com.example.pocketquestbudgeting.R
import com.example.pocketquestbudgeting.data.AchievementDefinition
import com.example.pocketquestbudgeting.data.AchievementGroup
import com.example.pocketquestbudgeting.data.AchievementInput
import com.example.pocketquestbudgeting.data.AchievementProgress
import com.example.pocketquestbudgeting.data.AchievementReport
import com.example.pocketquestbudgeting.data.AchievementUnit
import com.example.pocketquestbudgeting.data.Achievements
import com.example.pocketquestbudgeting.data.BudgetGoalEntity
import com.example.pocketquestbudgeting.data.CategoryEntity
import com.example.pocketquestbudgeting.data.ExpenseEntity
import com.example.pocketquestbudgeting.data.WorthCatalog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun achievementProgressText(progress: AchievementProgress): String {
    val current = progress.current
    val target = progress.definition.target
    return when (progress.definition.unit) {
        AchievementUnit.COUNT -> "$current/$target"
        AchievementUnit.DAYS -> "$current/$target days"
        AchievementUnit.MONTHS -> "$current/$target ${if (target == 1L) "month" else "months"}"
        AchievementUnit.CENTS -> "${formatBudgetAmount(current)} / ${formatBudgetAmount(target)}"
    }
}

internal fun earnedDateText(millis: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))

// Only some badges have artwork; the rest show their first letter.
internal fun achievementIconRes(definition: AchievementDefinition): Int? = when (definition.group) {
    AchievementGroup.WHAT_ITS_WORTH -> definition.worthItemId?.let(::worthItemIconRes)
    AchievementGroup.MONTHLY_BUDGET -> R.drawable.piggy_bank
    AchievementGroup.GETTING_STARTED -> R.drawable.piggy_login_logo
    else -> null
}

internal fun worthItemIconRes(itemId: String): Int? = when (itemId) {
    WorthCatalog.COFFEE -> R.drawable.icon_coffee
    WorthCatalog.BURGER -> R.drawable.icon_burger
    WorthCatalog.MOVIE_TICKET -> R.drawable.icon_ticket
    else -> null
}

internal fun previewAchievementReport(): AchievementReport = Achievements.evaluate(
    AchievementInput(
        expenses = listOf(
            ExpenseEntity(id = 1, userId = 1, categoryId = 1, amount = 1_200_00, date = "2026-09-12",
                startTime = "09:00", endTime = "09:30", description = "Groceries"),
            ExpenseEntity(id = 2, userId = 1, categoryId = 1, amount = 350_00, date = "2026-10-03",
                startTime = "12:00", endTime = "12:15", description = "Groceries"),
        ),
        budgetGoals = listOf(
            BudgetGoalEntity(id = 1, userId = 1, minimumAmount = 0, maximumAmount = 2_000_00,
                startDate = "2026-09-01", endDate = "2026-09-30"),
            BudgetGoalEntity(id = 2, userId = 1, minimumAmount = 0, maximumAmount = 2_000_00,
                startDate = "2026-10-01", endDate = "2026-10-31"),
        ),
        categories = listOf(CategoryEntity(id = 1, userId = 1, name = "Groceries", maxMonthlyBudget = 1_500_00)),
        activeDays = listOf("2026-10-18", "2026-10-19", "2026-10-20"),
        today = "2026-10-20",
    ),
    stored = emptyList(),
    nowMillis = 0L,
)
