package com.example.pocketquestbudgeting.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
private val BadgeGray = Color(0xFFC4C4C4)
private val ProgressTrack = Color(0xFF5E5E5E)
private val ProgressFill = Color(0xFFE07A5F)
private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun AchievementsScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
                text = "Achievements",
                fontSize = 36.sp,
                lineHeight = 44.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // Login Streak
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Login Streak", fontSize = 20.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(PanelBg, CardShape)
                            .padding(16.dp),
                    ) {
                        Text("7 days", fontSize = 40.sp, lineHeight = 48.sp, color = TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Keep reviewing expenses daily to streak",
                            fontSize = 10.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    // Placeholder for streak artwork from Figma
                    BadgePlaceholder(size = 100.dp)
                }
            }
        }

        // Badges
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Badges", fontSize = 20.sp, color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color.Black, CircleShape),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    BadgePlaceholder(size = 112.dp)
                    BadgePlaceholder(size = 112.dp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    BadgePlaceholder(size = 112.dp)
                    BadgePlaceholder(size = 112.dp)
                }
            }
        }

        // Next Unlocks
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Next Unlocks", fontSize = 20.sp, color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color.Black, CircleShape),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PanelBg, CardShape)
                        .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BadgePlaceholder(size = 70.dp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Budget Master",
                                fontSize = 17.sp,
                                color = TextPrimary,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                LinearProgressIndicator(
                                    progress = { 3f / 7f },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(14.dp),
                                    color = ProgressFill,
                                    trackColor = ProgressTrack,
                                    strokeCap = StrokeCap.Round,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("3/7", fontSize = 20.sp, color = TextPrimary)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Remain under your daily budget for a week",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun BadgePlaceholder(size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .border(2.dp, Color.Black.copy(alpha = 0.15f), CircleShape)
            .padding(12.dp)
            .background(BadgeGray, CircleShape),
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AchievementsScreenPreview() {
    MaterialTheme {
        AchievementsScreen(onBack = {})
    }
}