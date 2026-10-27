package com.example.pocketquestbudgeting.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.pocketquestbudgeting.data.AccountSessions
import com.example.pocketquestbudgeting.data.AccountSession

@Composable
fun PocketQuestNavigation(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val session = remember(context.applicationContext) { AccountSessions.get(context) }
    AccountContent(session, modifier)
}

@Composable
internal fun AccountContent(session: AccountSession, modifier: Modifier = Modifier) {
    val state by session.state.collectAsState()
    LaunchedEffect(session) { session.restore().join() }
    if (!state.checked) {
        Text("Checking account...")
        return
    }
    val account = state.account
    // I discarded navigation and saved drafts for every new session, even with the same UID (Google, 2026i).
    key(account?.sessionGeneration, account?.uid, account?.localUserId) {
        if (account == null) {
            AuthenticationNavigation(session, state, modifier)
        } else {
            Column(modifier) {
                if (state.offline) Text("Offline: showing this account's saved local data.")
                AccountNavigation(account.localUserId, onLogout = { session.logout() }, modifier = Modifier.weight(1f),
                    syncStatus = { SyncStatus(account) })
            }
        }
    }
}

@Composable
private fun AccountNavigation(userId: Long, onLogout: () -> Unit, modifier: Modifier, syncStatus: @Composable () -> Unit) {
    // I used a navigation controller and host to manage the screen routes (Google, 2026e).
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "dashboard", modifier = modifier) {
        composable("dashboard") {
            DashboardScreen(
                userId = userId,
                onAddExpense = { navController.navigate("add_expense") },
                onHistory = { navController.navigate("history") },
                onCategories = { navController.navigate("categories") },
                onCategorySpend = { navController.navigate("category_spend") },
                onMenu = { navController.navigate("menu") },
            )
        }
        composable("menu") {
            // I kept navigation in this file; Menu only reports which item was tapped.
            MenuScreen(
                onBack = { navController.popBackStack() },
                onAchievements = { navController.navigate("achievements") },
                onWhatItWorth = { navController.navigate("what_it_worth") },
                onDailySpend = { navController.navigate("daily_spend") },
                onCategorySpend = { navController.navigate("category_spend") },
                onAddSavingGoal = { navController.navigate("add_saving_goal") },
                onRemoveSavingGoal = { navController.navigate("remove_saving_goal") },
                onUpdateSavingGoal = { navController.navigate("update_saving_goal") },
                syncStatus = syncStatus,

                // I ended the session so the login graph replaced this account.
                onLogout = onLogout,
            )
        }
        composable("achievements") {
            AchievementsScreen(userId = userId, onBack = { navController.popBackStack() })
        }
        composable("what_it_worth") {
            WhatItWorthScreen(
                userId = userId,
                onBack = { navController.popBackStack() },
                onAchievements = { navController.navigate("achievements") },
            )
        }
        composable("daily_spend") {
            // I passed the signed-in account so the graph loads that user's expenses.
            DailySpendingGraphScreen(userId = userId, onBack = { navController.popBackStack() })
        }
        composable("category_spend") {
            CategorySpendingSummaryScreen(userId = userId, onBack = { navController.popBackStack() })
        }
        composable("add_saving_goal") {
            AddSavingGoalScreen(userId = userId, onBack = { navController.popBackStack() })
        }
        composable("update_saving_goal") {
            UpdateSavingGoalScreen(userId = userId, onBack = { navController.popBackStack() })
        }
        composable("remove_saving_goal") {
            RemoveSavingGoalScreen(userId = userId, onBack = { navController.popBackStack() })
        }
        composable("add_expense") {
            AddExpenseScreen(
                userId = userId,
                onBack = { navController.popBackStack() },
                onCategories = { navController.navigate("categories") },
            )
        }
        composable("history") {
            HistoryScreen(
                userId = userId,
                onBack = { navController.popBackStack() },
                onExpenseSelected = { expenseId ->
                    navController.navigate("expense_details/$expenseId")
                },
            )
        }
        composable("expense_details/{expenseId}") { entry ->
            ExpenseDetailsScreen(
                userId = userId,
                expenseId = entry.arguments?.getString("expenseId")?.toLongOrNull(),
                onEdit = { expenseId -> navController.navigate("edit_expense/$expenseId") },
                onBack = {
                    // I returned to History by route name in case Details was not opened from that screen.
                    if (!navController.popBackStack("history", inclusive = false)) {
                        navController.navigate("history") {
                            popUpTo("expense_details/{expenseId}") { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
            )
        }
        composable("edit_expense/{expenseId}") { entry ->
            AddExpenseScreen(
                userId = userId,
                expenseId = entry.arguments?.getString("expenseId")?.toLongOrNull() ?: -1L,
                onBack = { navController.popBackStack() },
                onCategories = { navController.navigate("categories") },
            )
        }
        composable("add_category") {
            AddCategoryScreen(userId = userId, onBack = { navController.popBackStack() })
        }
        composable("categories") {
            RemoveCategoryScreen(
                userId = userId,
                onBack = { navController.popBackStack() },
                onAddCategory = { navController.navigate("add_category") },
            )
        }
    }
}
