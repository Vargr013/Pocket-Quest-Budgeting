package com.example.pocketquestbudgeting.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import com.example.pocketquestbudgeting.R

@Composable
fun AddExpenseScreen(onBack: () -> Unit) {
    TextButton(onClick = onBack) {
        Text(stringResource(R.string.back))
    }
}