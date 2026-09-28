package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MonthlyTrendPoint
import com.example.model.SixMonthTrendData
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GreenIncome
import com.example.ui.theme.RedExpense
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900

@Composable
fun RechartsLineChart(
    trendData: SixMonthTrendData,
    currencySymbol: String = "₹",
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember { mutableIntStateOf(-1) }
    val points = trendData.points

    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(trendData) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("recharts_line_chart_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header: Title & Recharts-style Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(EmeraldPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = "Trend",
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "6-Month Trend Analysis",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Income vs. Expenses over time",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Recharts Legend
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(GreenIncome)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Income",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(RedExpense)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Expenses",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Tooltip Banner when a month point is selected/tapped
            val activePoint: MonthlyTrendPoint? = if (selectedIndex in points.indices) points[selectedIndex] else null
            AnimatedVisibility(
                visible = activePoint != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                activePoint?.let { pt ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Slate800
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${pt.monthLabel} ${pt.year}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = CyanAccent
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "In: +${formatCurrency(pt.income, currencySymbol)}",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = GreenIncome
                                )
                                Text(
                                    text = "Ex: -${formatCurrency(pt.expense, currencySymbol)}",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = RedExpense
                                )
                                Text(
                                    text = "Net: ${formatCurrency(pt.net, currencySymbol)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (pt.net >= 0) EmeraldPrimary else RedExpense
                                )
                            }
                        }
                    }
                }
            }

            // Canvas Chart
            val maxAmt = trendData.maxAmount.coerceAtLeast(1000.0)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(points) {
                            detectTapGestures { offset ->
                                val spacing = size.width / (points.size - 1).coerceAtLeast(1)
                                val index = ((offset.x + (spacing / 2)) / spacing).toInt().coerceIn(0, points.size - 1)
                                selectedIndex = if (selectedIndex == index) -1 else index
                            }
                        }
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height - 24.dp.toPx() // leave room for month labels
                    val count = points.size
                    if (count < 2) return@Canvas

                    val stepX = canvasWidth / (count - 1)

                    // Draw 3 horizontal dashed Recharts-style gridlines
                    val gridLines = 3
                    val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    for (i in 0..gridLines) {
                        val y = canvasHeight * (i.toFloat() / gridLines)
                        drawLine(
                            color = Slate700.copy(alpha = 0.5f),
                            start = Offset(0f, y),
                            end = Offset(canvasWidth, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashedEffect
                        )
                    }

                    // Compute point coordinates
                    val progress = animationProgress.value
                    val incomeOffsets = points.mapIndexed { idx, pt ->
                        val x = idx * stepX
                        val ratio = (pt.income / maxAmt).toFloat().coerceIn(0f, 1f)
                        val y = canvasHeight - (ratio * canvasHeight * progress)
                        Offset(x, y)
                    }

                    val expenseOffsets = points.mapIndexed { idx, pt ->
                        val x = idx * stepX
                        val ratio = (pt.expense / maxAmt).toFloat().coerceIn(0f, 1f)
                        val y = canvasHeight - (ratio * canvasHeight * progress)
                        Offset(x, y)
                    }

                    // Helper for Bézier curve path
                    fun createCurvedPath(offsets: List<Offset>): Path {
                        val path = Path()
                        if (offsets.isEmpty()) return path
                        path.moveTo(offsets[0].x, offsets[0].y)
                        for (i in 0 until offsets.size - 1) {
                            val p0 = offsets[i]
                            val p1 = offsets[i + 1]
                            val controlX = (p0.x + p1.x) / 2
                            path.cubicTo(controlX, p0.y, controlX, p1.y, p1.x, p1.y)
                        }
                        return path
                    }

                    val incomePath = createCurvedPath(incomeOffsets)
                    val expensePath = createCurvedPath(expenseOffsets)

                    // Draw Area Gradient Fills underneath lines (Recharts area effect)
                    val incomeAreaPath = Path().apply {
                        addPath(incomePath)
                        lineTo(canvasWidth, canvasHeight)
                        lineTo(0f, canvasHeight)
                        close()
                    }
                    drawPath(
                        path = incomeAreaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                GreenIncome.copy(alpha = 0.22f * progress),
                                GreenIncome.copy(alpha = 0.02f)
                            ),
                            startY = 0f,
                            endY = canvasHeight
                        )
                    )

                    val expenseAreaPath = Path().apply {
                        addPath(expensePath)
                        lineTo(canvasWidth, canvasHeight)
                        lineTo(0f, canvasHeight)
                        close()
                    }
                    drawPath(
                        path = expenseAreaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                RedExpense.copy(alpha = 0.18f * progress),
                                RedExpense.copy(alpha = 0.01f)
                            ),
                            startY = 0f,
                            endY = canvasHeight
                        )
                    )

                    // Draw Stroke Lines
                    drawPath(
                        path = incomePath,
                        color = GreenIncome,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    drawPath(
                        path = expensePath,
                        color = RedExpense,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw Points
                    incomeOffsets.forEachIndexed { i, offset ->
                        val isSelected = selectedIndex == i
                        val radius = if (isSelected) 6.dp.toPx() else 4.dp.toPx()
                        drawCircle(
                            color = Color.White,
                            radius = radius,
                            center = offset
                        )
                        drawCircle(
                            color = GreenIncome,
                            radius = radius,
                            center = offset,
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    }

                    expenseOffsets.forEachIndexed { i, offset ->
                        val isSelected = selectedIndex == i
                        val radius = if (isSelected) 6.dp.toPx() else 4.dp.toPx()
                        drawCircle(
                            color = Color.White,
                            radius = radius,
                            center = offset
                        )
                        drawCircle(
                            color = RedExpense,
                            radius = radius,
                            center = offset,
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    }

                    // Vertical Scrubber line if point is selected
                    if (selectedIndex in points.indices) {
                        val selX = selectedIndex * stepX
                        drawLine(
                            color = CyanAccent,
                            start = Offset(selX, 0f),
                            end = Offset(selX, canvasHeight),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                    }
                }
            }

            // Month Labels (X-Axis)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                points.forEachIndexed { idx, pt ->
                    val isSelected = selectedIndex == idx
                    Text(
                        text = pt.monthLabel,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) CyanAccent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Long-term Insights Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Slate800.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val netTotal = trendData.total6MonthIncome - trendData.total6MonthExpense
                    val icon = if (netTotal >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown
                    val tint = if (netTotal >= 0) EmeraldPrimary else RedExpense

                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "6-Mo Net: ${formatCurrency(netTotal, currencySymbol)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = tint
                    )
                }

                Text(
                    text = "Tap points to inspect details",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
