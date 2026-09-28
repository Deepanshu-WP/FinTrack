package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterList
import com.example.model.DateRangeFilter
import com.example.model.Transaction
import com.example.model.TransactionCategory
import com.example.model.TransactionType
import com.example.ui.components.BalanceOverviewCard
import com.example.ui.components.BudgetAlertBanner
import com.example.ui.components.FinancialPlanningCard
import com.example.ui.components.RechartsLineChart
import com.example.ui.components.TransactionItemCard
import com.example.ui.components.WeeklySummaryCard
import com.example.ui.components.getCategoryIcon
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GreenIncome
import com.example.ui.theme.RedExpense
import com.example.ui.theme.Slate800
import com.example.viewmodel.FinancialUiState
import com.example.viewmodel.SyncState

enum class TxFilter {
    ALL,
    EXPENSES,
    INCOME,
    SMS_ONLY
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    uiState: FinancialUiState,
    onSyncSmsClick: (Boolean) -> Unit,
    onAddTransactionClick: () -> Unit,
    onSetInitialBalance: (Double) -> Unit,
    onViewBudgetsClick: () -> Unit,
    onInspectJsonClick: () -> Unit,
    onDismissSyncAlert: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf(TxFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryTag by remember { mutableStateOf<TransactionCategory?>(null) }
    var selectedDateRange by remember { mutableStateOf(DateRangeFilter.ALL) }
    var isRequestingFullScan by remember { mutableStateOf(false) }
    var showEditBalanceDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onSyncSmsClick(isRequestingFullScan)
        }
    }

    val now = remember { System.currentTimeMillis() }
    val startOfMonthMs = remember(now) {
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val filteredTransactions = uiState.transactions.filter { tx ->
        val matchesFilter = when (selectedFilter) {
            TxFilter.ALL -> true
            TxFilter.EXPENSES -> tx.type == TransactionType.EXPENSE
            TxFilter.INCOME -> tx.type == TransactionType.INCOME
            TxFilter.SMS_ONLY -> tx.isFromSms
        }
        val matchesMerchant = searchQuery.isBlank() ||
                tx.title.contains(searchQuery, ignoreCase = true) ||
                tx.description.contains(searchQuery, ignoreCase = true) ||
                tx.accountOrCard.contains(searchQuery, ignoreCase = true)

        val matchesCategory = selectedCategoryTag == null || tx.category == selectedCategoryTag

        val matchesDate = when (selectedDateRange) {
            DateRangeFilter.ALL -> true
            DateRangeFilter.LAST_7_DAYS -> tx.timestamp >= now - (7L * 24 * 3600 * 1000)
            DateRangeFilter.THIS_MONTH -> tx.timestamp >= startOfMonthMs
            DateRangeFilter.LAST_30_DAYS -> tx.timestamp >= now - (30L * 24 * 3600 * 1000)
            DateRangeFilter.LAST_6_MONTHS -> tx.timestamp >= now - (180L * 24 * 3600 * 1000)
        }

        matchesFilter && matchesMerchant && matchesCategory && matchesDate
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("home_screen_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "₹",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "FinTrack",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "Smart SMS Tracker & Analytics",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onInspectJsonClick,
                            modifier = Modifier.testTag("button_inspect_json")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "View Saved JSON",
                                tint = CyanAccent
                            )
                        }

                        IconButton(
                            onClick = { permissionLauncher.launch(Manifest.permission.READ_SMS) },
                            modifier = Modifier.testTag("button_sync_sms")
                        ) {
                            if (uiState.syncState is SyncState.Syncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.5.dp,
                                    color = EmeraldPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Sync SMS",
                                    tint = EmeraldPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Sync Notification Card (if result available)
            item {
                AnimatedVisibility(visible = uiState.syncState is SyncState.Success) {
                    val success = uiState.syncState as? SyncState.Success
                    if (success != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = EmeraldPrimary.copy(alpha = 0.15f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Success",
                                        tint = EmeraldPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = success.message,
                                        fontSize = 12.sp,
                                        color = EmeraldPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                IconButton(
                                    onClick = onDismissSyncAlert,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = EmeraldPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Balance Card with Starting Balance (1-Sep-2026) manual entry
            item {
                BalanceOverviewCard(
                    totalBalance = uiState.netBalance,
                    totalIncome = uiState.totalIncome,
                    totalExpense = uiState.totalExpense,
                    initialBalance = uiState.initialAccountBalance,
                    onEditInitialBalance = { showEditBalanceDialog = true },
                    currencySymbol = uiState.currencySymbol
                )
            }

            // Quick Starting Balance prompt if not set
            if (uiState.initialAccountBalance <= 0.0) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CyanAccent.copy(alpha = 0.12f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Set Last Account Balance",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Enter the balance you had on 1-Sep-2026 to see your accurate live net balance.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = { showEditBalanceDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
                                modifier = Modifier.testTag("button_prompt_set_starting_balance")
                            ) {
                                Text("Enter Amount", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Battery Optimization & SMS Sync Helper Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.BatteryChargingFull,
                                    contentDescription = "Battery Efficient",
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Sync from 1-Sep-2026 Onwards",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "${uiState.processedSmsCount} IDs indexed",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Only messages from 1-Sep-2026 onwards are parsed. All previous SMS are skipped. Deduplication automatically ignores repeats.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    isRequestingFullScan = true
                                    permissionLauncher.launch(Manifest.permission.READ_SMS)
                                },
                                modifier = Modifier
                                    .weight(1.1f)
                                    .testTag("button_full_scan_sms"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Scan All Groups",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Scan All Groups", fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    isRequestingFullScan = false
                                    permissionLauncher.launch(Manifest.permission.READ_SMS)
                                },
                                modifier = Modifier
                                    .weight(0.9f)
                                    .testTag("button_read_inbox_sms"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Quick Sync",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Quick Sync", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Budget Alerts Banner
            item {
                BudgetAlertBanner(
                    budgetAlerts = uiState.activeAlerts,
                    currencySymbol = uiState.currencySymbol,
                    onViewBudgetsClick = onViewBudgetsClick
                )
            }

            // Recharts 6-Month Income vs. Expenses Trend Line Chart
            item {
                uiState.sixMonthTrendData?.let { trend ->
                    RechartsLineChart(
                        trendData = trend,
                        currencySymbol = uiState.currencySymbol
                    )
                }
            }

            // Weekly Summary View (displays total spending versus set budgets with progress bars)
            item {
                uiState.weeklySummary?.let { weekly ->
                    WeeklySummaryCard(
                        weeklySummary = weekly,
                        currencySymbol = uiState.currencySymbol
                    )
                }
            }

            // Financial Planning & Safe To Spend Card
            item {
                FinancialPlanningCard(
                    totalIncome = uiState.totalIncome,
                    totalExpense = uiState.totalExpense,
                    currencySymbol = uiState.currencySymbol
                )
            }

            // Transactions Header & Search Bar
            item {
                val hasActiveFilters = searchQuery.isNotBlank() ||
                        selectedCategoryTag != null ||
                        selectedDateRange != DateRangeFilter.ALL ||
                        selectedFilter != TxFilter.ALL

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transactions_search_header")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Financial History",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (hasActiveFilters) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = EmeraldPrimary.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "Filtered (${filteredTransactions.size})",
                                        color = EmeraldPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        if (hasActiveFilters) {
                            TextButton(
                                onClick = {
                                    searchQuery = ""
                                    selectedCategoryTag = null
                                    selectedDateRange = DateRangeFilter.ALL
                                    selectedFilter = TxFilter.ALL
                                },
                                modifier = Modifier.testTag("button_reset_filters")
                            ) {
                                Text("Reset Filters", fontSize = 12.sp, color = RedExpense)
                            }
                        } else {
                            Text(
                                text = "${filteredTransactions.size} transactions",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Merchant Name & Keyword Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by merchant name, payee, or note...", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_search_transactions"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Date Range Filter Chips
                    Text(
                        text = "Date Range",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        DateRangeFilter.entries.forEach { range ->
                            FilterChip(
                                selected = selectedDateRange == range,
                                onClick = { selectedDateRange = range },
                                label = { Text(range.displayName, fontSize = 11.sp) },
                                leadingIcon = if (selectedDateRange == range) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.CalendarToday,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null,
                                modifier = Modifier.testTag("filter_date_${range.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Category Tag Filter Chips
                    Text(
                        text = "Category Tag",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // "All Categories" chip
                        FilterChip(
                            selected = selectedCategoryTag == null,
                            onClick = { selectedCategoryTag = null },
                            label = { Text("All Categories", fontSize = 11.sp) },
                            modifier = Modifier.testTag("filter_category_all")
                        )

                        TransactionCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = selectedCategoryTag == cat,
                                onClick = {
                                    selectedCategoryTag = if (selectedCategoryTag == cat) null else cat
                                },
                                label = { Text(cat.displayName, fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = getCategoryIcon(cat),
                                        contentDescription = cat.displayName,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                modifier = Modifier.testTag("filter_category_${cat.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Type Filter Chips (All, Expenses, Income, SMS Only)
                    Text(
                        text = "Type",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TxFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = {
                                    Text(
                                        text = when (filter) {
                                            TxFilter.ALL -> "All Types"
                                            TxFilter.EXPENSES -> "Expenses Only"
                                            TxFilter.INCOME -> "Income Only"
                                            TxFilter.SMS_ONLY -> "From SMS Only"
                                        },
                                        fontSize = 11.sp
                                    )
                                },
                                modifier = Modifier.testTag("filter_type_${filter.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            // Transaction items
            if (filteredTransactions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No transactions found matching criteria.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                items(filteredTransactions, key = { it.id }) { tx ->
                    TransactionItemCard(
                        transaction = tx,
                        currencySymbol = uiState.currencySymbol
                    )
                }
            }
        }

        // Floating Action Button
        ExtendedFloatingActionButton(
            onClick = onAddTransactionClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp)
                .testTag("fab_add_transaction"),
            containerColor = EmeraldPrimary,
            contentColor = Color.Black,
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Transaction")
            Spacer(modifier = Modifier.width(6.dp))
            Text("Add Record", fontWeight = FontWeight.Bold)
        }
    }

    if (showEditBalanceDialog) {
        com.example.ui.dialogs.EditInitialBalanceDialog(
            currentBalance = uiState.initialAccountBalance,
            onDismiss = { showEditBalanceDialog = false },
            onSave = { newBal ->
                onSetInitialBalance(newBal)
                showEditBalanceDialog = false
            },
            currencySymbol = uiState.currencySymbol
        )
    }
}
