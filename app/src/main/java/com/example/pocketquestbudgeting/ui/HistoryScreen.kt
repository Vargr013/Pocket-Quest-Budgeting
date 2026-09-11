package com.example.pocketquestbudgeting.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.pocketquestbudgeting.R
import com.example.pocketquestbudgeting.data.activeUserId
import com.example.pocketquestbudgeting.data.DatabaseProvider
import com.example.pocketquestbudgeting.data.ExpenseEntity
import java.io.File
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.tooling.preview.Preview
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var expenses by remember { mutableStateOf<List<ExpenseEntity>>(emptyList()) }
    var selectedPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val db = DatabaseProvider.get(context)
        // I reused the save flow's user lookup to keep history scoped to that user.
        expenses = db.expenseDao().getForUser(db.activeUserId())
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.back))
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(expenses) { expense ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${expense.description}  ${expense.date}",
                        modifier = Modifier.weight(1f),
                    )
                    val photoPath = expense.receiptImageUri
                    if (!photoPath.isNullOrBlank()) {
                        AsyncImage(
                            model = File(photoPath),
                            contentDescription = stringResource(R.string.receipt_photo),
                            modifier = Modifier
                                .size(56.dp)
                                .clickable { selectedPath = photoPath },
                        )
                    }
                }
            }
        }
    }
    if (selectedPath != null) {
        AlertDialog(
            onDismissRequest = { selectedPath = null },
            confirmButton = {
                TextButton(onClick = { selectedPath = null }) {
                    Text(stringResource(R.string.close))
                }
            },
            text = {
                AsyncImage(
                    model = File(selectedPath!!),
                    contentDescription = stringResource(R.string.receipt_photo),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HistoryScreenPreview() {
    MaterialTheme {
        HistoryScreen(onBack = {})
    }
}