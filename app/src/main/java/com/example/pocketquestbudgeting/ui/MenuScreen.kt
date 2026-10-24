package com.example.pocketquestbudgeting.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.pocketquestbudgeting.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.tooling.preview.Preview

private val MenuBg = Color(0xFFF4F7F6)

@Composable
// I used callbacks instead of NavController so this screen does not own routes.
fun MenuScreen(onBack: () -> Unit,
               onAchievements: () -> Unit,
               onWhatItWorth: () -> Unit,
               onDailySpend: () -> Unit,
               onCategorySpend: () -> Unit,
               onAddSavingGoal: () -> Unit,
               onRemoveSavingGoal: () -> Unit,
               onLogout: () -> Unit,
               onUpdateSavingGoal: () -> Unit = {},) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MenuBg),
    ) {
        Image(
            painter = painterResource(R.drawable.piggy_login_logo),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            alpha = 0.50f,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
        )
        Column(modifier = Modifier.padding(24.dp)) {
        TextButton(onClick = onBack) {
            Text("Back")
        }
        Text("Pocket Quest Budgeting")
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
        Button(onClick = onUpdateSavingGoal, modifier = Modifier.fillMaxWidth()) {
            Text("Update saving goal")
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


}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun MenuScreenPreview() {
    MaterialTheme {
        MenuScreen(
            onBack = {},
            onAchievements = {},
            onWhatItWorth = {},
            onDailySpend = {},
            onCategorySpend = {},
            onAddSavingGoal = {},
            onRemoveSavingGoal = {},
            onLogout = {},
        )
    }
}