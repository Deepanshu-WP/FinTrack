package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TransactionType
import com.example.ui.components.CategoryDonutChart
import com.example.ui.components.FinancialPlanningCard
import com.example.ui.components.RechartsLineChart
import com.example.ui.components.formatCurrency
import com.example.ui.components.parseColor
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GreenIncome
import com.example.ui.theme.RedExpense
import com.example.ui.theme.Slate800
import com.example.viewmodel.FinancialUiState

@Composable
fun AnalyticsScreen(
    uiState: FinancialUiState,
    modifier: Modifier = Modifier
) {
    val expenseTxs = uiState.transactions.filter { it.type == TransactionType.EXPENSE }
    val incomeTxs = uiState.transactions.filter { it.type == TransactionType.INCOME }

    // Top merchants
    val merchantTotals = mutableMapOf<String, Double>()
    val merchantCounts = mutableMapOf<String, Int>()
    expenseTxs.forEach { tx ->
        val name = tx.title.ifBlank { "Other" }
        merchantTotals[name] = (merchantTotals[name] ?: 0.0) + tx.amount
        merchantCounts[name] = (merchantCounts[name] ?: 0) + 1
    }

    val topMerchants = merchantTotals.entries
        .sortedByDescending { it.value }
        .take(5)

    // Average transaction size
    val avgExpense = if (expenseTxs.isNotEmpty()) uiState.totalExpense / expenseTxs.size else 0.0

    // Top spending category
    val topCategory = uiState.categorySpendings.maxByOrNull { it.value }

    // Net Savings Rate
    val savingsRate = if (uiState.totalIncome > 0) {
        (((uiState.totalIncome - uiState.totalExpense) / uiState.totalIncome) * 100).coerceIn(0.0, 100.0)
    } else 0.0

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("analytics_screen_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        item {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = "Visual Analytics & Habits",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Personalized financial tracking & patterns",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Summary Metric Cards (2x2 grid style)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Savings Rate Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Savings Rate", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldPrimary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TrendingUp,
                                    contentDescription = null,
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${savingsRate.toInt()}%",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = EmeraldPrimary
                        )
                        Text(
                            text = "of monthly earnings",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Avg Transaction Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Avg Burn / Txn", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(CyanAccent.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CreditCard,
                                    contentDescription = null,
                                    tint = CyanAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = formatCurrency(avgExpense, uiState.currencySymbol),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${expenseTxs.size} total debits",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Category Donut Chart
        item {
            CategoryDonutChart(
                categorySpendings = uiState.categorySpendings,
                currencySymbol = uiState.currencySymbol
            )
        }

        // Recharts 6-Month Income vs. Expense Trend Line Chart
        item {
            uiState.sixMonthTrendData?.let { trend ->
                RechartsLineChart(
                    trendData = trend,
                    currencySymbol = uiState.currencySymbol
                )
            }
        }

        // Income vs Expense Comparison Bar
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Cashflow Comparison",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    val totalFlow = (uiState.totalIncome + uiState.totalExpense).coerceAtLeast(1.0)
                    val incomeRatio = (uiState.totalIncome / totalFlow).toFloat()
                    val expenseRatio = (uiState.totalExpense / totalFlow).toFloat()

                    // Stacked visual bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(incomeRatio.coerceAtLeast(0.01f))
                                .fillMaxSize()
                                .background(GreenIncome)
                        )
                        Box(
                            modifier = Modifier
                                .weight(expenseRatio.coerceAtLeast(0.01f))
                                .fillMaxSize()
                                .background(RedExpense)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(GreenIncome))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Income: ${formatCurrency(uiState.totalIncome, uiState.currencySymbol)} (${(incomeRatio * 100).toInt()}%)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(RedExpense))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Expense: ${formatCurrency(uiState.totalExpense, uiState.currencySymbol)} (${(expenseRatio * 100).toInt()}%)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Top Spending Merchants Leaderboard
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Store,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Top Spending Merchants",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "By Total Volume",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (topMerchants.isEmpty()) {
                        Text(
                            text = "No merchant spendings recorded yet.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val maxMerchantVal = topMerchants.first().value.coerceAtLeast(1.0)
                        topMerchants.forEachIndexed { index, entry ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Slate800,
                                            modifier = Modifier.size(22.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "#${index + 1}",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = CyanAccent
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = entry.key,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = formatCurrency(entry.value, uiState.currencySymbol),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = RedExpense
                                        )
                                        Text(
                                            text = "${merchantCounts[entry.key] ?: 1} txns",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                LinearProgressIndicator(
                                    progress = { (entry.value / maxMerchantVal).toFloat() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = RedExpense.copy(alpha = 0.8f),
                                    trackColor = Slate800
                                )
                            }
                        }
                    }
                }
            }
        }

        // Financial Planning & 50/30/20 Rule
        item {
            FinancialPlanningCard(
                totalIncome = uiState.totalIncome,
                totalExpense = uiState.totalExpense,
                currencySymbol = uiState.currencySymbol
            )
        }
    }
}
