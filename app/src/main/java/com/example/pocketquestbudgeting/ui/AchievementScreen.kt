package com.example.pocketquestbudgeting.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pocketquestbudgeting.R
import com.example.pocketquestbudgeting.data.AchievementGroup
import com.example.pocketquestbudgeting.data.AchievementLoader
import com.example.pocketquestbudgeting.data.AchievementProgress
import com.example.pocketquestbudgeting.data.AchievementReport
import com.example.pocketquestbudgeting.data.Achievements
import kotlinx.coroutines.CancellationException

private val ScreenBg = Color(0xFFF4F7F6)
private val TextPrimary = Color(0xFF1A2B28)
private val TextSecondary = Color(0xFF6B7C78)
private val CardWhite = Color(0xFFFFFFFF)
private val PanelBg = Color(0xFFE8EEEC)
private val Teal = Color(0xFF0F6B5C)
private val AccentCoral = Color(0xFFE07A5F)
private val BadgeLocked = Color(0xFFE8EEEC)
private val BadgeEarned = Color(0xFFCFE8E3)
private val ProgressTrack = Color(0xFFD9E2DF)
private val ProgressFill = AccentCoral
private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun AchievementsScreen(userId: Long, onBack: () -> Unit) {
    val inspecting = LocalInspectionMode.current
    val context = LocalContext.current
    val reload = gamificationReload(userId)
    var report by remember { mutableStateOf(if (inspecting) previewAchievementReport() else null) }
    var error by remember { mutableStateOf<String?>(null) }

    if (!inspecting) {
        LaunchedEffect(userId, reload) {
            try {
                report = AchievementLoader.load(context, userId)
                error = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "Could not load achievements."
            }
        }
    }

    AchievementsContent(report, error, onBack)
}

@Composable
private fun AchievementsContent(report: AchievementReport?, error: String?, onBack: () -> Unit) {
    var selected by remember { mutableStateOf<AchievementProgress?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
                text = "Achievements",
                fontSize = 36.sp,
                lineHeight = 44.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 72.dp),
            )
        }

        when {
            error != null -> Text(error, color = TextSecondary, modifier = Modifier.padding(horizontal = 8.dp))
            report == null -> Text("Loading achievements...", color = TextSecondary, modifier = Modifier.padding(horizontal = 8.dp))
            else -> {
                StreakCard(report)
                BadgesCard(report, onSelect = { selected = it })
                NextUnlockCard(report)
            }
        }
    }

    selected?.let { progress ->
        AlertDialog(
            onDismissRequest = { selected = null },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Close", color = Teal) } },
            title = { Text(progress.definition.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(progress.definition.description)
                    Text(
                        if (progress.earned) "Earned${progress.earnedAtMillis?.let { " on ${earnedDateText(it)}" } ?: ""}"
                        else "Progress: ${achievementProgressText(progress)}",
                        color = TextSecondary,
                    )
                }
            },
        )
    }
}

@Composable
private fun StreakCard(report: AchievementReport) {
    AchievementCard {
        Text("Logging Streak", fontSize = 20.sp, color = TextPrimary)
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
                Text(
                    text = "${report.currentStreak} ${if (report.currentStreak == 1) "day" else "days"}",
                    fontSize = 40.sp,
                    lineHeight = 48.sp,
                    color = AccentCoral,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Log or review your expenses each day to keep your streak. Best: ${report.longestStreak} days.",
                    fontSize = 10.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            BadgeIcon(size = 100.dp, drawableRes = R.drawable.achievement_warming_up)
        }
    }
}

@Composable
private fun BadgesCard(report: AchievementReport, onSelect: (AchievementProgress) -> Unit) {
    var expandedGroups by remember { mutableStateOf(setOf<AchievementGroup>()) }

    AchievementCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Badges", fontSize = 20.sp, color = TextPrimary)
            Text(
                "${report.progress.count { it.earned }}/${report.progress.size} earned",
                fontSize = 14.sp,
                color = TextSecondary,
            )
        }
        AchievementGroup.entries.forEach { group ->
            val badges = report.progress.filter { it.definition.group == group }
            if (badges.isEmpty()) return@forEach
            val rows = badges.chunked(3)
            val expanded = group in expandedGroups
            Spacer(modifier = Modifier.height(16.dp))
            Text(group.label, fontSize = 15.sp, color = Teal)
            Spacer(modifier = Modifier.height(8.dp))
            BadgeGridRow(rows.first(), onSelect)
            if (rows.size > 1) {
                if (expanded) {
                    rows.drop(1).forEach { row ->
                        BadgeGridRow(row, onSelect)
                    }
                    TextButton(
                        onClick = { expandedGroups = expandedGroups - group },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("View less", color = Teal)
                    }
                } else {
                    TextButton(
                        onClick = { expandedGroups = expandedGroups + group },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("View more", color = Teal)
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeGridRow(row: List<AchievementProgress>, onSelect: (AchievementProgress) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        row.forEach { BadgeTile(it, onSelect) }
        repeat(3 - row.size) { Spacer(modifier = Modifier.width(96.dp)) }
    }
}

@Composable
private fun BadgeTile(progress: AchievementProgress, onSelect: (AchievementProgress) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .clickable { onSelect(progress) },
    ) {
        BadgeIcon(
            size = 72.dp,
            drawableRes = achievementIconRes(progress.definition),
            locked = !progress.earned,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = progress.definition.title,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            color = if (progress.earned) TextPrimary else TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NextUnlockCard(report: AchievementReport) {
    val next = Achievements.nextUnlock(report.progress)
    AchievementCard {
        Text("Next Unlock", fontSize = 20.sp, color = TextPrimary)
        Spacer(modifier = Modifier.height(12.dp))
        if (next == null) {
            Text("You have earned every badge. Well done!", color = TextSecondary)
        } else {
            NextUnlockPanel(next)
        }
    }
}

@Composable
internal fun NextUnlockPanel(next: AchievementProgress) {
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
            BadgeIcon(
                size = 70.dp,
                drawableRes = achievementIconRes(next.definition),
                locked = true,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = next.definition.title,
                    fontSize = 17.sp,
                    color = TextPrimary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { next.fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp),
                    color = ProgressFill,
                    trackColor = ProgressTrack,
                    strokeCap = StrokeCap.Round,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = achievementProgressText(next),
                    fontSize = 14.sp,
                    color = TextPrimary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = next.definition.description,
            fontSize = 10.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AchievementCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
internal fun BadgeIcon(
    size: Dp,
    drawableRes: Int,
    locked: Boolean = false,
) {
    val ringColor = if (locked) TextSecondary.copy(alpha = 0.35f) else Teal.copy(alpha = 0.45f)
    Box(
        modifier = Modifier
            .size(size)
            .border(2.dp, ringColor, CircleShape)
            .padding(size / 10)
            .background(if (locked) BadgeLocked else BadgeEarned, CircleShape)
            .alpha(if (locked) 0.7f else 1f),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(drawableRes),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = if (locked) {
                ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.35f) })
            } else {
                null
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(size / 12),
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AchievementsScreenPreview() {
    MaterialTheme {
        AchievementsScreen(userId = 1, onBack = {})
    }
}
