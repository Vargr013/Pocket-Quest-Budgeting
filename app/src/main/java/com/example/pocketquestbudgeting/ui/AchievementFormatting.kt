package com.example.pocketquestbudgeting.ui

import com.example.pocketquestbudgeting.R
import com.example.pocketquestbudgeting.data.AchievementDefinition
import com.example.pocketquestbudgeting.data.AchievementInput
import com.example.pocketquestbudgeting.data.AchievementProgress
import com.example.pocketquestbudgeting.data.AchievementReport
import com.example.pocketquestbudgeting.data.AchievementUnit
import com.example.pocketquestbudgeting.data.Achievements
import com.example.pocketquestbudgeting.data.BudgetGoalEntity
import com.example.pocketquestbudgeting.data.CategoryEntity
import com.example.pocketquestbudgeting.data.ExpenseEntity
import com.example.pocketquestbudgeting.data.WorthCatalog
import com.example.pocketquestbudgeting.data.WorthCategory
import com.example.pocketquestbudgeting.data.WorthItem
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

internal fun achievementIconRes(definition: AchievementDefinition): Int = when (definition.id) {
    "first_expense" -> R.drawable.achievement_first_steps
    "first_budget" -> R.drawable.achievement_planner
    "first_limit" -> R.drawable.achievement_limit_setter
    "streak_3" -> R.drawable.achievement_warming_up
    "streak_7" -> R.drawable.achievement_on_a_roll
    "streak_30" -> R.drawable.achievement_habit_hero
    "active_50" -> R.drawable.achievement_dedicated_tracker
    "budget_1" -> R.drawable.achievement_budget_keeper
    "budget_3" -> R.drawable.achievement_budget_pro
    "budget_6" -> R.drawable.achievement_budget_master
    "categories_1" -> R.drawable.achievement_category_champion
    "categories_3" -> R.drawable.achievement_limit_legend
    "worth_coffee" -> R.drawable.achievement_coffee_collector
    "worth_burger" -> R.drawable.achievement_burger_boss
    "worth_movie" -> R.drawable.achievement_movie_buff
    "worth_trip" -> R.drawable.achievement_getaway_saver
    "worth_phone" -> R.drawable.achievement_phone_fund
    "worth_car" -> R.drawable.achievement_wheels_in_motion
    else -> R.drawable.achievement_first_steps
}

internal fun worthCategoryIconRes(category: WorthCategory): Int? = when (category) {
    WorthCategory.ENTERTAINMENT -> R.drawable.worth_category_entertainment
    WorthCategory.TECHNOLOGY -> R.drawable.worth_category_technology
    WorthCategory.TRAVEL -> R.drawable.worth_category_travel
    WorthCategory.VEHICLES -> R.drawable.worth_category_vehicles
    else -> null
}

internal fun worthItemIconRes(itemId: String): Int? = when (itemId) {
    WorthCatalog.COFFEE -> R.drawable.achievement_coffee_collector
    WorthCatalog.BURGER -> R.drawable.achievement_burger_boss
    WorthCatalog.MOVIE_TICKET -> R.drawable.achievement_movie_buff
    WorthCatalog.WEEKEND_TRIP -> R.drawable.achievement_getaway_saver
    WorthCatalog.PHONE -> R.drawable.achievement_phone_fund
    WorthCatalog.CAR -> R.drawable.achievement_wheels_in_motion
    else -> null
}

internal fun worthItemDisplayIconRes(item: WorthItem): Int? =
    worthItemIconRes(item.id) ?: worthCategoryIconRes(item.category)

internal fun previewAchievementReport(): AchievementReport = Achievements.evaluate(
    AchievementInput(
        expenses = listOf(
            ExpenseEntity(id = 1, userId = 1, categoryId = 1, amount = 1_200_00, date = "2026-09-12",
                paymentTime = "09:00", description = "Groceries"),
            ExpenseEntity(id = 2, userId = 1, categoryId = 1, amount = 350_00, date = "2026-10-03",
                paymentTime = "12:00", description = "Groceries"),
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
