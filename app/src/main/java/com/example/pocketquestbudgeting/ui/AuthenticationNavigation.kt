package com.example.pocketquestbudgeting.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.pocketquestbudgeting.data.AccountSession
import com.example.pocketquestbudgeting.data.AccountState

@Composable
fun AuthenticationNavigation(session: AccountSession, state: AccountState, modifier: Modifier) {
    val navController = rememberNavController()
    var help by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = state.busy || state.cloud != null) { session.logout() }
    if (state.cloud != null) {
        LinkLocalAccountScreen(session, state, modifier)
        return
    }
    NavHost(navController = navController, startDestination = "login", modifier = modifier) {
        composable("login") {
            LoginScreen(
                busy = state.busy,
                errorMessage = state.message ?: help,
                onLogin = { username, password, remember ->
                    if (!state.busy) { help = null; session.login(username, password, remember) }
                },
                onRegister = { if (!state.busy) { help = null; navController.navigate("register") } },
                onNeedHelp = { help = "Usernames are case-sensitive. First login needs internet. Old data can be connected after cloud login." },
                onForgotPassword = { help = "These username accounts have no email inbox for password resets. Keep your password safe; no automatic reset is available." },
                onCancel = { session.logout() },
            )
        }
        composable("register") {
            var validation by remember { mutableStateOf<String?>(null) }
            RegisterScreen(
                busy = state.busy,
                errorMessage = validation ?: state.message,
                onBackToLogin = { if (!state.busy) { session.logout(); navController.popBackStack() } },
                onRegister = { username, password, confirmation ->
                    validation = null
                    if (password != confirmation) validation = "Passwords do not match."
                    else if (!state.busy) session.login(username, password, remember = true, register = true)
                },
                onCancel = { session.logout() },
            )
        }
    }
}

@Composable
private fun LinkLocalAccountScreen(session: AccountSession, state: AccountState, modifier: Modifier) {
    var selectedId by remember { mutableStateOf<Long?>(null) }
    // I kept the old password in memory only, and discarded it when this linking screen left composition.
    var oldPassword by remember { mutableStateOf("") }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Connect existing local data", style = MaterialTheme.typography.headlineSmall)
        Text("Cloud username: ${state.cloud?.username}")
        Text("Choose your old local account and prove its password, or continue with a separate empty local account. Matching names do not connect accounts.")
        state.localAccounts.forEach { account ->
            TextButton(
                enabled = account.canLink && !state.busy,
                onClick = { selectedId = account.id; oldPassword = "" },
            ) {
                Text("${account.username} (local ID ${account.id})" + when {
                    !account.canLink -> " — password recovery required; data kept"
                    selectedId == account.id -> " — selected"
                    else -> ""
                })
            }
        }
        if (selectedId != null) {
            OutlinedTextField(
                value = oldPassword,
                onValueChange = { oldPassword = it },
                enabled = !state.busy,
                label = { Text("Old local password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(enabled = !state.busy, onClick = {
                val proof = oldPassword
                oldPassword = ""
                session.link(requireNotNull(selectedId), proof)
            }) { Text(if (state.busy) "Connecting..." else "Connect selected account") }
        }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("Connecting keeps the local rows and prepares them for the later import. It does not upload financial records yet.")
        Button(enabled = !state.busy, onClick = { session.useNewLocalAccount() }) { Text("Continue with a separate empty account") }
        TextButton(onClick = { oldPassword = ""; session.logout() }) { Text("Cancel and log out") }
    }
}
