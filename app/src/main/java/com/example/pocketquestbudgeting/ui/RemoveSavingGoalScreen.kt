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
private val FieldBgSoft = Color(0x69E8EEEC)
private val Teal = Color(0xFF0F6B5C)
private val RemoveOrange = Color(0xFFC45C26)
private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun RemoveSavingGoalScreen() {
    var savingGoal by rememberSaveable { mutableStateOf("") }

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
                text = "Remove Saving Goal",
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
                    Text("Remove Saving Goals", fontSize = 20.sp, color = TextPrimary)
                }

                Text("Select Saving Goal", fontSize = 15.sp, color = TextPrimary)
                Box(modifier = Modifier.fillMaxWidth()) {
                    PlainField(
                        value = savingGoal,
                        onValueChange = { savingGoal = it },
                        placeholder = "e.g. Holiday",
                        background = FieldBgSoft,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "▼",
                        fontSize = 12.sp,
                        color = TextPrimary,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp),
                    )
                }
            }
        }

        Button(
            onClick = { },
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
            Text("＋  Remove Saving Goal", fontSize = 20.sp)
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
private fun RemoveSavingGoalScreenPreview() {
    MaterialTheme {
        RemoveSavingGoalScreen()
    }
}