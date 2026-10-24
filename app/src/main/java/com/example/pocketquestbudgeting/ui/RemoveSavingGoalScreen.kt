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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ScreenBg = Color(0xFFF4F7F6)
private val TextPrimary = Color(0xFF1A2B28)
private val TextSecondary = Color(0xFF6B7C78)
private val CardWhite = Color(0xFFFFFFFF)
private val FieldBgSoft = Color(0x69E8EEEC)
private val Teal = Color(0xFF0F6B5C)
private val RemoveOrange = Color(0xFFC45C26)
private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun RemoveSavingGoalScreen(userId: Long = 0, onBack: () -> Unit, session: AccountSession? = null, sync: SavingsSync? = null) {
    val context = LocalContext.current
    val inspecting = LocalInspectionMode.current
    val db = remember { if (inspecting) null else DatabaseProvider.get(context) }
    val goalFlow: kotlinx.coroutines.flow.Flow<List<SavingsGoalEntity>?> = remember(db, userId) { db?.savingsDao()?.observeForUser(userId) ?: kotlinx.coroutines.flow.flowOf(emptyList<SavingsGoalEntity>()) }
    val loadedGoals by goalFlow.collectAsState(null)
    val goals = loadedGoals.orEmpty()
    val scope = rememberCoroutineScope()
    var reviewedSnapshot by rememberSaveable(userId) { mutableStateOf<String?>(null) }
    val reviewed = remember(userId, reviewedSnapshot) { reviewedSnapshot?.let { restoreSavingGoal(userId, it) } }
    var confirm by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = saving) {}


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
                onClick = { if (!saving) onBack() },
                modifier = Modifier.align(Alignment.CenterStart),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            ) {
                Text("Back", color = Teal, fontSize = 16.sp)
            }
            Text(
                text = "Remove Saving Goal",
                fontSize = 32.sp,
                lineHeight = 40.sp,
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
                    Text("Remove Saving Goals", fontSize = 20.sp, color = TextPrimary)
                }

                Text("Select Saving Goal", fontSize = 15.sp, color = TextPrimary)
                SavingGoalSelector(goals, reviewed, loading = loadedGoals == null) { if (!saving) { reviewedSnapshot = savingGoalSnapshot(it); error = null } }

            }
        }

        error?.let { Text(it, color = Color(0xFFD64545)) }
        Button(
            enabled = !saving && reviewed != null,
            onClick = { confirm = true },
            shape = CardShape,
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = RemoveOrange,
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
                Text("＋  Remove Saving Goal", fontSize = 20.sp)
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { if (!saving) confirm = false },
        title = { Text("Remove ${reviewed?.name}?") },
        text = { Text("This hides the goal. Its saved amounts and history are kept.") },
        dismissButton = { TextButton(onClick = { confirm = false }, enabled = !saving) { Text("Cancel") } },
        confirmButton = { TextButton(enabled = !saving, onClick = {
            saving = true
            scope.launch {
                try {
                    val account = screenAccount(context, userId, session)
                    (sync ?: SavingsSyncs.get(context)).removeGoal(account, requireNotNull(reviewed))
                    confirm = false
                    if ((session ?: AccountSessions.get(context)).isCurrent(account)) onBack()
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (failure: Exception) { confirm = false; error = failure.message ?: "Could not remove this goal."
                } finally { saving = false }
            }
        }) { Text("Remove goal") } })

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
            enabled = enabled,
            singleLine = true,
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
private fun RemoveSavingGoalScreenPreview() {
    MaterialTheme {
        RemoveSavingGoalScreen(onBack = {})
    }
}