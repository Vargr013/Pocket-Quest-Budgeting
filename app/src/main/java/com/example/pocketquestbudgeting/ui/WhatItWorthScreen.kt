package com.example.pocketquestbudgeting.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pocketquestbudgeting.R
import com.example.pocketquestbudgeting.data.AccountSession
import com.example.pocketquestbudgeting.data.AccountSessions
import com.example.pocketquestbudgeting.data.GamificationSync
import com.example.pocketquestbudgeting.data.GamificationSyncs
import org.json.JSONObject
import java.util.UUID
import com.example.pocketquestbudgeting.data.AchievementGroup
import com.example.pocketquestbudgeting.data.AchievementLoader
import com.example.pocketquestbudgeting.data.AchievementReport
import com.example.pocketquestbudgeting.data.Achievements
import com.example.pocketquestbudgeting.data.CustomWorthItemEntity
import com.example.pocketquestbudgeting.data.DatabaseProvider
import com.example.pocketquestbudgeting.data.WorthCatalog
import com.example.pocketquestbudgeting.data.WorthCategory
import com.example.pocketquestbudgeting.data.WorthItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val ScreenBg = Color(0xFFF4F7F6)
private val TextPrimary = Color(0xFF1A2B28)
private val TextSecondary = Color(0xFF6B7C78)
private val Teal = Color(0xFF0F6B5C)
private val ButtonTeal = Color(0xFF2A9D8F)
private val CardWhite = Color(0xFFFFFFFF)
private val PanelBg = Color(0xFFE8EEEC)
private val AccentCoral = Color(0xFFE07A5F)
private val CircleBg = Color(0xFFCFE8E3)
private val CardBorder = Color(0xFF0F6B5C)
private val ErrorRed = Color(0xFFD64545)
private val CardShape = RoundedCornerShape(20.dp)

private const val MAX_CUSTOM_NAME = 40

private enum class WorthBasis(val label: String) {
    TOTAL_SAVED("Total saved"),
    THIS_MONTH("This month so far"),
    CATEGORY_SAVINGS("Category savings"),
}

