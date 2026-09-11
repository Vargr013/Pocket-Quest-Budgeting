package com.example.pocketquestbudgeting.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.pocketquestbudgeting.R
import com.example.pocketquestbudgeting.data.DatabaseProvider
import com.example.pocketquestbudgeting.data.ExpenseEntity
import com.example.pocketquestbudgeting.data.activeUserId
import com.example.pocketquestbudgeting.data.copyReceiptToAppStorage
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val ScreenBg = Color(0xFFF4F7F6)
private val TextPrimary = Color(0xFF1A2B28)
private val TextSecondary = Color(0xFF6B7C78)
private val CardWhite = Color(0xFFFFFFFF)
private val FieldBg = Color(0xFFE8EEEC)
private val FieldBgSoft = Color(0x69E8EEEC) // ~41% opacity of #E8EEEC
private val PrefixBg = Color(0xFFD9D9D9)
private val Teal = Color(0xFF0F6B5C)
private val ButtonTeal = Color(0xFF2A9D8F)
private val CardShape = RoundedCornerShape(20.dp)

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

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = FieldBg,
        unfocusedContainerColor = FieldBgSoft,
        disabledContainerColor = FieldBgSoft,
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        errorBorderColor = Color(0xFFD64545),
        focusedLabelColor = TextPrimary,
        unfocusedLabelColor = TextPrimary,
        cursorColor = Teal,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header: keep Back (same onBack), Figma title styling
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("☰", fontSize = 28.sp, color = Teal)
            }
            Text(
                text = "Add Money",
                fontSize = 36.sp,
                lineHeight = 44.sp,
                color = TextPrimary,
            )
        }

        // Current Balance card (visual only — same as Figma)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Current Balance", fontSize = 20.sp, color = TextPrimary)
                Text("R12 654,00", fontSize = 20.sp, color = TextPrimary)
            }
        }

        // Main form card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("+", fontSize = 22.sp, color = Teal)
                    Spacer(Modifier.size(8.dp))
                    Text("Add Money", fontSize = 20.sp, color = TextPrimary)
                }

                // Amount with R prefix look
                Text("Amount", fontSize = 15.sp, color = TextPrimary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(FieldBg, CardShape),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                PrefixBg,
                                RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
                            )
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("R", fontSize = 20.sp, color = TextPrimary)
                    }
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        placeholder = {
                            Text("Enter Amount", fontSize = 12.sp, color = TextSecondary)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = fieldColors,
                        modifier = Modifier.weight(1f),
                    )
                }

                // Note = your description field (same state / save)
                Text("Note", fontSize = 12.sp, color = TextPrimary)
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = {
                        Text("e.g. Salary top-up", fontSize = 12.sp, color = TextSecondary)
                    },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Category (same state / validation / save)
                Text("Category", fontSize = 12.sp, color = TextPrimary)
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = {
                        categoryName = it
                        categoryError = null
                    },
                    placeholder = {
                        Text("Select Category", fontSize = 12.sp, color = TextSecondary)
                    },
                    isError = categoryError != null,
                    supportingText = { categoryError?.let { Text(it) } },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Kept for functionality — still saved the same way
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = startTime,
                    onValueChange = { startTime = it },
                    label = { Text("Start time (HH:mm)") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = endTime,
                    onValueChange = { endTime = it },
                    label = { Text("End time (HH:mm)") },
                    singleLine = true,
                    colors = fieldColors,
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FieldBg,
                            contentColor = TextPrimary,
                        ),
                        shape = CardShape,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.add_photo))
                    }
                }
            }
        }

        // Figma teal "Add Money" button — same save logic as your Save button
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
                        // I reused the shared category checks when saving expenses.
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
            shape = CardShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = ButtonTeal,
                contentColor = TextPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(41.dp),
        ) {
            Text(
                text = if (saving) "Saving…" else "＋  Add Money",
                fontSize = 20.sp,
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AddExpenseScreenPreview() {
    MaterialTheme {
        AddExpenseScreen(onBack = {})
    }
}