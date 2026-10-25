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
    val barProgress = if (hasMax) {
        (category.totalCents.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    } else {
        categorySpendingFraction(category.totalCents, overallCents)
    }
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics {
            // I read each row once instead of repeating the amount and bar separately.
            contentDescription = if (hasMax) {
                "${category.categoryName}, $amountLine, $percentage of spending in this period"
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
                color = colors.primary,
                trackColor = colors.surfaceVariant,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}