@Composable
fun WhatItWorthScreen(userId: Long, onBack: () -> Unit, onAchievements: () -> Unit = {}, session: AccountSession? = null, sync: GamificationSync? = null) {
    val inspecting = LocalInspectionMode.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf(if (inspecting) previewAchievementReport() else null) }
    var customItems by remember { mutableStateOf(if (inspecting) previewCustomItems else emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val changed = gamificationReload(userId)
    var changing by remember(userId) { mutableStateOf(false) }
    var category by rememberSaveable { mutableStateOf(if (inspecting) WorthCategory.FOOD else loadWorthCategory(context, userId)) }

    if (!inspecting) {
        LaunchedEffect(userId, reload, changed) {
            try {
                val db = DatabaseProvider.get(context)
                report = AchievementLoader.load(context, userId)
                customItems = db.gamificationDao().getCustomItems(userId)
                error = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "Could not load What It's Worth."
            }
        }
    }

    suspend fun change(action: suspend () -> Unit) {
        check(!changing) { "Please wait for the current save." }
        changing = true
        try {
            action()
            reload++
            error = null
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: Exception) { error = failure.message ?: "Could not save your custom item."; throw failure
        } finally { changing = false }
    }

    WhatItWorthContent(
        report = report,
        customItems = customItems,
        error = error,
        category = category,
        onCategory = {
            category = it
            if (!inspecting) saveWorthCategory(context, userId, it)
        },
        onAddCustom = { name, cents, reviewed, key ->
            change {
                val account = screenAccount(context, userId, session)
                val row = reviewed?.copy(name = name, valueCents = cents, category = WorthCategory.CUSTOM.name, cloudId = reviewed.cloudId ?: key)
                    ?: CustomWorthItemEntity(userId = userId, name = name, valueCents = cents,
                        category = WorthCategory.CUSTOM.name, cloudId = key)
                (sync ?: GamificationSyncs.get(context)).saveItem(account, row, reviewed)
                check((session ?: AccountSessions.get(context)).isCurrent(account)) { "Please log in again." }
            }
        },
        onDeleteCustom = { id ->
            scope.launch {
                try {
                    change {
                        val account = screenAccount(context, userId, session)
                        val row = requireNotNull(customItems.firstOrNull { it.id == id }) { "This item changed. Reopen it before removing it." }
                        (sync ?: GamificationSyncs.get(context)).removeItem(account, row)
                    }
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) { }
            }
        },
        onBack = onBack,
        onAchievements = onAchievements,
    )
}

@Composable
private fun WhatItWorthContent(
    report: AchievementReport?,
    customItems: List<CustomWorthItemEntity>,
    error: String?,
    category: WorthCategory,
    onCategory: (WorthCategory) -> Unit,
    onAddCustom: suspend (String, Long, CustomWorthItemEntity?, String) -> Unit,
    onDeleteCustom: (Long) -> Unit,
    onBack: () -> Unit,
    onAchievements: () -> Unit,
) {
    var basis by rememberSaveable { mutableStateOf(WorthBasis.TOTAL_SAVED) }
    var selectedItemId by rememberSaveable { mutableStateOf<String?>(null) }

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
            // I added Back so this screen can return to the menu.
            TextButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Text("Back", color = Teal)
            }
            Text(
                text = "What It's Worth",
                fontSize = 36.sp,
                lineHeight = 44.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 72.dp),
            )
        }

        Text(
            text = "See the value of what you’ve saved compared to real life items",
            fontSize = 12.sp,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
        )

        if (error != null) Text(error, color = ErrorRed, textAlign = TextAlign.Center)
        if (report == null) {
            if (error == null) Text("Loading...", color = TextSecondary)
            return@Column
        }

        ChipRow(WorthBasis.entries, basis, { it.label }) { basis = it }

        val savedCents = when (basis) {
            WorthBasis.TOTAL_SAVED -> report.totalSavedCents
            WorthBasis.THIS_MONTH -> report.currentMonthUnderCents
            WorthBasis.CATEGORY_SAVINGS -> report.lastMonthCategorySavedCents
        }

        ChipRow(WorthCategory.entries, category, { it.label }) {
            onCategory(it)
            selectedItemId = null
        }

        val items = if (category == WorthCategory.CUSTOM) customItems.filter { it.valueCents > 0 }.map(WorthCatalog::custom)
            else WorthCatalog.forCategory(category)
        val item = items.firstOrNull { it.id == selectedItemId } ?: items.firstOrNull()

        if (savedCents == null) {
            Text(
                "Set a monthly budget for this month to see how far under it you are.",
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        } else if (item == null) {
            Text(
                "Add a custom item below to compare your savings with it.",
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        } else {
            ComparisonHeadline(basis, savedCents, item)
            val others = items.filter { it.id != item.id }
            if (others.isNotEmpty()) {
                WorthCard(title = "Try another comparison") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        others.forEach { other ->
                            val comparison = WorthCatalog.compare(savedCents, other)
                            ComparisonCard(
                                item = other,
                                label = "${comparison.count} ${other.label(comparison.count)}\n${formatBudgetAmount(other.priceCents)} each",
                                onClick = { selectedItemId = other.id },
                            )
                        }
                    }
                }
            }
        }

        // The disclaimer is always shown because catalogue and custom prices are estimates.
        Text(
            text = WorthCatalog.PRICE_DISCLAIMER,
            fontSize = 11.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )

        if (category == WorthCategory.CUSTOM) {
            CustomItemsCard(customItems, onAddCustom, onDeleteCustom)
        }

        val nextItemBadge = Achievements.nextUnlock(report.progress) { it.group == AchievementGroup.WHAT_ITS_WORTH }
        WorthCard(title = "Next item badge") {
            if (nextItemBadge == null) {
                Text("You have earned every What It's Worth badge.", color = TextSecondary)
            } else {
                NextUnlockPanel(nextItemBadge)
            }
            TextButton(onClick = onAchievements, modifier = Modifier.align(Alignment.End)) {
                Text("View all achievements", color = Teal)
            }
        }
    }
}

@Composable
private fun ComparisonHeadline(basis: WorthBasis, savedCents: Long, item: WorthItem) {
    val comparison = WorthCatalog.compare(savedCents, item)
    val amount = formatBudgetAmount(savedCents)
    val lead = when (basis) {
        WorthBasis.TOTAL_SAVED -> "You have saved $amount by staying under your monthly budgets"
        WorthBasis.THIS_MONTH -> "You are on track: $amount under this month's budget so far"
        WorthBasis.CATEGORY_SAVINGS -> "You stayed $amount under your category limits last month"
    }
    val sentence = if (comparison.count > 0) "$lead, which is equal to about ${comparison.count} ${item.label(comparison.count)}."
        else "$lead. Save ${formatBudgetAmount(item.priceCents - comparison.remainderCents)} more to reach 1 ${item.name}."

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircle(drawableRes = R.drawable.piggy_bank, size = 140.dp, iconSize = 90.dp)
        Text("=", fontSize = 36.sp, color = TextPrimary)
        IconCircle(drawableRes = worthItemDisplayIconRes(item), monogram = item.name.take(1), size = 140.dp, iconSize = 90.dp)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = sentence,
            fontSize = 18.sp,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "= ${comparison.count} ${item.label(comparison.count).replaceFirstChar { it.uppercase() }}",
            fontSize = 36.sp,
            lineHeight = 42.sp,
            color = AccentCoral,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "about ${formatBudgetAmount(item.priceCents)} each",
            fontSize = 12.sp,
            color = TextSecondary,
        )
    }
}

