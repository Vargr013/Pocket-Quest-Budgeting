package com.example.pocketquestbudgeting.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pocketquestbudgeting.data.DatabaseProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val ScreenBg = Color(0xFFF4F7F6)
private val TextPrimary = Color(0xFF1A2B28)
private val TextSecondary = Color(0xFF6B7C78)
private val CardWhite = Color(0xFFFFFFFF)
private val FieldBg = Color(0xFFE8EEEC)
private val FieldBgSoft = Color(0x69E8EEEC)
private val PrefixBg = Color(0xFFD9D9D9)
private val Teal = Color(0xFF0F6B5C)
private val ButtonTeal = Color(0xFF2A9D8F)
private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun AddCategoryScreen(userId: Long, onBack: () -> Unit = {}) {
    var categoryName by rememberSaveable { mutableStateOf("") }
    var minBudget by rememberSaveable { mutableStateOf("") }
    var maxBudget by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) {
            TextButton(
                onClick = onBack,
                enabled = !saving,
                modifier = Modifier.align(Alignment.CenterStart),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            ) {
                Text("Back", color = Teal, fontSize = 16.sp)
            }
            Text(
                text = "Add Category",
                fontSize = 36.sp,
                lineHeight = 44.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 56.dp),
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
                    Text("＋", fontSize = 22.sp, color = Teal)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Add Category", fontSize = 20.sp, color = TextPrimary)
                }

                Text("Category Name", fontSize = 15.sp, color = TextPrimary)
                PlainField(
                    value = categoryName,
                    onValueChange = { categoryName = it; error = null },
                    enabled = !saving,
                    placeholder = "e.g. Emergency Cash",
                    background = FieldBgSoft,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Minimum Monthly Budget", fontSize = 15.sp, color = TextPrimary)
                AmountField(
                    value = minBudget,
                    onValueChange = { minBudget = it; error = null },
                    enabled = !saving,
                )

                Text("Maximum Monthly Budget", fontSize = 15.sp, color = TextPrimary)
                AmountField(
                    value = maxBudget,
                    onValueChange = { maxBudget = it; error = null },
                    enabled = !saving,
                )
                Text(
                    "Use a dot for decimals, e.g. 12.34. Enter 0 if a limit is not set.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                )

                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }

        Button(
            onClick = {
                if (saving) return@Button
                val name = categoryName.trim()
                val minCents = parseBudgetCents(minBudget)
                val maxCents = parseBudgetCents(maxBudget)
                when {
                    name.isBlank() -> error = "Enter a category name."
                    minBudget.isBlank() -> error = "Enter a minimum monthly budget."
                    maxBudget.isBlank() -> error = "Enter a maximum monthly budget."
                    minCents == null || maxCents == null ->
                        error = "Enter amounts that are zero or more, using a dot and up to 2 decimal places."
                    minCents > maxCents -> error = "Minimum cannot be greater than the maximum."
                    else -> {
                        saving = true
                        error = null
                        scope.launch {
                            try {
                                val db = DatabaseProvider.get(context)
                                val id = userId
                                if (db.categoryDao().create(id, name, minCents, maxCents) == null) {
                                    error = "You already have a category with that name."
                                } else {
                                    onBack()
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (invalid: IllegalArgumentException) {
                                error = invalid.message ?: "Enter amounts that are zero or more."
                            } catch (_: Exception) {
                                error = "Could not create the category. Please try again."
                            } finally {
                                saving = false
                            }
                        }
                    }
                }
            },
            enabled = !saving,
            shape = CardShape,
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ButtonTeal,
                contentColor = TextPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 315.dp)
                .height(41.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (saving) "Saving..." else "＋  Add Category", fontSize = 20.sp)
            }
        }
        TextButton(onClick = onBack, enabled = !saving) { Text("Back to Categories") }
    }
}

@Composable
private fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(35.dp)
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
                .height(35.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("R", fontSize = 16.sp, color = TextPrimary)
        }
        PlainField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            placeholder = "Enter Amount",
            keyboardType = KeyboardType.Decimal,
            background = Color.Transparent,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    background: Color = FieldBgSoft,
    height: Dp = 35.dp,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .height(height)
            .background(background, CardShape)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(placeholder, fontSize = 12.sp, color = TextSecondary)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            enabled = enabled,
            textStyle = TextStyle(
                fontSize = 12.sp,
                color = TextPrimary,
                lineHeight = 12.sp,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            cursorBrush = SolidColor(Teal),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AddCategoryScreenPreview() {
    MaterialTheme {
        AddCategoryScreen(userId = 0L)
    }
}
