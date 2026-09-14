package com.example.pocketquestbudgeting.ui

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
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "login",
        modifier = modifier,
    ) {
        composable("login") {
            val context = LocalContext.current
            val db = remember(context) { DatabaseProvider.get(context) }
            val scope = rememberCoroutineScope()
            var error by remember { mutableStateOf<String?>(null) }

            LoginScreen(
                errorMessage = error,
                onLogin = { username, password ->
                    if (username.isBlank() || password.isBlank()) {
                        error = "Enter a username and password"
                        return@LoginScreen
                    }
                    scope.launch {
                        val user = db.userDao().findByCredentials(username.trim(), password)
                        if (user != null) {
                            error = null
                            navController.navigate("dashboard") {
                                launchSingleTop = true
                            }
                        } else {
                            error = "Username or password is incorrect"
                        }
                    }
                },
                onRegister = { navController.navigate("register") },
            )
        }
        composable("register") {
            val context = LocalContext.current
            val db = remember(context) { DatabaseProvider.get(context) }
            val scope = rememberCoroutineScope()
            var error by remember { mutableStateOf<String?>(null) }

            RegisterScreen(
                errorMessage = error,
                onBackToLogin = { navController.popBackStack() },
                onRegister = { username, password, confirmPassword ->
                    when {
                        username.isBlank() || password.isBlank() ->
                            error = "Enter a username and password"
                        password != confirmPassword ->
                            error = "Passwords do not match"
                        else -> scope.launch {
                            error = null
                            when (db.userDao().register(username, password)) {
                                RegisterResult.USERNAME_TAKEN ->
                                    error = "That username is already taken"
                                RegisterResult.CREATED ->
                                    navController.popBackStack()
                            }
                        }
                    }
                },
            )
        }
        composable("dashboard") {
            DashboardScreen(
                onAddExpense = { navController.navigate("add_expense") },
                onHistory = { navController.navigate("history") },
                onCategories = { navController.navigate("categories") },
            )
        }
        composable("add_expense") {
            AddExpenseScreen(
                onBack = { navController.popBackStack() },
                onCategories = { navController.navigate("categories") },
            )
        }
        composable("history") {
            HistoryScreen(onBack = { navController.popBackStack() })
        }
        composable("add_category") {
            AddCategoryScreen(onBack = { navController.popBackStack() })
        }
        composable("categories") {
            RemoveCategoryScreen(
                onBack = { navController.popBackStack() },
                onAddCategory = { navController.navigate("add_category") },
            )
        }
    }
}
