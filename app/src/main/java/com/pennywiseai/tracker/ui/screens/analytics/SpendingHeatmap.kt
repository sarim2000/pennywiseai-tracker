package com.pennywiseai.tracker.ui.screens.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pennywiseai.tracker.ui.components.BalancePoint
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun SpendingHeatmap(
    data: List<BalancePoint>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    val maxAmount = remember(data) { data.maxOfOrNull { it.balance.toDouble() } ?: 1.0 }
    val groupedData = remember(data) {
        data.associate { it.timestamp.toLocalDate() to it.balance.toDouble() }
    }

    val sortedDates = remember(data) { data.map { it.timestamp.toLocalDate() }.distinct().sorted() }
    val startDate = sortedDates.first().with(DayOfWeek.MONDAY)
    val endDate = sortedDates.last()

    val totalWeeks = ChronoUnit.WEEKS.between(startDate, endDate.plusDays(1)).toInt() + 1

    val monthLabels = remember(startDate, endDate) {
        val allMonthStarts = mutableListOf<Pair<Int, String>>()
        var current = startDate
        var lastMonth = -1
        var weekIndex = 0

        while (current <= endDate) {
            if (current.monthValue != lastMonth) {
                val formatter = DateTimeFormatter.ofPattern("MMM")
                allMonthStarts.add(weekIndex to current.format(formatter))
                lastMonth = current.monthValue
            }
            current = current.plusWeeks(1)
            weekIndex++
        }

        val filteredLabels = mutableListOf<Pair<Int, String>>()
        for (i in allMonthStarts.indices) {
            val (week, label) = allMonthStarts[i]

            if (i == 0 && allMonthStarts.size > 1) {
                val nextWeek = allMonthStarts[1].first
                if (nextWeek - week < 4) continue
            }

            if (filteredLabels.isEmpty()) {
                filteredLabels.add(week to label)
            } else {
                val lastAddedWeek = filteredLabels.last().first
                if (week - lastAddedWeek >= 4) {
                    filteredLabels.add(week to label)
                }
            }
        }
        filteredLabels
    }

    val scrollState = rememberScrollState()
    LaunchedEffect(data) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    dayLabels.forEach { label ->
                        Box(
                            modifier = Modifier.size(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .horizontalScroll(scrollState)
                        .padding(bottom = 8.dp)
                ) {
                    for (w in 0 until totalWeeks) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (d in 0 until 7) {
                                val date = startDate.plusWeeks(w.toLong()).plusDays(d.toLong())
                                val amount = groupedData[date] ?: 0.0
                                val intensity = if (maxAmount > 0) (amount / maxAmount).toFloat().coerceIn(0f, 1f) else 0f

                                val primary = MaterialTheme.colorScheme.primary
                                val color = when {
                                    date > endDate -> MaterialTheme.colorScheme.surfaceContainerHigh
                                    amount == 0.0 -> MaterialTheme.colorScheme.surfaceContainerHigh
                                    intensity < 0.25f -> primary.copy(alpha = 0.25f)
                                    intensity < 0.5f -> primary.copy(alpha = 0.5f)
                                    intensity < 0.75f -> primary.copy(alpha = 0.75f)
                                    else -> primary
                                }

                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(color)
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp)
            ) {
                monthLabels.forEach { (weekIndex, label) ->
                    val xOffset = (weekIndex * 24).dp
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        modifier = Modifier.offset(x = xOffset)
                    )
                }
            }
        }
    }
}
