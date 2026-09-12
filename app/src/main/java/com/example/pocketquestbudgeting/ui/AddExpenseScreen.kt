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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
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
import androidx.compose.ui.text.style.TextAlign

private val ScreenBg = Color(0xFFF4F7F6)
private val TextPrimary = Color(0xFF1A2B28)
private val TextSecondary = Color(0xFF6B7C78)
private val CardWhite = Color(0xFFFFFFFF)
private val FieldBg = Color(0xFFE8EEEC)
private val FieldBgSoft = Color(0x69E8EEEC)
private val PrefixBg = Color(0xFFD9D9D9)
private val Teal = Color(0xFF0F6B5C)
private val ButtonTeal = Color(0xFF2A9D8F)
private val ErrorRed = Color(0xFFD64545)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) {
            Text(
                text = "☰",
                fontSize = 28.sp,
                color = Teal,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 4.dp),
            )
            Text(
                text = "Add Expense",
                fontSize = 36.sp,
                lineHeight = 44.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
        }

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

                Text("Amount", fontSize = 15.sp, color = TextPrimary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(31.dp)
                        .background(FieldBg, CardShape),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                PrefixBg,
                                RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
                            )
                            .padding(horizontal = 14.dp)
                            .height(31.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("R", fontSize = 16.sp, color = TextPrimary)
                    }
                    PlainField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        placeholder = "Enter Amount",
                        keyboardType = KeyboardType.Decimal,
                        background = Color.Transparent,
                        modifier = Modifier.weight(1f),
                    )
                }

                Text("Note", fontSize = 12.sp, color = TextPrimary)
                PlainField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = "e.g. Salary top-up",
                    background = FieldBgSoft,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Category", fontSize = 12.sp, color = TextPrimary)
                PlainField(
                    value = categoryName,
                    onValueChange = {
                        categoryName = it
                        categoryError = null
                    },
                    placeholder = "Select Category",
                    background = FieldBgSoft,
                    isError = categoryError != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                categoryError?.let {
                    Text(it, fontSize = 11.sp, color = ErrorRed)
                }

                Text("Date (YYYY-MM-DD)", fontSize = 12.sp, color = TextPrimary)
                PlainField(
                    value = date,
                    onValueChange = { date = it },
                    placeholder = "YYYY-MM-DD",
                    background = FieldBgSoft,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Start time (HH:mm)", fontSize = 12.sp, color = TextPrimary)
                PlainField(
                    value = startTime,
                    onValueChange = { startTime = it },
                    placeholder = "HH:mm",
                    background = FieldBgSoft,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("End time (HH:mm)", fontSize = 12.sp, color = TextPrimary)
                PlainField(
                    value = endTime,
                    onValueChange = { endTime = it },
                    placeholder = "HH:mm",
                    background = FieldBgSoft,
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

@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    background: Color = FieldBgSoft,
    height: Dp = 31.dp,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(fontSize = 12.sp, color = TextPrimary),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        cursorBrush = SolidColor(Teal),
        modifier = modifier
            .height(height)
            .background(background, CardShape)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 12.sp,
                        color = if (isError) ErrorRed else TextSecondary,
                    )
                }
                inner()
            }
        },
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AddExpenseScreenPreview() {
    MaterialTheme {
        AddExpenseScreen(onBack = {})
    }
}