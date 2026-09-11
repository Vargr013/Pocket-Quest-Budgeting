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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pocketquestbudgeting.R

private val DashboardBg = Color(0xFFF4F7F6)
private val TextPrimary = Color(0xFF1A2B28)
private val TextSecondary = Color(0xFF6B7C78)
private val CardWhite = Color(0xFFFFFFFF)
private val RowBg = Color(0xFFE8EEEC)
private val Teal = Color(0xFF0F6B5C)
private val ProgressTeal = Color(0xFF2A9D8F)
private val ProgressAmber = Color(0xFFE9A825)
private val ProgressRed = Color(0xFFD64545)
private val ProgressTrack = Color(0xFF5E5E5E)
private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun DashboardScreen(
    onAddExpense: () -> Unit,
    onHistory: () -> Unit,
    onMenu: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DashboardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp)
                .padding(top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header: menu + Dashboard
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onMenu) {
                    Text("☰", fontSize = 28.sp, color = Teal)
                }

                Text(
                    text = stringResource(R.string.dashboard),
                    fontSize = 36.sp,
                    lineHeight = 44.sp,
                    color = TextPrimary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            // Current Balance
            DashboardCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Current Balance", fontSize = 20.sp, color = TextPrimary)
                    Text("R12 654,00", fontSize = 20.sp, color = TextPrimary)
                }
            }

            // Budgets / Daily Budget
            DashboardCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Budgets", fontSize = 20.sp, color = TextPrimary)
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(RowBg, CardShape)
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Daily Budget", fontSize = 20.sp, color = TextPrimary)
                                Text("R350", fontSize = 20.sp, color = TextPrimary)
                                Text("of R400", fontSize = 15.sp, color = TextSecondary)
                                Spacer(Modifier.height(16.dp))
                                LinearProgressIndicator(
                                    progress = { 350f / 400f },
                                    modifier = Modifier
                                        .fillMaxWidth(0.85f)
                                        .height(14.dp),
                                    color = ProgressAmber,
                                    trackColor = ProgressTrack,
                                    strokeCap = StrokeCap.Round,
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(0.85f),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("R50 remaining", fontSize = 10.sp, color = TextSecondary)
                                    Text("3 hours left", fontSize = 10.sp, color = TextSecondary)
                                }
                            }
                            CircularBudgetRing(
                                progress = 350f / 400f,
                                color = ProgressAmber,
                                modifier = Modifier.size(110.dp),
                            )
                        }
                    }
                }
            }

            // Category Progress
            DashboardCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Category Progress", fontSize = 20.sp, color = TextPrimary)
                    Spacer(Modifier.height(12.dp))
                    CategoryRow("Groceries", "R1 392,60 of R 2 600", "56%", 0.56f, ProgressTeal)
                    Spacer(Modifier.height(8.dp))
                    CategoryRow("Entertainment", "R464 of R400", "130%", 1f, ProgressRed, showWarning = true)
                    Spacer(Modifier.height(8.dp))
                    CategoryRow("Transport", "R1 160,50 of R1 450", "70%", 0.70f, ProgressAmber, showWarning = true)
                    Spacer(Modifier.height(8.dp))
                    CategoryRow("Rent", "R2 200 of R2 200", "100%", 1f, ProgressRed, showWarning = true)
                }
            }

            // Recent Expenses
            DashboardCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Recent Expenses", fontSize = 20.sp, color = TextPrimary)
                        TextButton(onClick = onHistory) {
                            Text("History", color = Teal)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    ExpenseRow("Coffee", "Today - Entertainment", "R45")
                    Spacer(Modifier.height(8.dp))
                    ExpenseRow("Pick n Pay", "Yesterday - Groceries", "R690")
                    Spacer(Modifier.height(8.dp))
                    ExpenseRow("Uber", "Yesterday - Transport", "R85")
                    Spacer(Modifier.height(8.dp))
                    ExpenseRow("Netflix", "This week - Entertainment", "R199")
                }
            }

            // Saving Goals
            DashboardCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Saving Goals", fontSize = 20.sp, color = TextPrimary)
                    Spacer(Modifier.height(12.dp))
                    SavingGoalRow("Emergency Fund", "R4 000 of R10 000")
                    Spacer(Modifier.height(8.dp))
                    SavingGoalRow("Holiday", "R1 200 of R5 000")
                }
            }
        }

        // FAB — matches teal plus button
        FloatingActionButton(
            onClick = onAddExpense,
            containerColor = Teal,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 24.dp),
        ) {
            Text("+", fontSize = 28.sp, color = Color.White)        }
    }
}

@Composable
private fun DashboardCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        content = { content() },
    )
}

@Composable
private fun CategoryRow(
    name: String,
    subtitle: String,
    percent: String,
    progress: Float,
    barColor: Color,
    showWarning: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RowBg, CardShape)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontSize = 15.sp, color = TextPrimary)
                Text(subtitle, fontSize = 10.sp, color = TextSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(percent, fontSize = 15.sp, color = TextPrimary)
                if (showWarning) {
                    Text(
                        text = "!",
                        color = barColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = barColor,
            trackColor = ProgressTrack,
            strokeCap = StrokeCap.Round,
        )
    }
}

@Composable
private fun ExpenseRow(title: String, subtitle: String, amount: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RowBg, CardShape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, fontSize = 15.sp, color = TextPrimary)
            Text(subtitle, fontSize = 10.sp, color = TextSecondary)
        }
        Text(amount, fontSize = 15.sp, color = TextPrimary)
    }
}

@Composable
private fun SavingGoalRow(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RowBg, CardShape)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(title, fontSize = 15.sp, color = TextPrimary)
        Text(subtitle, fontSize = 10.sp, color = TextSecondary)
    }
}

@Composable
private fun CircularBudgetRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val stroke = 10.dp.toPx()
        val diameter = size.minDimension - stroke
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)
        drawArc(
            color = Color.Black,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun DashboardScreenPreview() {
    MaterialTheme {
        DashboardScreen(onAddExpense = {}, onHistory = {})
    }
}