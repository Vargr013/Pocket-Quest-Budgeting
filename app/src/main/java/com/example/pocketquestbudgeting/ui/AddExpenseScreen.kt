package com.example.pocketquestbudgeting.ui

import androidx.room.withTransaction
import androidx.activity.compose.BackHandler
import java.math.BigDecimal
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.LaunchedEffect
import com.example.pocketquestbudgeting.data.CategoryEntity
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
import com.example.pocketquestbudgeting.R
import com.example.pocketquestbudgeting.data.DatabaseProvider
import com.example.pocketquestbudgeting.data.ExpenseEntity
import com.example.pocketquestbudgeting.data.activeUserId
import com.example.pocketquestbudgeting.data.copyReceiptToAppStorage
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
fun AddExpenseScreen(
    onBack: () -> Unit,
    onCategories: () -> Unit = {},
    expenseId: Long? = null,
) {
    val editing = expenseId != null
    var loadedExpense by rememberSaveable(expenseId) { mutableStateOf(false) }
    var expenseError by remember { mutableStateOf<String?>(null) }
    var loadedUserId by rememberSaveable(expenseId) { mutableStateOf<Long?>(null) }
    var receiptPath by rememberSaveable { mutableStateOf<String?>(null) }
    var date by rememberSaveable { mutableStateOf(todayExpenseDate()) }
    var startTime by rememberSaveable { mutableStateOf("") }
    var endTime by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var selectedCategoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var categories by remember { mutableStateOf<List<CategoryEntity>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }
    var loadingCategories by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var receiptError by remember { mutableStateOf<String?>(null) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    // I blocked Back while saving so an update cannot finish after cancelling.
    BackHandler(enabled = editing || saving) { if (!saving) onBack() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null && !saving) {
            try {
                val copiedPath = copyReceiptToAppStorage(context, uri)
                if (copiedPath != null) {
                    receiptPath = copiedPath
                    receiptError = null
                } else receiptError = "Could not attach the receipt. Please try again."
            } catch (_: Exception) {
                receiptError = "Could not attach the receipt. Please try again."
            }
        }
    }

    LaunchedEffect(expenseId, reload) {
        loadingCategories = true
        loadError = null
        expenseError = null
        try {
            val db = DatabaseProvider.get(context)
            val userId = db.activeUserId()
            if (editing && !loadedExpense) {
                val expense = expenseId?.takeIf { it > 0 }?.let {
                    db.expenseDao().getForUserById(userId, it)
                }
                if (expense == null) {
                    expenseError = "Expense unavailable. It may have been removed or may not belong to the current user."
                    return@LaunchedEffect
                }
                // I filled this once so reloading categories would not reset my edits.
                amountText = BigDecimal.valueOf(expense.amount, 2).toPlainString()
                date = expense.date
                startTime = expense.startTime
                endTime = expense.endTime
                description = expense.description
                selectedCategoryId = expense.categoryId
                receiptPath = expense.receiptImageUri
                loadedUserId = userId
                loadedExpense = true
            }
            db.categoryDao().observeForUser(userId).collect { savedCategories ->
                categories = savedCategories
                loadingCategories = false
                if (selectedCategoryId != null && savedCategories.none { it.id == selectedCategoryId }) {
                    selectedCategoryId = null
                    errors = errors + ("category" to "This category is no longer available. Select another one.")
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (editing && !loadedExpense) {
                expenseError = "Could not load this expense. Please retry or go back."
            } else loadError = "Could not load categories. Please retry."
        } finally {
            loadingCategories = false
        }
    }

    if (editing && (!loadedExpense || expenseError != null)) {
        Column(Modifier.fillMaxSize().background(ScreenBg).padding(24.dp)) {
            TextButton(onClick = onBack) { Text("Back to Details") }
            Text("Edit Expense", style = MaterialTheme.typography.titleLarge)
            Text(expenseError ?: "Loading expense…")
            if (expenseError != null) {
                TextButton(onClick = { reload++ }) { Text("Retry") }
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {

        if (editing) {
            TextButton(onClick = onBack, enabled = !saving) { Text("Back to Details") }
        }
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
                text = if (editing) "Edit Expense" else "Add Expense",
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
                    Text(if (editing) "Edit expense" else "Add Money", fontSize = 20.sp, color = TextPrimary)
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
                        onValueChange = { amountText = it; errors = errors - "amount" },
                        enabled = !saving,
                        placeholder = "Enter Amount",
                        keyboardType = KeyboardType.Decimal,
                        background = Color.Transparent,
                        modifier = Modifier.weight(1f),
                    )
                }

                Text("Use a dot for decimals, e.g. 12.34.", fontSize = 11.sp, color = TextSecondary)
                FieldError(errors["amount"])

                Text("Note", fontSize = 12.sp, color = TextPrimary)
                PlainField(
                    value = description,
                    onValueChange = { description = it; errors = errors - "description" },
                    enabled = !saving,
                    placeholder = "e.g. Salary top-up",
                    background = FieldBgSoft,
                    modifier = Modifier.fillMaxWidth(),
                )
                FieldError(errors["description"])

                Text("Category", fontSize = 12.sp, color = TextPrimary)
                Box(modifier = Modifier.fillMaxWidth().background(FieldBgSoft, CardShape)) {
                    TextButton(
                        onClick = { expanded = true },
                        enabled = !saving && !loadingCategories && loadError == null && categories.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "Select category") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    selectedCategoryId = category.id
                                    expanded = false
                                    errors = errors - "category"
                                },
                            )
                        }
                    }
                }
                FieldError(errors["category"])
                if (loadingCategories) Text("Loading categories...", fontSize = 12.sp)
                loadError?.let {
                    FieldError(it)
                    TextButton(onClick = { reload++ }, enabled = !saving) { Text("Retry categories") }
                }
                if (!loadingCategories && loadError == null && categories.isEmpty()) {
                    Text("Create a category before saving an expense.", fontSize = 12.sp)
                }
                TextButton(onClick = onCategories, enabled = !saving) { Text("Categories") }

                Text("Date (YYYY-MM-DD)", fontSize = 12.sp, color = TextPrimary)
                PlainField(
                    value = date,
                    onValueChange = { date = it; errors = errors - "date" },
                    enabled = !saving,
                    placeholder = "YYYY-MM-DD",
                    background = FieldBgSoft,
                    modifier = Modifier.fillMaxWidth(),
                )
                FieldError(errors["date"])

                Text("Start time (HH:mm)", fontSize = 12.sp, color = TextPrimary)
                PlainField(
                    value = startTime,
                    onValueChange = { startTime = it; errors = errors - "startTime" },
                    enabled = !saving,
                    placeholder = "HH:mm",
                    background = FieldBgSoft,
                    modifier = Modifier.fillMaxWidth(),
                )
                FieldError(errors["startTime"])

                Text("End time (HH:mm)", fontSize = 12.sp, color = TextPrimary)
                PlainField(
                    value = endTime,
                    onValueChange = { endTime = it; errors = errors - "endTime" },
                    enabled = !saving,
                    placeholder = "HH:mm",
                    background = FieldBgSoft,
                    modifier = Modifier.fillMaxWidth(),
                )
                FieldError(errors["endTime"])

                FieldError(receiptError)
                if (receiptError != null) {
                    TextButton(onClick = { receiptError = null }, enabled = !saving) {
                        Text("Keep current receipt choice")
                    }
                }
                val path = receiptPath
                if (path != null) {
                    ExpenseReceipt(path)
                    TextButton(onClick = { receiptPath = null; receiptError = null }, enabled = !saving) {
                        Text(stringResource(R.string.remove_photo))
                    }
                }
                Button(
                    onClick = {
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !saving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FieldBg,
                        contentColor = TextPrimary,
                    ),
                    shape = CardShape,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (path != null) "Replace photo" else stringResource(R.string.add_photo))
                }
            }
        }

        Text("Using the demo user. Login is not connected yet.", fontSize = 12.sp, color = TextSecondary)
        FieldError(saveError)
        Button(
            onClick = {
                if (saving || receiptError != null) return@Button
                val validation = validateExpense(date, startTime, endTime, description, selectedCategoryId, amountText)
                errors = validation.errors
                saveError = null
                if (validation.errors.isNotEmpty()) return@Button
                val categoryId = selectedCategoryId ?: return@Button
                val amountCents = validation.amountCents ?: return@Button
                // I kept a copy of the entered values before saving.
                val savedDate = date.trim()
                val savedStart = startTime.trim()
                val savedEnd = endTime.trim()
                val savedDescription = description.trim()
                val savedReceipt = receiptPath
                val editUserId = loadedUserId
                saving = true
                scope.launch {
                    try {
                        val db = DatabaseProvider.get(context)
                        val userId = if (editing) requireNotNull(editUserId) else db.activeUserId()
                        // I checked the category belongs to this user and cannot be deleted while saving.
                        val saved = db.withTransaction {
                            if (db.categoryDao().getForUserById(userId, categoryId) == null) {
                                return@withTransaction -1
                            }
                            if (expenseId != null) {
                                db.expenseDao().updateForUser(
                                    userId, expenseId, categoryId, amountCents, savedDate,
                                    savedStart, savedEnd, savedDescription, savedReceipt,
                                )
                            } else {
                                db.expenseDao().insert(
                                    ExpenseEntity(
                                        userId = userId,
                                        categoryId = categoryId,
                                        amount = amountCents,
                                        date = savedDate,
                                        startTime = savedStart,
                                        endTime = savedEnd,
                                        description = savedDescription,
                                        receiptImageUri = savedReceipt,
                                    ),
                                )
                                1
                            }
                        }
                        if (saved == 1) onBack() else if (saved == 0) {
                            saveError = "Expense unavailable. No changes were saved. You can go back to Details."
                        } else {
                            selectedCategoryId = null
                            errors = errors + ("category" to "This category is no longer available. Select another one.")
                            reload++
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        saveError = "Could not save the expense to the database. Your entries and receipt are kept. Please try again."
                    } finally {
                        saving = false
                    }
                }
            },
            enabled = !saving && !loadingCategories && loadError == null && receiptError == null,
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
                text = if (saving) "Saving…" else if (editing) "Save changes" else "＋  Add Money",
                fontSize = 20.sp,
            )
        }
        if (editing) {
            TextButton(onClick = onBack, enabled = !saving) { Text("Cancel") }
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
    enabled: Boolean = true,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        enabled = enabled,
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

@Composable
private fun FieldError(message: String?) {
    message?.let { Text(it, fontSize = 12.sp, color = ErrorRed) }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AddExpenseScreenPreview() {
    MaterialTheme {
        AddExpenseScreen(onBack = {})
    }
}