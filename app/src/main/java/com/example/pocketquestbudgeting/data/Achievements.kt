package com.example.pocketquestbudgeting.data

enum class AchievementGroup(val label: String) {
    GETTING_STARTED("Getting started"),
    CONSISTENCY("Consistency"),
    MONTHLY_BUDGET("Monthly budget"),
    CATEGORY_LIMITS("Category limits"),
    WHAT_ITS_WORTH("What It's Worth"),
}

enum class AchievementUnit { COUNT, DAYS, MONTHS, CENTS }

// Rewards only count logging, reviewing and staying under limits, never purchases or amounts spent.
enum class AchievementMetric { EXPENSES, BUDGET_GOALS, LIMITED_CATEGORIES, STREAK, ACTIVE_DAYS, BUDGET_RUN, CATEGORY_RUN, SAVED_CENTS }

data class AchievementDefinition(
    val id: String,
    val title: String,
    val description: String,
    val group: AchievementGroup,
    val metric: AchievementMetric,
    val target: Long,
    val unit: AchievementUnit,
    val worthItemId: String? = null,
)

data class AchievementProgress(
    val definition: AchievementDefinition,
    val current: Long,
    val earned: Boolean,
    val earnedAtMillis: Long?,
) {
    val fraction: Float get() = if (earned) 1f else (current.toDouble() / definition.target).coerceIn(0.0, 1.0).toFloat()
}

data class MonthResult(
    val month: String,
    val expenseCount: Int,
    val spentCents: Long,
    val budgetMaxCents: Long?,
    // Null when no category has a limit or nothing was logged that month.
    val allCategoriesUnderLimit: Boolean?,
    val categorySavedCents: Long,
) {
    // I skipped months with nothing logged so an unused month is not treated as staying under budget.
    val underBudget: Boolean? get() = if (expenseCount == 0) null else budgetMaxCents?.let { spentCents <= it }
    val savedCents: Long get() = if (underBudget == true) requireNotNull(budgetMaxCents) - spentCents else 0L
}

data class AchievementInput(
    val expenses: List<ExpenseEntity>,
    val budgetGoals: List<BudgetGoalEntity>,
    val categories: List<CategoryEntity>,
    val activeDays: List<String>,
    // YYYY-MM-DD in the user's local calendar.
    val today: String,
)

data class AchievementReport(
    val progress: List<AchievementProgress>,
    val newlyEarned: List<String>,
    val currentStreak: Int,
    val longestStreak: Int,
    val finishedMonths: List<MonthResult>,
    val currentMonth: MonthResult,
    val totalSavedCents: Long,
) {
    // The current month is shown as "on track" only; it never counts as savings.
    val currentMonthUnderCents: Long? get() = currentMonth.budgetMaxCents?.let { maxOf(0L, it - currentMonth.spentCents) }
    val lastMonthCategorySavedCents: Long get() = finishedMonths.lastOrNull()?.categorySavedCents ?: 0L
}

object Achievements {
    val definitions: List<AchievementDefinition> = listOf(
        AchievementDefinition("first_expense", "First Steps", "Log your first expense.",
            AchievementGroup.GETTING_STARTED, AchievementMetric.EXPENSES, 1, AchievementUnit.COUNT),
        AchievementDefinition("first_budget", "Planner", "Set your first monthly budget.",
            AchievementGroup.GETTING_STARTED, AchievementMetric.BUDGET_GOALS, 1, AchievementUnit.COUNT),
        AchievementDefinition("first_limit", "Limit Setter", "Give a category a monthly limit.",
            AchievementGroup.GETTING_STARTED, AchievementMetric.LIMITED_CATEGORIES, 1, AchievementUnit.COUNT),
        AchievementDefinition("streak_3", "Warming Up", "Log or review your expenses 3 days in a row.",
            AchievementGroup.CONSISTENCY, AchievementMetric.STREAK, 3, AchievementUnit.DAYS),
        AchievementDefinition("streak_7", "On a Roll", "Log or review your expenses 7 days in a row.",
            AchievementGroup.CONSISTENCY, AchievementMetric.STREAK, 7, AchievementUnit.DAYS),
        AchievementDefinition("streak_30", "Habit Hero", "Log or review your expenses 30 days in a row.",
            AchievementGroup.CONSISTENCY, AchievementMetric.STREAK, 30, AchievementUnit.DAYS),
        AchievementDefinition("active_50", "Dedicated Tracker", "Log or review your expenses on 50 different days.",
            AchievementGroup.CONSISTENCY, AchievementMetric.ACTIVE_DAYS, 50, AchievementUnit.DAYS),
        AchievementDefinition("budget_1", "Budget Keeper", "Finish a month at or under your monthly budget.",
            AchievementGroup.MONTHLY_BUDGET, AchievementMetric.BUDGET_RUN, 1, AchievementUnit.MONTHS),
        AchievementDefinition("budget_3", "Budget Pro", "Stay under your monthly budget 3 months in a row.",
            AchievementGroup.MONTHLY_BUDGET, AchievementMetric.BUDGET_RUN, 3, AchievementUnit.MONTHS),
        AchievementDefinition("budget_6", "Budget Master", "Stay under your monthly budget 6 months in a row.",
            AchievementGroup.MONTHLY_BUDGET, AchievementMetric.BUDGET_RUN, 6, AchievementUnit.MONTHS),
        AchievementDefinition("categories_1", "Category Champion", "Finish a month with every category at or under its limit.",
            AchievementGroup.CATEGORY_LIMITS, AchievementMetric.CATEGORY_RUN, 1, AchievementUnit.MONTHS),
        AchievementDefinition("categories_3", "Limit Legend", "Keep every category under its limit 3 months in a row.",
            AchievementGroup.CATEGORY_LIMITS, AchievementMetric.CATEGORY_RUN, 3, AchievementUnit.MONTHS),
        worth("worth_coffee", "Coffee Collector", "10 coffees", WorthCatalog.COFFEE, 100),
        worth("worth_burger", "Burger Boss", "20 burgers", WorthCatalog.BURGER, 200),
        worth("worth_movie", "Movie Buff", "5 movie tickets", WorthCatalog.MOVIE_TICKET, 50),
        worth("worth_trip", "Getaway Saver", "a weekend trip", WorthCatalog.WEEKEND_TRIP, 10),
        worth("worth_phone", "Phone Fund", "a new phone", WorthCatalog.PHONE, 10),
        worth("worth_car", "Wheels in Motion", "10% of a car", WorthCatalog.CAR, 1),
    )

