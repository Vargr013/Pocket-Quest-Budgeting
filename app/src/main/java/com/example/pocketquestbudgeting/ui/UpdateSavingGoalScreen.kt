package com.example.pocketquestbudgeting.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import com.example.pocketquestbudgeting.data.AccountSession
import com.example.pocketquestbudgeting.data.AccountSessions
import com.example.pocketquestbudgeting.data.SavingsSync
import com.example.pocketquestbudgeting.data.SavingsSyncs
import com.example.pocketquestbudgeting.data.SavingsGoalEntity
import com.example.pocketquestbudgeting.data.SavingsContributionEntity
import com.example.pocketquestbudgeting.data.SavingsChecks
import com.example.pocketquestbudgeting.data.DatabaseProvider
import com.example.pocketquestbudgeting.data.todayIsoDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
fun UpdateSavingGoalScreen(userId: Long = 0, onBack: () -> Unit = {}, session: AccountSession? = null, sync: SavingsSync? = null) {
    val context = LocalContext.current
    val inspecting = LocalInspectionMode.current
    val db = remember { if (inspecting) null else DatabaseProvider.get(context) }
    val goalFlow: kotlinx.coroutines.flow.Flow<List<SavingsGoalEntity>?> = remember(db, userId) { db?.savingsDao()?.observeForUser(userId) ?: kotlinx.coroutines.flow.flowOf(emptyList<SavingsGoalEntity>()) }
    val loadedGoals by goalFlow.collectAsState(null)
    val goals = loadedGoals.orEmpty()
    val scope = rememberCoroutineScope()
    var reviewedSnapshot by rememberSaveable(userId) { mutableStateOf<String?>(null) }
    val reviewed = remember(userId, reviewedSnapshot) { reviewedSnapshot?.let { restoreSavingGoal(userId, it) } }
    var contributionKey by rememberSaveable(userId) { mutableStateOf(UUID.randomUUID().toString()) }
    var additionDate by rememberSaveable(userId) { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val contributionFlow = remember(db, userId, reviewed?.id) {
        reviewed?.let { db?.savingsDao()?.observeContributions(userId, it.id) }
            ?: kotlinx.coroutines.flow.flowOf(emptyList<SavingsContributionEntity>())
    }
    val contributions by contributionFlow.collectAsState(emptyList())
    val savedCents = remember(contributions) { SavingsChecks.balance(contributions) }
    var error by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = saving) {}

    var goalName by rememberSaveable { mutableStateOf("") }
    var targetAmount by rememberSaveable { mutableStateOf("") }
    var amountToAdd by rememberSaveable { mutableStateOf("") }
    var targetDate by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }

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
            PocketQuestBackButton(
                onBack = { if (!saving) onBack() },
                modifier = Modifier.align(Alignment.CenterStart),
            )
            Text(
                text = "Update Saving Goal",
                fontSize = 32.sp,
                lineHeight = 40.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 72.dp),
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
                    Text("Update Saving Goal", fontSize = 20.sp, color = TextPrimary)
                }

                Text("Select Saving Goal", fontSize = 15.sp, color = TextPrimary)
                SavingGoalSelector(goals, reviewed, loading = loadedGoals == null) { goal ->
                    if (!saving) {
                        reviewedSnapshot = savingGoalSnapshot(goal)
                        goalName = goal.name
                        targetAmount = centsToBudgetInput(goal.targetCents)
                        targetDate = savingsDateInput(goal.targetDate)
                        note = goal.note ?: ""
                        amountToAdd = ""
                        contributionKey = UUID.randomUUID().toString()
                        additionDate = null
                        error = null
                    }
                }
                Text("Saved locally: ${formatBudgetAmount(savedCents)}", color = TextSecondary)

                Text("Goal Name", fontSize = 12.sp, color = TextPrimary)
                PlainField(
                    value = goalName,
                    onValueChange = { goalName = it }, enabled = !saving,
                    placeholder = "e.g. Holiday Fund",
                    background = FieldBgSoft,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Target Amount", fontSize = 15.sp, color = TextPrimary)
                AmountField(
                    value = targetAmount,
                    onValueChange = { targetAmount = it }, enabled = !saving,
                    placeholder = "Enter new target",
                )

                Text("Amount to Add", fontSize = 15.sp, color = TextPrimary)
                AmountField(
                    value = amountToAdd,
                    onValueChange = { amountToAdd = it }, enabled = !saving,
                    placeholder = "Enter amount to add",
                )

                Text("Target Date", fontSize = 12.sp, color = TextPrimary)
                PlainField(
                    value = targetDate,
                    onValueChange = { targetDate = it }, enabled = !saving,
                    placeholder = "e.g. 13/12/2027",
                    background = FieldBgSoft,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Goal note", fontSize = 12.sp, color = TextPrimary)
                PlainField(
                    value = note,
                    onValueChange = { note = it }, enabled = !saving,
                    placeholder = "Optional goal note",
                    imeAction = ImeAction.Done,
                    background = FieldBgSoft,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Text("Additions are recorded today. A local save will sync when connected.", color = TextSecondary, fontSize = 12.sp)
        error?.let { Text(it, color = Color(0xFFD64545)) }
        Button(
            enabled = !saving && reviewed != null,
            onClick = {
                if (!saving) {
                    saving = true
                    error = null
                    scope.launch {
                        try {
                            val account = screenAccount(context, userId, session)
                            val row = requireNotNull(reviewed) { "Select a saving goal." }
                            val target = savingsAmount(targetAmount)
                            val addition = savingsAmount(amountToAdd, allowZero = true)
                            val date = savingsTargetDate(targetDate)
                            val savedNote = note.trim().ifEmpty { null }
                            SavingsChecks.requireGoal(row.copy(name = goalName.trim(), targetCents = target, targetDate = date, note = savedNote))
                            if (addition > 0 && additionDate == null) additionDate = todayIsoDate()
                            val event = if (addition == 0L) null else SavingsContributionEntity(userId = userId, goalId = row.id,
                                businessId = contributionKey, kind = SavingsChecks.ADDITION, amountCents = addition,
                                effectiveDate = requireNotNull(additionDate))
                            (sync ?: SavingsSyncs.get(context)).updateGoalWithContribution(account, row, goalName.trim(), target,
                                date, savedNote, event)
                            if ((session ?: AccountSessions.get(context)).isCurrent(account)) onBack()
                        } catch (cancelled: CancellationException) { throw cancelled
                        } catch (failure: Exception) { error = failure.message ?: "Could not update this goal. Your entries are kept."
                        } finally { saving = false }
                    }
                }
            },
            shape = CardShape,
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ButtonTeal,
                contentColor = TextPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 315.dp)
                .heightIn(min = 48.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("＋  Update Saving Goal", fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    placeholder: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
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
                .heightIn(min = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("R", fontSize = 16.sp, color = TextPrimary)
        }
        PlainField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            placeholder = placeholder,
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
    enabled: Boolean = true,
    background: Color = FieldBgSoft,
    height: Dp = 35.dp,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
) {
    Box(
        modifier = modifier
            .heightIn(min = height.coerceAtLeast(48.dp))
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
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 12.sp,
                color = TextPrimary,
                lineHeight = 12.sp,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = mobileKeyboardActions(),
            cursorBrush = SolidColor(Teal),
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) { innerTextField() }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun UpdateSavingGoalScreenPreview() {
    MaterialTheme {
        UpdateSavingGoalScreen(onBack = {})
    }
}