@Composable
private fun CustomItemsCard(
    customItems: List<CustomWorthItemEntity>,
    onAdd: suspend (String, Long, CustomWorthItemEntity?, String) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var value by rememberSaveable { mutableStateOf("") }
    var formError by rememberSaveable { mutableStateOf<String?>(null) }
    var key by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var snapshot by rememberSaveable { mutableStateOf<String?>(null) }
    val reviewed = remember(snapshot) {
        snapshot?.let {
            val json = JSONObject(it)
            CustomWorthItemEntity(id = json.getLong("id"), userId = json.getLong("owner"), name = json.getString("name"),
                valueCents = json.getLong("valueCents"), category = json.getString("category"),
                cloudId = if (json.isNull("key")) null else json.getString("key"))
        }
    }
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }

    WorthCard(title = "Your custom items") {
        customItems.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.name, color = TextPrimary, modifier = Modifier.weight(1f))
                Text(formatBudgetAmount(item.valueCents), color = TextSecondary)
                TextButton(enabled = !saving, onClick = {
                    snapshot = JSONObject().put("id", item.id).put("owner", item.userId).put("key", item.cloudId ?: JSONObject.NULL)
                        .put("name", item.name).put("valueCents", item.valueCents).put("category", item.category).toString()
                    key = UUID.randomUUID().toString()
                    name = item.name
                    value = centsToBudgetInput(item.valueCents)
                    formError = null
                }) { Text("Edit", color = Teal) }
                TextButton(enabled = !saving, onClick = { onDelete(item.id) }) { Text("Remove", color = ErrorRed) }
            }
        }
        if (customItems.isEmpty()) Text("No custom items yet.", color = TextSecondary)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            enabled = !saving,
            value = name,
            onValueChange = { name = it.take(MAX_CUSTOM_NAME) },
            label = { Text("Item name") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = mobileKeyboardActions(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            enabled = !saving,
            value = value,
            onValueChange = { value = it },
            label = { Text("Estimated value (R)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = mobileKeyboardActions(),
            modifier = Modifier.fillMaxWidth(),
        )
        formError?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(it, color = ErrorRed, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (reviewed != null) TextButton(enabled = !saving, onClick = {
            snapshot = null; name = ""; value = ""; key = UUID.randomUUID().toString(); formError = null
        }) { Text("Cancel edit") }
        Button(
            enabled = !saving,
            onClick = {
                val cents = parseBudgetCents(value)
                formError = when {
                    name.isBlank() -> "Enter a name for the item."
                    cents == null || cents <= 0 -> "Enter a value greater than zero, like 250 or 250.50."
                    else -> null
                }
                if (formError == null && cents != null) {
                    saving = true
                    scope.launch {
                        try {
                            onAdd(name.trim(), cents, reviewed, key)
                            name = ""; value = ""; snapshot = null; key = UUID.randomUUID().toString()
                        } catch (cancelled: CancellationException) { throw cancelled
                        } catch (failure: Exception) { formError = failure.message ?: "Could not save this item. Your entries are kept."
                        } finally { saving = false }
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = ButtonTeal),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (reviewed == null) "Add custom item" else "Save item changes")
        }
    }
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Teal,
                    selectedLabelColor = Color.White,
                ),
            )
        }
    }
}

@Composable
private fun WorthCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, fontSize = 15.sp, color = TextPrimary)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun IconCircle(
    drawableRes: Int?,
    size: Dp,
    iconSize: Dp,
    monogram: String? = null,
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(CircleBg, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (drawableRes != null) {
            Image(
                painter = painterResource(drawableRes),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(iconSize),
            )
        } else if (monogram != null) {
            Text(monogram.uppercase(), fontSize = (iconSize.value / 2).sp, color = Teal)
        }
    }
}

@Composable
private fun ComparisonCard(item: WorthItem, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(145.dp)
            .height(145.dp)
            .background(PanelBg, CardShape)
            .border(1.dp, CardBorder.copy(alpha = 0.25f), CardShape)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        val icon = worthItemDisplayIconRes(item)
        if (icon != null) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(64.dp),
            )
        } else {
            Text(item.name.take(1).uppercase(), fontSize = 40.sp, color = Teal)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp,
        )
    }
}

private fun worthPreferences(context: Context) =
    context.applicationContext.getSharedPreferences("pocket_quest_worth", Context.MODE_PRIVATE)

private fun loadWorthCategory(context: Context, userId: Long): WorthCategory {
    val saved = worthPreferences(context).getString("category_$userId", null)
    return WorthCategory.entries.firstOrNull { it.name == saved } ?: WorthCategory.FOOD
}

private fun saveWorthCategory(context: Context, userId: Long, category: WorthCategory) {
    worthPreferences(context).edit().putString("category_$userId", category.name).apply()
}

private val previewCustomItems = listOf(
    CustomWorthItemEntity(id = 1, userId = 1, name = "Bicycle", valueCents = 4_500_00, category = WorthCategory.CUSTOM.name),
)

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun WhatItWorthScreenPreview() {
    MaterialTheme {
        WhatItWorthScreen(userId = 1, onBack = {})
    }
}
