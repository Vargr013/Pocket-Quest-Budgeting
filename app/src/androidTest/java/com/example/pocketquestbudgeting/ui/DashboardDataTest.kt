package com.example.pocketquestbudgeting.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pocketquestbudgeting.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DashboardDataTest {
    @Test fun dashboardUsesOnlyTheOwnersActiveRecordsAndExactSavingsHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val alice = db.userDao().insert(UserEntity(username = "DashboardAlice"))
            val bob = db.userDao().insert(UserEntity(username = "DashboardBob"))
            val category = db.categoryDao().insert(CategoryEntity(userId = alice, name = "Groceries"))
            val otherCategory = db.categoryDao().insert(CategoryEntity(userId = bob, name = "Private category"))
            for (day in 1..4) db.expenseDao().insert(ExpenseEntity(userId = alice, categoryId = category,
                amount = 12345L + day, date = "2026-10-0$day", startTime = "10:00", endTime = "10:01", description = "Expense $day"))
            db.expenseDao().insert(ExpenseEntity(userId = alice, categoryId = category, amount = 1,
                date = "2026-10-06", startTime = "10:00", endTime = "10:01", description = "Removed", isDeleted = true))
            db.expenseDao().insert(ExpenseEntity(userId = bob, categoryId = otherCategory, amount = 999,
                date = "2026-10-07", startTime = "10:00", endTime = "10:01", description = "Private expense"))
            val goal = db.savingsDao().createGoal(SavingsGoalEntity(userId = alice, cloudId = UUID.randomUUID().toString(),
                name = "Holiday", targetCents = 100000), openingCents = 25001, openingDate = "2026-10-01")
            val removed = db.savingsDao().createGoal(SavingsGoalEntity(userId = alice, cloudId = UUID.randomUUID().toString(),
                name = "Removed goal", targetCents = 10000))
            db.savingsDao().removeGoal(alice, removed)
            db.savingsDao().createGoal(SavingsGoalEntity(userId = bob, cloudId = UUID.randomUUID().toString(),
                name = "Private goal", targetCents = 100000))

            val data = loadDashboardData(db, alice)
            assertEquals(listOf("Expense 4", "Expense 3", "Expense 2"), data.recentExpenses.map { it.description })
            assertEquals(mapOf(category to "Groceries"), data.categoryNames)
            assertEquals("Holiday", data.savings.single().goal.name)
            assertEquals(25001L, data.savings.single().savedCents)

            db.savingsDao().recordContribution(SavingsContributionEntity(userId = alice, goalId = goal,
                businessId = "addition:dashboard", kind = SavingsChecks.ADDITION, amountCents = 999, effectiveDate = "2026-10-02"))
            assertEquals(26000L, loadDashboardData(db, alice).savings.single().savedCents)
            assertTrue(loadDashboardData(db, db.userDao().insert(UserEntity(username = "Empty"))).recentExpenses.isEmpty())
        } finally {
            db.close()
        }
    }
}
