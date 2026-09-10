package com.example.pocketquestbudgeting.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.pocketquestbudgeting.R
import com.example.pocketquestbudgeting.data.copyReceiptToAppStorage
import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import com.example.pocketquestbudgeting.data.activeUserId
import com.example.pocketquestbudgeting.data.DatabaseProvider
import com.example.pocketquestbudgeting.data.ExpenseEntity
import kotlinx.coroutines.CancellationException

@Composable
fun AddExpenseScreen(onBack: () -> Unit) {
    var receiptPath by rememberSaveable { mutableStateOf<String?>(null) }
    var date by rememberSaveable { mutableStateOf("") }
    var startTime by rememberSaveable { mutableStateOf("") }
    var endTime by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var categoryName by rememberSaveable { mutableStateOf("General") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var categoryError by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            receiptPath = copyReceiptToAppStorage(context, uri)
        }
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
        OutlinedTextField(
            value = date,
            onValueChange = { date = it },
            label = { Text("Date (YYYY-MM-DD)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = startTime,
            onValueChange = { startTime = it },
            label = { Text("Start time (HH:mm)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = endTime,
            onValueChange = { endTime = it },
            label = { Text("End time (HH:mm)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = categoryName,
            onValueChange = {
                categoryName = it
                categoryError = null
            },
            isError = categoryError != null,
            supportingText = { categoryError?.let { Text(it) } },
            label = { Text("Category") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text("Amount") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        val path = receiptPath
        if (path != null) {
            AsyncImage(
                model = File(path),
                contentDescription = stringResource(R.string.receipt_photo),
                modifier = Modifier.size(160.dp),
            )
            TextButton(onClick = { receiptPath = null }) {
                Text(stringResource(R.string.remove_photo))
            }
        } else {
            Button(
                onClick = {
                    pickImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.add_photo))
            }
        }
        Button(
            onClick = {
                if (saving) return@Button
                if (categoryName.isBlank()) {
                    categoryError = "Enter a category name."
                    return@Button
                }
                saving = true
                val amountCents = ((amountText.trim().toDoubleOrNull() ?: 0.0) * 100).toLong()
                scope.launch {
                    try {
                        val db = DatabaseProvider.get(context)
                        val userId = db.activeUserId()
                        val categoryId = db.categoryDao().getOrCreate(userId, categoryName)
                        db.expenseDao().insert(
                            ExpenseEntity(
                                userId = userId,
                                categoryId = categoryId,
                                amount = amountCents,
                                date = date.trim(),
                                startTime = startTime.trim(),
                                endTime = endTime.trim(),
                                description = description.trim(),
                                receiptImageUri = receiptPath,
                            ),
                        )
                        onBack()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        categoryError = "Could not save the expense. Please try again."
                    } finally {
                        saving = false
                    }
                }
            },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (saving) "Saving…" else stringResource(R.string.save))
        }
    }
}
