package com.example.pocketquestbudgeting.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ScreenBg = Color(0xFFF4F7F6)
private val TextPrimary = Color(0xFF1A2B28)
private val TextSecondary = Color(0xFF6B7C78)
private val CardWhite = Color(0xFFFFFFFF)
private val GridLine = Color(0xFFE8E8E8)
private val Teal = Color(0xFF0F6B5C)

private val Rent = Color(0xFFC4A35A)
private val Groceries = Color(0xFF0F6B5C)
private val Transport = Color(0xFF7A9E7E)
private val Entertainment = Color(0xFFE07A5F)
private val Utilities = Color(0xFF3D7EA6)

private val CardShape = RoundedCornerShape(20.dp)

// Placeholder stacked values (bottom → top), matching Figma look
private data class DaySpend(
    val label: String,
    val rent: Float,
    val groceries: Float,
    val transport: Float,
    val entertainment: Float,
    val utilities: Float,
) {
    val total: Float get() = rent + groceries + transport + entertainment + utilities
}

private val weekData = listOf(
    DaySpend("Mon", 80f, 80f, 80f, 80f, 80f),
    DaySpend("Tue", 52f, 52f, 52f, 52f, 52f),
    DaySpend("Wed", 47f, 47f, 47f, 47f, 47f),
    DaySpend("Thu", 66f, 66f, 66f, 66f, 66f),
    DaySpend("Fri", 28f, 28f, 28f, 28f, 28f),
    DaySpend("Sat", 48f, 48f, 48f, 48f, 48f),
    DaySpend("Sun", 106f, 106f, 106f, 106f, 106f),
)

@Composable
fun DailySpendingGraphScreen() {
    val maxY = 600f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
                text = "Daily Spending Graph",
                fontSize = 28.sp,
                lineHeight = 34.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 40.dp),
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
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                ) {
                    // Y-axis labels
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(36.dp)
                            .padding(end = 4.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End,
                    ) {
                        listOf("R600", "R400", "R200", "R0").forEach {
                            Text(it, fontSize = 10.sp, color = Color.Black)
                        }
                    }

                    // Chart area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .border(1.dp, GridLine),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            repeat(4) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(GridLine),
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            weekData.forEach { day ->
                                StackedBar(
                                    day = day,
                                    maxY = maxY,
                                    modifier = Modifier
                                        .width(28.dp)
                                        .fillMaxHeight(),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // X-axis day labels
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 36.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    weekData.forEach { day ->
                        Text(
                            text = day.label,
                            fontSize = 10.sp,
                            color = Color.Black,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(28.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Legend
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        LegendItem("Rent", Rent)
                        LegendItem("Entertainment", Entertainment)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        LegendItem("Groceries", Groceries)
                        LegendItem("Utilities", Utilities)
                    }
                    LegendItem("Transport", Transport)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Total this Week:",
                        fontSize = 14.sp,
                        color = TextSecondary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "R2,290",
                        fontSize = 20.sp,
                        color = Color.Black,
                    )
                }
            }
        }
    }
}

@Composable
private fun StackedBar(
    day: DaySpend,
    maxY: Float,
    modifier: Modifier = Modifier,
) {
    val segments = listOf(
        day.rent to Rent,
        day.groceries to Groceries,
        day.transport to Transport,
        day.entertainment to Entertainment,
        day.utilities to Utilities,
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Bottom,
    ) {
        val emptyWeight = ((maxY - day.total).coerceAtLeast(0f)) / maxY
        if (emptyWeight > 0f) {
            Spacer(modifier = Modifier.weight(emptyWeight))
        }
        // Draw top → bottom visually by reversing (utilities on top in Figma)
        segments.asReversed().forEach { (value, color) ->
            val w = (value / maxY).coerceAtLeast(0.001f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(w)
                    .background(color)
                    .border(0.2.dp, Color.White),
            )
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(color, CircleShape),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontSize = 10.sp, color = Color.Black)
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun DailySpendingGraphScreenPreview() {
    MaterialTheme {
        DailySpendingGraphScreen()
    }
}