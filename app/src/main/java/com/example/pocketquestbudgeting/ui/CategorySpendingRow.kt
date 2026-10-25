package com.example.pocketquestbudgeting.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.pocketquestbudgeting.data.CategorySpendingTotal

private val ProgressTeal = Color(0xFF2A9D8F)
private val ProgressAmber = Color(0xFFE9A825)
private val ProgressRed = Color(0xFFD64545)

@Composable
internal fun CategorySpendingRow(
    category: CategorySpendingTotal,
    overallCents: Long,
    modifier: Modifier = Modifier,
) {
    val percentage = categorySpendingPercentage(category.totalCents, overallCents)
    val spent = formatRand(category.totalCents)
    val max = category.maxMonthlyBudget
    val hasMax = max > 0L
    val amountLine = if (hasMax) "$spent of ${formatRand(max)}" else spent
    val overLimit = hasMax && category.totalCents > max
    val nearLimit = hasMax && !overLimit && max > 0L &&
        category.totalCents >= (max * 85L / 100L)
    val barProgress = if (hasMax) {
        categoryLimitUsageFraction(category.totalCents, max)
    } else {
        categorySpendingFraction(category.totalCents, overallCents)
    }
    val colors = MaterialTheme.colorScheme
    val barColor = when {
        overLimit -> ProgressRed
        nearLimit -> ProgressAmber
        hasMax -> ProgressTeal
        else -> colors.primary
    }
    val limitLine = if (hasMax) categoryLimitUsagePercentage(category.totalCents, max) else null
    Surface(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics {
            // I read each row once instead of repeating the amount and bar separately.
            contentDescription = if (hasMax) {
                "${category.categoryName}, $amountLine, ${limitLine ?: ""}, $percentage of spending in this period"
            } else {
                "${category.categoryName}, $spent, $percentage of spending"
            }
        },
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFE8EEEC),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Text(category.categoryName, modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(
                    amountLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                    textAlign = TextAlign.End,
                )
            }
            if (hasMax) {
                Text(
                    limitLine ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overLimit) ProgressRed else colors.onSurfaceVariant,
                )
                Text(
                    "$percentage of spending in this period",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            } else {
                Text(percentage, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            LinearProgressIndicator(
                progress = { barProgress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = barColor,
                trackColor = colors.surfaceVariant,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}
