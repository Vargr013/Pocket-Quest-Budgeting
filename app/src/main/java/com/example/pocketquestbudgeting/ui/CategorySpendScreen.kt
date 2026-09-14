package com.example.pocketquestbudgeting.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ScreenBg = Color(0xFFF4F7F6)
private val TextPrimary = Color(0xFF1A2B28)
private val TextSecondary = Color(0xFF6B7C78)
private val CardWhite = Color(0xFFFFFFFF)
private val PanelBg = Color(0xFFE8EEEC)
private val Teal = Color(0xFF0F6B5C)
private val ProgressTrack = Color(0xFF5E5E5E)
private val TabSelected = Color(0xFFE07A5F)

private val Rent = Color(0xFFC4A35A)
private val Groceries = Color(0xFF0F6B5C)
private val Transport = Color(0xFF7A9E7E)
private val Entertainment = Color(0xFFE07A5F)
private val Utilities = Color(0xFF3D7EA6)

private val CardShape = RoundedCornerShape(20.dp)

private data class CategorySpend(
    val name: String,
    val amount: String,
    val percentLabel: String,
    val progress: Float,
    val color: Color,
)

private val categories = listOf(
    CategorySpend("Rent", "R2 200", "38.8%", 0.388f, Rent),
    CategorySpend("Groceries", "R1 392,60", "24.6%", 0.246f, Groceries),
    CategorySpend("Transport", "R1 160,50", "20.5%", 0.205f, Transport),
    CategorySpend("Entertainment", "R464", "8.2%", 0.082f, Entertainment),
    CategorySpend("Utilities", "R450", "7.9%", 0.079f, Utilities),
)

private enum class PeriodTab { Daily, Monthly, Custom }

@Composable
fun CategorySpendingSummaryScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf(PeriodTab.Monthly) }

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
        TextButton(onClick = onBack) {
    Text("Back")
}
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
                text = "Category Spending Summary",
                fontSize = 28.sp,
                lineHeight = 34.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 40.dp),
            )
        }

        // Daily / Monthly / Custom tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(39.dp)
                .background(PanelBg, CardShape)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PeriodTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(
                            if (selected) TabSelected else Color.Transparent,
                            CardShape,
                        )
                        .clickable { selectedTab = tab },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = tab.name,
                        fontSize = 15.sp,
                        color = if (selected) TextPrimary else TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // Donut chart + total
        Box(
            modifier = Modifier.size(317.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 47.5.dp.toPx()
                val diameter = size.minDimension - stroke
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val arcSize = Size(diameter, diameter)
                var start = -90f
                categories.forEach { cat ->
                    val sweep = 360f * cat.progress
                    drawArc(
                        color = cat.color,
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Butt),
                    )
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Total Spent", fontSize = 20.sp, color = TextSecondary)
                Text("R4 642,12", fontSize = 30.sp, color = TextPrimary)
            }
        }

        // Category rows
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { cat ->
                    CategoryRow(cat)
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(cat: CategorySpend) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelBg, CardShape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(cat.color, CircleShape),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = cat.name,
                fontSize = 14.sp,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(cat.amount, fontSize = 14.sp, color = TextPrimary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = cat.percentLabel,
                fontSize = 14.sp,
                color = TextSecondary,
                modifier = Modifier.width(48.dp),
                textAlign = TextAlign.End,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { cat.progress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp)
                .height(11.dp),
            color = cat.color,
            trackColor = ProgressTrack,
            strokeCap = StrokeCap.Round,
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun CategorySpendingSummaryScreenPreview() {
    MaterialTheme {
        CategorySpendingSummaryScreen(onBack = {})
    }
}