    // I counted item quantities in tenths so a badge can target part of an item, such as 10% of a car.
    private fun worth(id: String, title: String, what: String, itemId: String, tenths: Long) = AchievementDefinition(
        id, title, "Save the value of $what by staying under your monthly budget.",
        AchievementGroup.WHAT_ITS_WORTH, AchievementMetric.SAVED_CENTS,
        WorthCatalog.item(itemId).priceCents * tenths / 10, AchievementUnit.CENTS, itemId,
    )

    fun evaluate(input: AchievementInput, stored: List<EarnedAchievementEntity>, nowMillis: Long): AchievementReport {
        val finished = monthResults(input.expenses, input.budgetGoals, input.categories, input.today)
        val thisMonth = monthResult(input.today.take(7), input.expenses, input.budgetGoals, input.categories)
        val streak = currentStreak(input.activeDays, input.today)
        val longest = longestStreak(input.activeDays)
        val totalSaved = finished.fold(0L) { total, month -> plusCapped(total, month.savedCents) }
        val storedById = stored.associateBy { it.achievementId }

        val progress = definitions.map { definition ->
            val (current, reached) = when (definition.metric) {
                AchievementMetric.EXPENSES -> input.expenses.count { !it.isDeleted }.toLong().let { it to it }
                AchievementMetric.BUDGET_GOALS -> effectiveGoals(input.budgetGoals).size.toLong().let { it to it }
                AchievementMetric.LIMITED_CATEGORIES -> input.categories.count { !it.isArchived && it.maxMonthlyBudget > 0 }.toLong().let { it to it }
                // I showed the live streak as progress but awarded the best streak so a past run still counts.
                AchievementMetric.STREAK -> streak.toLong() to longest.toLong()
                AchievementMetric.ACTIVE_DAYS -> validDays(input.activeDays).size.toLong().let { it to it }
                AchievementMetric.BUDGET_RUN -> longestRun(finished) { it.underBudget == true }.toLong().let { it to it }
                AchievementMetric.CATEGORY_RUN -> longestRun(finished) { it.allCategoriesUnderLimit == true }.toLong().let { it to it }
                AchievementMetric.SAVED_CENTS -> totalSaved to totalSaved
            }
            val saved = storedById[definition.id]
            val earned = saved != null || reached >= definition.target
            AchievementProgress(
                definition = definition,
                current = if (earned) definition.target else minOf(current, definition.target),
                earned = earned,
                earnedAtMillis = saved?.earnedAtMillis ?: if (earned) nowMillis else null,
            )
        }
        return AchievementReport(
            progress = progress,
            newlyEarned = progress.filter { it.earned && it.definition.id !in storedById }.map { it.definition.id },
            currentStreak = streak,
            longestStreak = longest,
            finishedMonths = finished,
            currentMonth = thisMonth,
            totalSavedCents = totalSaved,
        )
    }

    fun nextUnlock(
        progress: List<AchievementProgress>,
        include: (AchievementDefinition) -> Boolean = { true },
    ): AchievementProgress? = progress.filter { !it.earned && include(it.definition) }.maxByOrNull { it.fraction }

