package com.example.pocketquestbudgeting.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.pocketquestbudgeting.R

@Composable
fun DashboardScreen(onAddExpense: () -> Unit,
                    onHistory: () -> Unit,
                    onCategories: () -> Unit,) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.dashboard),
            style = MaterialTheme.typography.headlineMedium,
        )
        val onLogin = null
        Button(onClick = onAddExpense, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.add_expense))
        }
        Button(onClick = onHistory, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.history))
        }
        Button(onClick = onCategories, modifier = Modifier.fillMaxWidth()) {
            Text("Categories")
        }
    }
}
