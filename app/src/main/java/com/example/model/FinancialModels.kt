package com.example.model

enum class TransactionType {
    EXPENSE,
    INCOME
}

enum class TransactionCategory(
    val displayName: String,
    val iconName: String,
    val defaultColorHex: String
) {
    FOOD_DINING("Food & Dining", "Restaurant", "#F97316"),
    GROCERIES("Groceries", "ShoppingCart", "#10B981"),
    SHOPPING("Shopping", "ShoppingBag", "#8B5CF6"),
    BILLS_UTILITIES("Bills & Utilities", "Receipt", "#06B6D4"),
    TRAVEL_TRANSPORT("Travel & Transport", "DirectionsCar", "#3B82F6"),
    ENTERTAINMENT("Entertainment", "Movie", "#EC4899"),
    HEALTH_WELLNESS("Health & Wellness", "LocalHospital", "#EF4444"),
    INVESTMENTS("Investments", "TrendingUp", "#14B8A6"),
    SALARY_INCOME("Salary & Income", "AccountBalanceWallet", "#22C55E"),
    OTHER("Other", "MoreHoriz", "#64748B");

    companion object {
        fun fromName(name: String?): TransactionCategory {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) || it.displayName.equals(name, ignoreCase = true) } ?: OTHER
        }
    }
}

data class Transaction(
    val id: String,
    val amount: Double,
    val type: TransactionType,
    val category: TransactionCategory,
    val title: String,
    val description: String = "",
    val timestamp: Long,
    val accountOrCard: String = "",
    val isFromSms: Boolean = false,
    val smsId: Long? = null,
    val fingerprint: String = "",
    val paymentMode: String = "Auto/SMS"
)

enum class BudgetPeriod {
    MONTHLY,
    WEEKLY
}

enum class AlertLevel {
    SAFE,
    WARNING,
    EXCEEDED
}

data class CategoryBudget(
    val category: TransactionCategory,
    val limitAmount: Double,
    val period: BudgetPeriod = BudgetPeriod.MONTHLY,
    val warningThresholdRatio: Double = 0.80 // 80% default warning
)

data class BudgetStatus(
    val budget: CategoryBudget,
    val currentSpent: Double,
    val remaining: Double,
    val percentage: Double,
    val alertLevel: AlertLevel,
    val message: String
)

data class WeeklyCategorySummary(
    val category: TransactionCategory,
    val weeklySpent: Double,
    val weeklyBudget: Double,
    val percentage: Double,
    val alertLevel: AlertLevel,
    val remaining: Double
)

data class WeeklySummaryData(
    val weekStartDateMs: Long,
    val weekEndDateMs: Long,
    val totalWeeklySpent: Double,
    val totalWeeklyBudget: Double,
    val overallPercentage: Double,
    val categorySummaries: List<WeeklyCategorySummary>
)

data class MonthlyTrendPoint(
    val monthLabel: String,
    val year: Int,
    val income: Double,
    val expense: Double,
    val net: Double,
    val startTimestampMs: Long,
    val endTimestampMs: Long
)

data class SixMonthTrendData(
    val points: List<MonthlyTrendPoint>,
    val maxAmount: Double,
    val total6MonthIncome: Double,
    val total6MonthExpense: Double
)

enum class DateRangeFilter(val displayName: String) {
    ALL("All Time"),
    LAST_7_DAYS("Last 7 Days"),
    THIS_MONTH("This Month"),
    LAST_30_DAYS("Last 30 Days"),
    LAST_6_MONTHS("Last 6 Months")
}

enum class GoalPriority {
    HIGH,
    MEDIUM,
    LOW
}

data class FinancialGoal(
    val id: String,
    val title: String,
    val targetAmount: Double,
    val currentAmount: Double,
    val targetDateMs: Long,
    val category: String = "Savings",
    val colorHex: String = "#10B981",
    val priority: GoalPriority = GoalPriority.HIGH
) {
    val progressPercentage: Double
        get() = if (targetAmount > 0) ((currentAmount / targetAmount) * 100).coerceIn(0.0, 100.0) else 0.0

    val remainingAmount: Double
        get() = (targetAmount - currentAmount).coerceAtLeast(0.0)
}

object DateCutoffUtils {
    // Cutoff timestamp: 1-Sep-2026 00:00:00 (Transactions prior to this date are strictly ignored)
    val CUTOFF_1_SEP_2026_MS: Long by lazy {
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, 2026)
            set(java.util.Calendar.MONTH, java.util.Calendar.SEPTEMBER)
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}

data class AppFinancialData(
    val version: Int = 1,
    val currencySymbol: String = "₹",
    val initialAccountBalance: Double = 0.0, // Last amount / starting balance in account as of 1-Sep-2026
    val lastSyncTimestampMs: Long = 0L,
    val lastUpdatedIso: String = "",
    val processedSmsIds: List<Long> = emptyList(),
    val processedFingerprints: List<String> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val budgets: List<CategoryBudget> = emptyList(),
    val goals: List<FinancialGoal> = emptyList()
)

data class SyncResult(
    val newTransactionsCount: Int,
    val duplicatesSkippedCount: Int,
    val nonFinancialSkippedCount: Int,
    val lastSyncTimestampMs: Long,
    val executionTimeMs: Long,
    val message: String
)
