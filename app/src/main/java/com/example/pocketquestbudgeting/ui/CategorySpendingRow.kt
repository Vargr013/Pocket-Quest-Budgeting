package com.example.pocketquestbudgeting.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.pocketquestbudgeting.data.CategorySpendingTotal

@Composable
internal fun CategorySpendingRow(category: CategorySpendingTotal, overallCents: Long) {
    val percentage = categorySpendingPercentage(category.totalCents, overallCents)
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(Color(0xFFE8EEEC), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(category.categoryName, style = MaterialTheme.typography.titleMedium, color = Color(0xFF1A2B28))
        Text(formatRand(category.totalCents), color = Color(0xFF1A2B28))
        Text("$percentage of spending", color = Color(0xFF6B7C78))
        LinearProgressIndicator(
            progress = { categorySpendingFraction(category.totalCents, overallCents) },
            modifier = Modifier.fillMaxWidth().height(8.dp).semantics {
                contentDescription = "${category.categoryName}: $percentage of spending"
            },
            color = Color(0xFF2A9D8F),
            trackColor = Color(0xFF5E5E5E),
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}
