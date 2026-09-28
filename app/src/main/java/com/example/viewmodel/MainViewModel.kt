package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.JsonStorageManager
import com.example.data.SmsReaderRepository
import com.example.data.StorageFileInfo
import com.example.model.AlertLevel
import com.example.model.AppFinancialData
import com.example.model.BudgetStatus
import com.example.model.CategoryBudget
import com.example.model.FinancialGoal
import com.example.model.MonthlyTrendPoint
import com.example.model.SixMonthTrendData
import com.example.model.Transaction
import com.example.model.TransactionCategory
import com.example.model.TransactionType
import com.example.model.WeeklyCategorySummary
import com.example.model.WeeklySummaryData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    data class Success(val message: String, val newCount: Int, val skippedCount: Int) : SyncState()
    data class Error(val errorMessage: String) : SyncState()
}

data class FinancialUiState(
    val isLoading: Boolean = true,
    val currencySymbol: String = "₹",
    val initialAccountBalance: Double = 0.0,
    val transactions: List<Transaction> = emptyList(),
    val budgets: List<CategoryBudget> = emptyList(),
    val goals: List<FinancialGoal> = emptyList(),
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val netBalance: Double = 0.0,
    val categorySpendings: Map<TransactionCategory, Double> = emptyMap(),
    val budgetStatuses: List<BudgetStatus> = emptyList(),
    val activeAlerts: List<BudgetStatus> = emptyList(),
    val weeklySummary: WeeklySummaryData? = null,
    val sixMonthTrendData: SixMonthTrendData? = null,
    val syncState: SyncState = SyncState.Idle,
    val lastSyncTimestampMs: Long = 0L,
    val storageInfo: StorageFileInfo? = null,
    val processedSmsCount: Int = 0,
    val processedFingerprintCount: Int = 0
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val storageManager = JsonStorageManager(application)
    private val smsRepository = SmsReaderRepository(application)

    private val _uiState = MutableStateFlow(FinancialUiState())
    val uiState: StateFlow<FinancialUiState> = _uiState.asStateFlow()

    private var currentData: AppFinancialData = AppFinancialData()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            currentData = storageManager.loadData()
            recalculateUiState(currentData)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun recalculateUiState(data: AppFinancialData) {
        val income = data.transactions
            .filter { it.type == TransactionType.INCOME }
            .sumOf { it.amount }

        val expense = data.transactions
            .filter { it.type == TransactionType.EXPENSE }
            .sumOf { it.amount }

        val net = data.initialAccountBalance + income - expense

        // Spending by category
        val catSpendings = mutableMapOf<TransactionCategory, Double>()
        TransactionCategory.entries.forEach { cat ->
            catSpendings[cat] = 0.0
        }
        data.transactions
            .filter { it.type == TransactionType.EXPENSE }
            .forEach { tx ->
                catSpendings[tx.category] = (catSpendings[tx.category] ?: 0.0) + tx.amount
            }

        // Budget calculation & alerts
        val statuses = data.budgets.map { b ->
            val spent = catSpendings[b.category] ?: 0.0
            val remaining = b.limitAmount - spent
            val percentage = if (b.limitAmount > 0) (spent / b.limitAmount) * 100.0 else 0.0

            val alertLevel = when {
                spent >= b.limitAmount -> AlertLevel.EXCEEDED
                spent >= b.limitAmount * b.warningThresholdRatio -> AlertLevel.WARNING
                else -> AlertLevel.SAFE
            }

            val message = when (alertLevel) {
                AlertLevel.EXCEEDED -> "Limit exceeded by ${data.currencySymbol}${(spent - b.limitAmount).toInt()}!"
                AlertLevel.WARNING -> "Used ${percentage.toInt()}% of monthly limit"
                AlertLevel.SAFE -> "${data.currencySymbol}${remaining.toInt()} remaining"
            }

            BudgetStatus(
                budget = b,
                currentSpent = spent,
                remaining = remaining,
                percentage = percentage,
                alertLevel = alertLevel,
                message = message
            )
        }

        val activeAlerts = statuses.filter { it.alertLevel != AlertLevel.SAFE }
        val fileInfo = storageManager.getFileDetails()

        // Calculate Weekly Summary
        val calendar = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val weekStartMs = calendar.timeInMillis
        val weekEndMs = weekStartMs + (7L * 24 * 3600 * 1000) - 1

        val weeklyExpenses = data.transactions.filter {
            it.type == TransactionType.EXPENSE && it.timestamp >= weekStartMs && it.timestamp <= weekEndMs
        }

        val weeklyCatSpendings = mutableMapOf<TransactionCategory, Double>()
        weeklyExpenses.forEach { tx ->
            weeklyCatSpendings[tx.category] = (weeklyCatSpendings[tx.category] ?: 0.0) + tx.amount
        }

        val weeklyCategorySummaries = data.budgets.map { b ->
            val spent = weeklyCatSpendings[b.category] ?: 0.0
            val weeklyBudgetLimit = b.limitAmount / 4.33
            val percentage = if (weeklyBudgetLimit > 0) (spent / weeklyBudgetLimit) * 100.0 else 0.0
            val alertLevel = when {
                spent >= weeklyBudgetLimit -> AlertLevel.EXCEEDED
                spent >= weeklyBudgetLimit * b.warningThresholdRatio -> AlertLevel.WARNING
                else -> AlertLevel.SAFE
            }
            WeeklyCategorySummary(
                category = b.category,
                weeklySpent = spent,
                weeklyBudget = weeklyBudgetLimit,
                percentage = percentage,
                alertLevel = alertLevel,
                remaining = weeklyBudgetLimit - spent
            )
        }

        val totalWeeklySpent = weeklyExpenses.sumOf { it.amount }
        val totalWeeklyBudget = data.budgets.sumOf { it.limitAmount / 4.33 }
        val overallWeeklyPercentage = if (totalWeeklyBudget > 0) (totalWeeklySpent / totalWeeklyBudget) * 100.0 else 0.0

        val weeklySummaryData = WeeklySummaryData(
            weekStartDateMs = weekStartMs,
            weekEndDateMs = weekEndMs,
            totalWeeklySpent = totalWeeklySpent,
            totalWeeklyBudget = totalWeeklyBudget,
            overallPercentage = overallWeeklyPercentage,
            categorySummaries = weeklyCategorySummaries
        )

        // Calculate 6-Month Income vs. Expenses Trend
        val trendPoints = mutableListOf<MonthlyTrendPoint>()
        val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

        for (i in 5 downTo 0) {
            val cal = Calendar.getInstance().apply {
                add(Calendar.MONTH, -i)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val mStart = cal.timeInMillis
            val mLabel = monthNames[cal.get(Calendar.MONTH)]
            val mYear = cal.get(Calendar.YEAR)

            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val mEnd = cal.timeInMillis

            val monthIncome = data.transactions
                .filter { it.type == TransactionType.INCOME && it.timestamp in mStart..mEnd }
                .sumOf { it.amount }

            val monthExpense = data.transactions
                .filter { it.type == TransactionType.EXPENSE && it.timestamp in mStart..mEnd }
                .sumOf { it.amount }

            trendPoints.add(
                MonthlyTrendPoint(
                    monthLabel = mLabel,
                    year = mYear,
                    income = monthIncome,
                    expense = monthExpense,
                    net = monthIncome - monthExpense,
                    startTimestampMs = mStart,
                    endTimestampMs = mEnd
                )
            )
        }

        val maxTrendAmount = trendPoints.maxOfOrNull { maxOf(it.income, it.expense) } ?: 1000.0
        val sixMonthTrend = SixMonthTrendData(
            points = trendPoints,
            maxAmount = maxTrendAmount,
            total6MonthIncome = trendPoints.sumOf { it.income },
            total6MonthExpense = trendPoints.sumOf { it.expense }
        )

        _uiState.update { state ->
            state.copy(
                currencySymbol = data.currencySymbol,
                initialAccountBalance = data.initialAccountBalance,
                transactions = data.transactions.sortedByDescending { it.timestamp },
                budgets = data.budgets,
                goals = data.goals,
                totalIncome = income,
                totalExpense = expense,
                netBalance = net,
                categorySpendings = catSpendings,
                budgetStatuses = statuses,
                activeAlerts = activeAlerts,
                weeklySummary = weeklySummaryData,
                sixMonthTrendData = sixMonthTrend,
                lastSyncTimestampMs = data.lastSyncTimestampMs,
                storageInfo = fileInfo,
                processedSmsCount = data.processedSmsIds.size,
                processedFingerprintCount = data.processedFingerprints.size
            )
        }
    }

    /**
     * Manually sets the initial/opening account balance as of 1-Sep-2026.
     */
    fun setInitialAccountBalance(amount: Double) {
        viewModelScope.launch {
            val updated = currentData.copy(initialAccountBalance = amount)
            currentData = updated
            storageManager.saveData(updated)
            recalculateUiState(updated)
        }
    }

    /**
     * Reads real SMS messages from inbox across all conversation groups.
     * With fullScan = true, reads all messages in all threads.
     */
    fun syncInboxMessages(force: Boolean = false, fullScan: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(syncState = SyncState.Syncing) }

            val existingIds = currentData.processedSmsIds.toSet()
            val existingFingerprints = currentData.processedFingerprints.toSet()

            val (newTxs, syncResult) = smsRepository.syncInboxMessages(
                existingSmsIds = existingIds,
                existingFingerprints = existingFingerprints,
                lastSyncTimestampMs = currentData.lastSyncTimestampMs,
                force = force,
                fullScan = fullScan
            )

            if (newTxs.isNotEmpty()) {
                val updatedTxs = currentData.transactions + newTxs
                val updatedSmsIds = currentData.processedSmsIds + newTxs.mapNotNull { it.smsId }
                val updatedFingerprints = currentData.processedFingerprints + newTxs.map { it.fingerprint }

                currentData = currentData.copy(
                    transactions = updatedTxs,
                    processedSmsIds = updatedSmsIds.distinct(),
                    processedFingerprints = updatedFingerprints.distinct(),
                    lastSyncTimestampMs = syncResult.lastSyncTimestampMs
                )

                // Save to local JSON text file
                storageManager.saveData(currentData)
                recalculateUiState(currentData)
            }

            _uiState.update {
                it.copy(
                    syncState = SyncState.Success(
                        message = syncResult.message,
                        newCount = syncResult.newTransactionsCount,
                        skippedCount = syncResult.duplicatesSkippedCount
                    )
                )
            }
        }
    }

    suspend fun exportJsonBackup(uri: android.net.Uri): Boolean {
        return storageManager.exportJsonToUri(uri)
    }

    fun clearAllTransactions() {
        viewModelScope.launch {
            val clearedData = currentData.copy(
                transactions = emptyList(),
                processedSmsIds = emptyList(),
                processedFingerprints = emptyList(),
                lastSyncTimestampMs = 0L
            )
            currentData = clearedData
            storageManager.saveData(clearedData)
            recalculateUiState(clearedData)
        }
    }

    /**
     * Simulates receiving realistic bank SMS messages for emulator / offline testing.
     * Demonstrates parsing and automatic deduplication of repeated messages.
     */
    fun simulateSampleBankSms() {
        viewModelScope.launch {
            _uiState.update { it.copy(syncState = SyncState.Syncing) }

            val existingIds = currentData.processedSmsIds.toSet()
            val existingFingerprints = currentData.processedFingerprints.toSet()

            val (newTxs, syncResult) = smsRepository.simulateSampleSync(
                existingSmsIds = existingIds,
                existingFingerprints = existingFingerprints,
                lastSyncTimestampMs = currentData.lastSyncTimestampMs
            )

            if (newTxs.isNotEmpty()) {
                val updatedTxs = currentData.transactions + newTxs
                val updatedSmsIds = currentData.processedSmsIds + newTxs.mapNotNull { it.smsId }
                val updatedFingerprints = currentData.processedFingerprints + newTxs.map { it.fingerprint }

                currentData = currentData.copy(
                    transactions = updatedTxs,
                    processedSmsIds = updatedSmsIds.distinct(),
                    processedFingerprints = updatedFingerprints.distinct(),
                    lastSyncTimestampMs = syncResult.lastSyncTimestampMs
                )

                // Save to local phone JSON file
                storageManager.saveData(currentData)
                recalculateUiState(currentData)
            }

            _uiState.update {
                it.copy(
                    syncState = SyncState.Success(
                        message = syncResult.message,
                        newCount = syncResult.newTransactionsCount,
                        skippedCount = syncResult.duplicatesSkippedCount
                    )
                )
            }
        }
    }

    fun dismissSyncAlert() {
        _uiState.update { it.copy(syncState = SyncState.Idle) }
    }

    fun addManualTransaction(transaction: Transaction) {
        viewModelScope.launch {
            // Strictly enforce cutoff: transactions prior to 1-Sep-2026 are not allowed
            val validatedTx = if (transaction.timestamp < com.example.model.DateCutoffUtils.CUTOFF_1_SEP_2026_MS) {
                transaction.copy(timestamp = com.example.model.DateCutoffUtils.CUTOFF_1_SEP_2026_MS)
            } else {
                transaction
            }
            val updated = currentData.copy(
                transactions = currentData.transactions + validatedTx
            )
            currentData = updated
            storageManager.saveData(currentData)
            recalculateUiState(currentData)
        }
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch {
            val updated = currentData.copy(
                transactions = currentData.transactions.filter { it.id != id }
            )
            currentData = updated
            storageManager.saveData(currentData)
            recalculateUiState(currentData)
        }
    }

    fun saveCategoryBudget(budget: CategoryBudget) {
        viewModelScope.launch {
            val existing = currentData.budgets.toMutableList()
            val index = existing.indexOfFirst { it.category == budget.category }
            if (index >= 0) {
                existing[index] = budget
            } else {
                existing.add(budget)
            }

            val updated = currentData.copy(budgets = existing)
            currentData = updated
            storageManager.saveData(currentData)
            recalculateUiState(currentData)
        }
    }

    fun addFinancialGoal(goal: FinancialGoal) {
        viewModelScope.launch {
            val updated = currentData.copy(goals = currentData.goals + goal)
            currentData = updated
            storageManager.saveData(currentData)
            recalculateUiState(currentData)
        }
    }

    fun contributeToGoal(goalId: String, amount: Double) {
        viewModelScope.launch {
            val updatedGoals = currentData.goals.map { g ->
                if (g.id == goalId) {
                    g.copy(currentAmount = g.currentAmount + amount)
                } else g
            }
            val updated = currentData.copy(goals = updatedGoals)
            currentData = updated
            storageManager.saveData(currentData)
            recalculateUiState(currentData)
        }
    }

    suspend fun getRawJsonContent(): String {
        return storageManager.getRawJsonString()
    }

    fun hasSmsPermission(): Boolean {
        return smsRepository.hasSmsPermission()
    }
}
