package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AlertLevel
import com.example.model.CategoryBudget
import com.example.model.TransactionCategory
import com.example.ui.components.CategoryBudgetCard
import com.example.ui.components.WeeklySummaryCard
import com.example.ui.components.formatCurrency
import com.example.ui.dialogs.SetBudgetDialog
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.RedExpense
import com.example.viewmodel.FinancialUiState

enum class BudgetViewMode {
    WEEKLY,
    MONTHLY
}

@Composable
fun BudgetsScreen(
    uiState: FinancialUiState,
    onSaveBudget: (CategoryBudget) -> Unit,
    modifier: Modifier = Modifier
) {
    var showBudgetDialog by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<CategoryBudget?>(null) }
    var viewMode by remember { mutableStateOf(BudgetViewMode.WEEKLY) }

    val totalBudgetLimit = uiState.budgets.sumOf { it.limitAmount }
    val totalBudgetSpent = uiState.budgetStatuses.sumOf { it.currentSpent }
    val totalRemaining = (totalBudgetLimit - totalBudgetSpent).coerceAtLeast(0.0)

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("budgets_screen_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Category Budget Alerts",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Set thresholds & prevent overspending",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = {
                            editingBudget = null
                            showBudgetDialog = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("button_add_budget")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Set Budget", fontSize = 12.sp)
                    }
                }
            }

            // View Mode Switcher: Weekly Summary vs Monthly Budgets
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (viewMode == BudgetViewMode.WEEKLY) EmeraldPrimary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { viewMode = BudgetViewMode.WEEKLY }
                            .padding(vertical = 8.dp)
                            .testTag("tab_weekly_summary"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Weekly Summary",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (viewMode == BudgetViewMode.WEEKLY) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (viewMode == BudgetViewMode.MONTHLY) EmeraldPrimary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { viewMode = BudgetViewMode.MONTHLY }
                            .padding(vertical = 8.dp)
                            .testTag("tab_monthly_budgets"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Monthly Budgets",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (viewMode == BudgetViewMode.MONTHLY) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (viewMode == BudgetViewMode.WEEKLY) {
                // Weekly Summary View displaying total spending versus set budgets using progress bars
                item {
                    uiState.weeklySummary?.let { weekly ->
                        WeeklySummaryCard(
                            weeklySummary = weekly,
                            currencySymbol = uiState.currencySymbol
                        )
                    }
                }
            } else {
                // Total Monthly Budget Summary
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
                                Text(
                                    text = "Total Monthly Budget Cap",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = EmeraldPrimary.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "${uiState.budgets.size} Active Budgets",
                                        color = EmeraldPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(
                                    text = formatCurrency(totalBudgetLimit, uiState.currencySymbol),
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Total Spent: ${formatCurrency(totalBudgetSpent, uiState.currencySymbol)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (totalBudgetSpent > totalBudgetLimit) RedExpense else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Safe Remaining: ${formatCurrency(totalRemaining, uiState.currencySymbol)}",
                                        fontSize = 11.sp,
                                        color = EmeraldPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                // Active Alerts Section
                if (uiState.activeAlerts.isNotEmpty()) {
                    item {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = AmberAlert,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Active Threshold Alerts (${uiState.activeAlerts.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                uiState.activeAlerts.forEach { alert ->
                                    val isExceeded = alert.alertLevel == AlertLevel.EXCEEDED
                                    val color = if (isExceeded) RedExpense else AmberAlert

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .padding(14.dp)
                                                .fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(CircleShape)
                                                    .background(color.copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = color,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${alert.budget.category.displayName} - ${if (isExceeded) "Exceeded" else "Warning Alert"}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = color
                                                )
                                                Text(
                                                    text = alert.message,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Text(
                                                text = "${alert.percentage.toInt()}%",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 14.sp,
                                                color = color
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Category Budgets List Header
                item {
                    Text(
                        text = "Monthly Category Breakdown",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Category Cards
                items(uiState.budgetStatuses, key = { it.budget.category.name }) { status ->
                    CategoryBudgetCard(
                        status = status,
                        currencySymbol = uiState.currencySymbol,
                        onEditClick = {
                            editingBudget = status.budget
                            showBudgetDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showBudgetDialog) {
        SetBudgetDialog(
            initialBudget = editingBudget,
            onDismiss = { showBudgetDialog = false },
            onSave = { budget ->
                onSaveBudget(budget)
                showBudgetDialog = false
            },
            currencySymbol = uiState.currencySymbol
        )
    }
}
