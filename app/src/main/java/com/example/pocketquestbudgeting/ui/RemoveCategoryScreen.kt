package com.example.pocketquestbudgeting.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pocketquestbudgeting.data.CategoryChangeResult
import com.example.pocketquestbudgeting.data.CategoryEntity
import com.example.pocketquestbudgeting.data.DatabaseProvider
import com.example.pocketquestbudgeting.data.activeUserId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val ScreenBg = Color(0xFFF4F7F6)
private val TextPrimary = Color(0xFF1A2B28)
private val TextSecondary = Color(0xFF6B7C78)
private val CardWhite = Color(0xFFFFFFFF)
private val Teal = Color(0xFF0F6B5C)
private val RemoveOrange = Color(0xFFC45C26)
private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun RemoveCategoryScreen(onBack: () -> Unit = {}, onAddCategory: () -> Unit = {}) {
    val context = LocalContext.current
    val db = remember(context) { DatabaseProvider.get(context) }
    val scope = rememberCoroutineScope()
    var userId by remember { mutableStateOf<Long?>(null) }
    var categories by remember { mutableStateOf<List<CategoryEntity>>(emptyList()) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf<CategoryAction?>(null) }
    var categoryName by rememberSaveable { mutableStateOf("") }
    var minBudget by rememberSaveable { mutableStateOf("") }
    var maxBudget by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    val selected = categories.firstOrNull { it.id == selectedId }

    LaunchedEffect(reload) {
        loading = true
        try {
            val id = db.activeUserId()
            categories = db.categoryDao().getForUser(id)
            userId = id
            if (categories.none { it.id == selectedId }) selectedId = null
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "Could not refresh categories. Please retry before making changes."
        } finally {
            loading = false
        }
    }

    fun saveChange() {
        val id = userId ?: return
        val category = selected ?: return
        val currentAction = action ?: return
        if (saving) return
        saving = true
        error = null
        scope.launch {
            try {
                val result = when (currentAction) {
                    CategoryAction.RENAME -> db.categoryDao().rename(id, category.id, categoryName)
                    CategoryAction.SET_BUDGETS -> {
                        val minCents = parseBudgetCents(minBudget)
                            ?: throw IllegalArgumentException("Enter a minimum monthly budget using a dot and up to 2 decimal places.")
                        val maxCents = parseBudgetCents(maxBudget)
                            ?: throw IllegalArgumentException("Enter a maximum monthly budget using a dot and up to 2 decimal places.")
                        db.categoryDao().setBudgets(id, category.id, minCents, maxCents)
                    }
                    CategoryAction.DELETE -> db.categoryDao().delete(id, category.id)
                }
                when (result) {
                    CategoryChangeResult.SUCCESS -> {
                        action = null
                        reload++
                    }
                    CategoryChangeResult.DUPLICATE -> error = "You already have a category with that name."
                    CategoryChangeResult.NOT_FOUND -> {
                        action = null
                        error = "This category is no longer available. Please retry the list."
                    }
                    CategoryChangeResult.IN_USE -> error = "This category has expenses. Move or delete those expenses before deleting it."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            }             catch (invalid: IllegalArgumentException) {
                error = invalid.message ?: "Could not save the category change."
            } catch (_: Exception) {
                error = "Could not save the category change. Please try again."
            } finally {
                saving = false
            }
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
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            TextButton(
                onClick = onBack,
                enabled = !saving,
                modifier = Modifier.align(Alignment.CenterStart),
            ) { Text("Back", color = Teal) }
            Text(
                text = "Categories",
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
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Manage categories", fontSize = 20.sp, color = TextPrimary)
                }

                Text("Select Category", fontSize = 15.sp, color = TextPrimary)
                Box(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { expanded = true },
                        enabled = !loading && !saving && categories.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(selected?.name ?: "Select category") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    selectedId = category.id
                                    expanded = false
                                    error = null
                                },
                            )
                        }
                    }
                }
            }
        }

        TextButton(onClick = onAddCategory, enabled = !saving) { Text("Create category") }
        Text("Using the demo user. Login is not connected yet.", color = TextSecondary)
        if (loading) Text("Loading categories...")
        else if (userId != null && categories.isEmpty()) Text("No categories yet.")
        if (action == null) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (error != null) TextButton(onClick = { reload++ }) { Text("Retry") }
        }
        TextButton(
            enabled = selected != null && !loading && !saving && error == null,
            onClick = {
                categoryName = selected?.name.orEmpty()
                error = null
                action = CategoryAction.RENAME
            },
        ) { Text("Rename category") }
        TextButton(
            enabled = selected != null && !loading && !saving && error == null,
            onClick = {
                minBudget = centsToBudgetInput(selected?.minMonthlyBudget ?: 0)
                maxBudget = centsToBudgetInput(selected?.maxMonthlyBudget ?: 0)
                error = null
                action = CategoryAction.SET_BUDGETS
            },
        ) { Text("Set budgets") }
        Button(
            enabled = selected != null && !loading && !saving && error == null,
            onClick = { error = null; action = CategoryAction.DELETE },
            shape = CardShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = RemoveOrange,
                contentColor = TextPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 315.dp)
                .height(41.dp),
        ) {
            Text("Remove Category", fontSize = 20.sp)
        }
    }
    if (action != null && selected != null) {
        val currentAction = action
        AlertDialog(
            onDismissRequest = { if (!saving) { action = null; error = null } },
            title = {
                Text(
                    when (currentAction) {
                        CategoryAction.RENAME -> "Rename category"
                        CategoryAction.SET_BUDGETS -> "Set monthly budgets"
                        else -> "Delete category?"
                    },
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (currentAction) {
                        CategoryAction.RENAME -> {
                            OutlinedTextField(
                                value = categoryName,
                                onValueChange = { categoryName = it; error = null },
                                label = { Text("Category name") },
                                singleLine = true,
                                enabled = !saving,
                                isError = error != null,
                            )
                        }
                        CategoryAction.SET_BUDGETS -> {
                            OutlinedTextField(
                                value = minBudget,
                                onValueChange = { minBudget = it; error = null },
                                label = { Text("Minimum monthly budget") },
                                singleLine = true,
                                enabled = !saving,
                                isError = error != null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            )
                            OutlinedTextField(
                                value = maxBudget,
                                onValueChange = { maxBudget = it; error = null },
                                label = { Text("Maximum monthly budget") },
                                singleLine = true,
                                enabled = !saving,
                                isError = error != null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            )
                            Text("Use a dot for decimals, e.g. 12.34. Enter 0 if a limit is not set.")
                        }
                        else -> Text("Delete \"${selected.name}\"? This cannot be undone.")
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = { saveChange() }, enabled = !saving) {
                    Text(
                        when {
                            saving -> "Saving..."
                            currentAction == CategoryAction.DELETE -> "Delete"
                            else -> "Save"
                        },
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { action = null; error = null }, enabled = !saving) { Text("Cancel") }
            },
        )
    }
}

private enum class CategoryAction { RENAME, SET_BUDGETS, DELETE }

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun RemoveCategoryScreenPreview() {
    MaterialTheme {
        RemoveCategoryScreen()
    }
}