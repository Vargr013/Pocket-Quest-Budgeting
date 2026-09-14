package com.example.pocketquestbudgeting.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MenuScreen(onBack: () -> Unit,
               onAchievements: () -> Unit,
               onWhatItWorth: () -> Unit,
               onDailySpend: () -> Unit,
               onCategorySpend: () -> Unit,
               onAddSavingGoal: () -> Unit,
               onRemoveSavingGoal: () -> Unit,
               onLogout: () -> Unit,) {
    Column(modifier = Modifier.padding(24.dp)) {
        TextButton(onClick = onBack) {
            Text("Back")
        }
        Text("Menu")
        Button(
            onClick = onWhatItWorth,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("What it's worth")
        }
        Button(
            onClick = onDailySpend,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Daily spend")
        }
        Button(
            onClick = onCategorySpend,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Category spend")
        }
        Button(
            onClick = onAddSavingGoal,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Add saving goal")
        }
        Button(
            onClick = onRemoveSavingGoal,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Remove saving goal")
        }
        Button(
            onClick = onAchievements,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Achievements")
        }
        TextButton(onClick = onLogout) {
            Text("Log out")
        }
    }
}