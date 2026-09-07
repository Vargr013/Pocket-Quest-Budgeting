package com.example.pocketquestbudgeting.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val PocketQuestLightColorScheme = lightColorScheme(
    primary = QuestTeal,
    onPrimary = QuestOnTeal,
    primaryContainer = QuestTealContainer,
    onPrimaryContainer = QuestOnTealContainer,
    secondary = QuestGreen,
    onSecondary = QuestOnTeal,
    secondaryContainer = QuestGreenContainer,
    onSecondaryContainer = QuestOnTealContainer,
    tertiary = QuestTeal,
    onTertiary = QuestOnTeal,
    tertiaryContainer = QuestTealContainer,
    onTertiaryContainer = QuestOnTealContainer,
    background = QuestOffWhite,
    onBackground = QuestOnBackground,
    surface = QuestOffWhite,
    onSurface = QuestOnBackground,
    surfaceVariant = QuestSurfaceVariant,
    onSurfaceVariant = QuestOnSurfaceVariant,
    surfaceTint = QuestTeal,
    inversePrimary = QuestTealContainer,
    outline = QuestOutline,
    outlineVariant = QuestOutlineVariant,
)

@Composable
fun PocketQuestTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PocketQuestLightColorScheme,
        typography = PocketQuestTypography,
        content = content,
    )
}
