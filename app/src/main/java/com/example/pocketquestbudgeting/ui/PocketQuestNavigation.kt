package com.example.pocketquestbudgeting.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun PocketQuestNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "login",
        modifier = modifier,
    ) {
        composable("login") {
            LoginScreen(
                onLogin = {
                    // I left credential checks out while login is still a demo.
                    navController.navigate("dashboard") {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable("dashboard") {
            DashboardScreen(
                onAddExpense = { navController.navigate("add_expense") },
                onHistory = { navController.navigate("history") },
            )
        }
        composable("add_expense") {
            AddExpenseScreen(onBack = { navController.popBackStack() })
        }
        composable("history") {
            HistoryScreen(onBack = { navController.popBackStack() })
        }
    }
}