    fun currentStreak(activeDays: Collection<String>, today: String): Int {
        val days = validDays(activeDays)
        val todayNumber = epochDay(today)
        var day = when {
            todayNumber in days -> todayNumber
            todayNumber - 1 in days -> todayNumber - 1
            else -> return 0
        }
        var count = 0
        while (day in days) {
            count++
            day--
        }
        return count
    }

    fun longestStreak(activeDays: Collection<String>): Int {
        var best = 0
        var run = 0
        var previous: Long? = null
        for (day in validDays(activeDays).sorted()) {
            run = if (previous != null && day == previous + 1) run + 1 else 1
            best = maxOf(best, run)
            previous = day
        }
        return best
    }

    // One result per finished calendar month, from the first month with data up to last month.
    fun monthResults(
        expenses: List<ExpenseEntity>,
        goals: List<BudgetGoalEntity>,
        categories: List<CategoryEntity>,
        today: String,
    ): List<MonthResult> {
        val lastFinished = monthIndex(today.take(7)) - 1
        val firstMonth = (expenses.filter { !it.isDeleted }.map { it.date.take(7) } +
            effectiveGoals(goals).map { it.startDate.take(7) })
            .filter(MonthlyGoalChecks::validMonth)
            .minOfOrNull(::monthIndex) ?: return emptyList()
        if (firstMonth > lastFinished) return emptyList()
        return (firstMonth..lastFinished).map { monthResult(monthKey(it), expenses, goals, categories) }
    }

    fun monthResult(
        month: String,
        expenses: List<ExpenseEntity>,
        goals: List<BudgetGoalEntity>,
        categories: List<CategoryEntity>,
    ): MonthResult {
        val inMonth = expenses.filter { !it.isDeleted && it.date.take(7) == month }
        val spentByCategory = inMonth.groupBy { it.categoryId }.mapValues { (_, rows) -> sumCapped(rows.map { it.amount }) }
        // Categories keep no history of their limits, so past months use today's limits.
        val limited = categories.filter { !it.isArchived && it.maxMonthlyBudget > 0 }
        val logged = inMonth.isNotEmpty()
        return MonthResult(
            month = month,
            expenseCount = inMonth.size,
            spentCents = sumCapped(inMonth.map { it.amount }),
            budgetMaxCents = effectiveGoals(goals).firstOrNull { it.startDate.take(7) == month }?.maximumAmount,
            allCategoriesUnderLimit = if (!logged || limited.isEmpty()) null
                else limited.all { (spentByCategory[it.id] ?: 0L) <= it.maxMonthlyBudget },
            categorySavedCents = if (!logged) 0L
                else sumCapped(limited.map { maxOf(0L, it.maxMonthlyBudget - (spentByCategory[it.id] ?: 0L)) }),
        )
    }

    // I used the newest row per month and hid it if deleted, matching BudgetGoalDao.getForMonth.
    private fun effectiveGoals(goals: List<BudgetGoalEntity>): List<BudgetGoalEntity> =
        goals.filter { it.startDate.endsWith("-01") }
            .groupBy { it.startDate.take(7) }
            .mapNotNull { (_, rows) -> rows.maxByOrNull { it.id }?.takeIf { !it.isDeleted } }

    private fun longestRun(months: List<MonthResult>, passed: (MonthResult) -> Boolean): Int {
        var best = 0
        var run = 0
        for (month in months) {
            run = if (passed(month)) run + 1 else 0
            best = maxOf(best, run)
        }
        return best
    }

    private fun validDays(days: Collection<String>): Set<Long> =
        days.filter(ExpenseChecks::validDate).map(::epochDay).toSet()

    private fun sumCapped(values: List<Long>): Long = values.fold(0L, ::plusCapped)

    private fun plusCapped(a: Long, b: Long): Long = if (b > 0 && a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b

    private fun monthIndex(month: String): Int = month.take(4).toInt() * 12 + month.takeLast(2).toInt() - 1

    private fun monthKey(index: Int): String =
        "${(index / 12).toString().padStart(4, '0')}-${(index % 12 + 1).toString().padStart(2, '0')}"

    // I converted calendar dates to day numbers without time zones (Hinnant, 2021).
    internal fun epochDay(date: String): Long {
        val month = date.substring(5, 7).toInt()
        val day = date.substring(8, 10).toInt()
        val year = date.substring(0, 4).toLong() - if (month <= 2) 1 else 0
        val era = Math.floorDiv(year, 400L)
        val yearOfEra = year - era * 400
        val dayOfYear = (153 * ((month + 9) % 12) + 2) / 5 + day - 1
        val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era * 146_097 + dayOfEra - 719_468
    }
}
