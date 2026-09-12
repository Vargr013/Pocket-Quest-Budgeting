package com.example.pocketquestbudgeting.ui

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
private val FieldBg = Color(0xFFE8EEEC)
private val FieldBgSoft = Color(0x69E8EEEC)
private val PrefixBg = Color(0xFFD9D9D9)
private val Teal = Color(0xFF0F6B5C)
private val ButtonTeal = Color(0xFF2A9D8F)
private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun AddSavingGoalScreen() {
    var totalSavingAmount by rememberSaveable { mutableStateOf("") }
    var currentSavedAmount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var targetDate by rememberSaveable { mutableStateOf("") }

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
        // ☰ = menu (corner only), title separate
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
                text = "Add Saving Goal",
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
                    Text("＋", fontSize = 22.sp, color = Teal)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Add Saving Goal", fontSize = 20.sp, color = TextPrimary)
                }

                Text("Total Saving Goal", fontSize = 15.sp, color = TextPrimary)
                AmountField(
                    value = totalSavingAmount,
                    onValueChange = { totalSavingAmount = it },
                )

                Text("Current Saved Amount", fontSize = 15.sp, color = TextPrimary)
                AmountField(
                    value = currentSavedAmount,
                    onValueChange = { currentSavedAmount = it },
                )

                Text("Note", fontSize = 12.sp, color = TextPrimary)
                PlainField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = "e.g. Salary top-up",
                    background = FieldBgSoft,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Target Date", fontSize = 12.sp, color = TextPrimary)
                PlainField(
                    value = targetDate,
                    onValueChange = { targetDate = it },
                    placeholder = "e.g. 13/12/2027",
                    background = FieldBgSoft,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Button(
            onClick = { },
            shape = CardShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = ButtonTeal,
                contentColor = TextPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 315.dp)
                .height(41.dp),
        ) {
            Text("＋  Add Saving Goal", fontSize = 20.sp)
        }
    }
}

@Composable
private fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
) {
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
            value = value,
            onValueChange = onValueChange,
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
    height: Dp = 31.dp,
    keyboardType: KeyboardType = KeyboardType.Text,
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
                    Text(placeholder, fontSize = 12.sp, color = TextSecondary)
                }
                inner()
            }
        },
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AddSavingGoalScreenPreview() {
    MaterialTheme {
        AddSavingGoalScreen()
    }
}