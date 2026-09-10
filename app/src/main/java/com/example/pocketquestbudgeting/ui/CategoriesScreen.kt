package com.example.pocketquestbudgeting.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.pocketquestbudgeting.data.CategoryEntity
import com.example.pocketquestbudgeting.data.DatabaseProvider
import com.example.pocketquestbudgeting.data.activeUserId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember(context) { DatabaseProvider.get(context) }
    val scope = rememberCoroutineScope()
    var userId by remember { mutableStateOf<Long?>(null) }
    var categories by remember { mutableStateOf<List<CategoryEntity>>(emptyList()) }
    var name by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        loading = true
        error = null
        try {
            val id = db.activeUserId()
            categories = db.categoryDao().getForUser(id)
            userId = id
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "Could not load categories. Please try again."
        } finally {
            loading = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TextButton(onClick = onBack, enabled = !saving) { Text("Back") }
            Text("Categories", style = MaterialTheme.typography.headlineMedium)
            Text("Using the demo user. Login is not connected yet.")
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    error = null
                },
                label = { Text("Category name") },
                singleLine = true,
                enabled = userId != null && !saving,
                isError = error != null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = userId != null && !saving,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val id = userId ?: return@Button
                    if (saving) return@Button
                    val trimmed = name.trim()
                    if (trimmed.isBlank()) {
                        error = "Enter a category name."
                        return@Button
                    }
                    saving = true
                    error = null
                    scope.launch {
                        try {
                            if (db.categoryDao().create(id, trimmed) == null) {
                                error = "You already have a category with that name."
                            } else {
                                name = ""
                            }
                            categories = db.categoryDao().getForUser(id)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            error = "Could not save or refresh categories. Please try again."
                        } finally {
                            saving = false
                        }
                    }
                },
            ) { Text(if (saving) "Saving…" else "Create category") }
        }
        if (loading) {
            item { Text("Loading categories…") }
        } else if (userId == null) {
            item { TextButton(onClick = { reload++ }) { Text("Retry") } }
        } else if (categories.isEmpty()) {
            item { Text("No categories yet. Enter a name above to create your first category.") }
        }
        items(categories, key = { it.id }) { category -> Text(category.name) }
    }
}
