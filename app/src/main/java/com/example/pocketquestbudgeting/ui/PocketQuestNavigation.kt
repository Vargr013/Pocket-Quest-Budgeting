package com.example.pocketquestbudgeting.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.Text
import kotlinx.coroutines.CancellationException
import com.example.pocketquestbudgeting.data.initializeUserCategories
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.pocketquestbudgeting.data.DatabaseProvider
import com.example.pocketquestbudgeting.data.RegisterResult
import kotlinx.coroutines.launch

@Composable
fun PocketQuestNavigation(modifier: Modifier = Modifier) {
    var userId by rememberSaveable { mutableStateOf<Long?>(null) }
    var checked by remember { mutableStateOf(userId == null) }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (userId != null) {
            try {
                DatabaseProvider.get(context).initializeUserCategories(requireNotNull(userId))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                userId = null
            }
        }
        checked = true
    }
    if (!checked) {
        Text("Checking account...")
        return
    }
    // I discarded the whole navigation state when accounts changed, including saved drafts.
    key(userId) {
        AccountNavigation(userId, onLogin = { userId = it }, onLogout = { userId = null }, modifier = modifier)
    }
}

@Composable
private fun AccountNavigation(userId: Long?, onLogin: (Long) -> Unit, onLogout: () -> Unit, modifier: Modifier) {
    // I used a navigation controller and host to manage the screen routes (Google, 2026e).
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = if (userId == null) "login" else "dashboard",
        modifier = modifier,
    ) {
        if (userId == null) {
            composable("login") {
                val context = LocalContext.current
                val db = remember(context) { DatabaseProvider.get(context) }
                val scope = rememberCoroutineScope()
                var error by remember { mutableStateOf<String?>(null) }
                var busy by remember { mutableStateOf(false) }

                LoginScreen(
                    errorMessage = error,
                    onLogin = { username, password ->
                        if (busy) return@LoginScreen
                        if (username.isBlank() || password.isBlank()) {
                            error = "Enter a username and password"
                            return@LoginScreen
                        }
                        busy = true
                        scope.launch {
                            try {
                                val user = db.userDao().findByCredentials(username.trim(), password)
                                if (user == null) {
                                    error = "Username or password is incorrect"
                                } else {
                                    db.initializeUserCategories(user.id)
                                    onLogin(user.id)
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                error = "Could not log in. Please try again."
                            } finally {
                                busy = false
                            }
                        }
                    },
                    onRegister = { if (!busy) navController.navigate("register") },
                )
            }
            composable("register") {
                val context = LocalContext.current
                val db = remember(context) { DatabaseProvider.get(context) }
                val scope = rememberCoroutineScope()
                var error by remember { mutableStateOf<String?>(null) }
                var busy by remember { mutableStateOf(false) }

                RegisterScreen(
                    errorMessage = error,
                    onBackToLogin = { navController.popBackStack() },
                    onRegister = { username, password, confirmPassword ->
                        if (busy) return@RegisterScreen
                        when {
                            username.isBlank() || password.isBlank() ->
                                error = "Enter a username and password"
                            password != confirmPassword ->
                                error = "Passwords do not match"
                            else -> {
                                busy = true
                                scope.launch {
                                    try {
                                        error = null
                                        when (db.userDao().register(username, password)) {
                                            RegisterResult.USERNAME_TAKEN ->
                                                error = "That username is already taken"
                                            RegisterResult.CREATED ->
                                                navController.popBackStack()
                                        }
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        error = "Could not register. Please try again."
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        }
                    },
                )
            }
        } else {
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
                MenuScreen(
                    onBack = { navController.popBackStack() },
                    onAchievements = { navController.navigate("achievements") },
                    onWhatItWorth = { navController.navigate("what_it_worth") },
                    onDailySpend = { navController.navigate("daily_spend") },
                    onCategorySpend = { navController.navigate("category_spend") },
                    onAddSavingGoal = { navController.navigate("add_saving_goal") },
                    onRemoveSavingGoal = { navController.navigate("remove_saving_goal") },
                    onLogout = onLogout,
                )
            }
            composable("achievements") {
                AchievementsScreen(onBack = { navController.popBackStack() })
            }
            composable("what_it_worth") {
                WhatItWorthScreen(onBack = { navController.popBackStack() })
            }
            composable("daily_spend") {
                DailySpendingGraphScreen(onBack = { navController.popBackStack() })
            }
            composable("category_spend") {
                CategorySpendingSummaryScreen(userId = userId, onBack = { navController.popBackStack() })
            }
            composable("add_saving_goal") {
                AddSavingGoalScreen(onBack = { navController.popBackStack() })
            }
            composable("remove_saving_goal") {
                RemoveSavingGoalScreen(onBack = { navController.popBackStack() })
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
}
