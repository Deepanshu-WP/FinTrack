package com.example.data

import android.content.Context
import com.example.model.AlertLevel
import com.example.model.AppFinancialData
import com.example.model.BudgetPeriod
import com.example.model.CategoryBudget
import com.example.model.FinancialGoal
import com.example.model.GoalPriority
import com.example.model.Transaction
import com.example.model.TransactionCategory
import com.example.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class JsonStorageManager(private val context: Context) {

    private val mutex = Mutex()
    private val fileName = "financial_data.json"
    private val storageFile: File
        get() = File(context.filesDir, fileName)

    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    suspend fun loadData(): AppFinancialData = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = storageFile
            if (!file.exists() || file.length() == 0L) {
                val initialData = createDefaultInitialData()
                saveDataInternal(initialData)
                return@withLock initialData
            }

            try {
                val jsonString = file.readText(Charsets.UTF_8)
                val parsed = parseJsonToAppData(jsonString)
                // Filter out any data strictly before 1-Sep-2026 and lingering seed test data
                val cleanedTransactions = parsed.transactions.filter {
                    it.timestamp >= com.example.model.DateCutoffUtils.CUTOFF_1_SEP_2026_MS &&
                    !it.id.startsWith("tx_seed_") && !it.id.startsWith("demo_") && !it.fingerprint.startsWith("seed_")
                }
                if (cleanedTransactions.size != parsed.transactions.size) {
                    val cleanedData = parsed.copy(
                        transactions = cleanedTransactions,
                        processedFingerprints = parsed.processedFingerprints.filter { !it.startsWith("seed_") }
                    )
                    saveDataInternal(cleanedData)
                    cleanedData
                } else {
                    parsed
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Return fallback initial clean data if corrupted
                createDefaultInitialData()
            }
        }
    }

    suspend fun exportJsonToUri(uri: android.net.Uri): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val jsonString = getRawJsonStringInternal()
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    private fun getRawJsonStringInternal(): String {
        val file = storageFile
        return if (file.exists()) {
            file.readText(Charsets.UTF_8)
        } else {
            "{}"
        }
    }

    suspend fun saveData(data: AppFinancialData): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            saveDataInternal(data)
        }
    }

    private fun saveDataInternal(data: AppFinancialData): Boolean {
        return try {
            val updatedData = data.copy(lastUpdatedIso = isoDateFormat.format(Date()))
            val jsonObject = appDataToJson(updatedData)
            val formattedJson = jsonObject.toString(2) // pretty print indent 2

            val file = storageFile
            val tempFile = File(context.filesDir, "$fileName.tmp")

            tempFile.writeText(formattedJson, Charsets.UTF_8)
            if (tempFile.exists()) {
                if (file.exists()) {
                    file.delete()
                }
                tempFile.renameTo(file)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getRawJsonString(): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = storageFile
            if (file.exists()) {
                file.readText(Charsets.UTF_8)
            } else {
                "{}"
            }
        }
    }

    fun getFileDetails(): StorageFileInfo {
        val file = storageFile
        return StorageFileInfo(
            filePath = file.absolutePath,
            exists = file.exists(),
            sizeBytes = if (file.exists()) file.length() else 0L,
            lastModifiedMs = if (file.exists()) file.lastModified() else 0L
        )
    }

    private fun appDataToJson(data: AppFinancialData): JSONObject {
        val root = JSONObject()
        root.put("version", data.version)
        root.put("currencySymbol", data.currencySymbol)
        root.put("initialAccountBalance", data.initialAccountBalance)
        root.put("lastSyncTimestampMs", data.lastSyncTimestampMs)
        root.put("lastUpdatedIso", data.lastUpdatedIso)

        // Processed SMS IDs
        val smsIdsArray = JSONArray()
        data.processedSmsIds.forEach { smsIdsArray.put(it) }
        root.put("processedSmsIds", smsIdsArray)

        // Processed Fingerprints
        val fingerprintsArray = JSONArray()
        data.processedFingerprints.forEach { fingerprintsArray.put(it) }
        root.put("processedFingerprints", fingerprintsArray)

        // Transactions
        val transactionsArray = JSONArray()
        data.transactions.forEach { tx ->
            val txObj = JSONObject()
            txObj.put("id", tx.id)
            txObj.put("amount", tx.amount)
            txObj.put("type", tx.type.name)
            txObj.put("category", tx.category.name)
            txObj.put("title", tx.title)
            txObj.put("description", tx.description)
            txObj.put("timestamp", tx.timestamp)
            txObj.put("accountOrCard", tx.accountOrCard)
            txObj.put("isFromSms", tx.isFromSms)
            if (tx.smsId != null) txObj.put("smsId", tx.smsId)
            txObj.put("fingerprint", tx.fingerprint)
            txObj.put("paymentMode", tx.paymentMode)
            transactionsArray.put(txObj)
        }
        root.put("transactions", transactionsArray)

        // Budgets
        val budgetsArray = JSONArray()
        data.budgets.forEach { b ->
            val bObj = JSONObject()
            bObj.put("category", b.category.name)
            bObj.put("limitAmount", b.limitAmount)
            bObj.put("period", b.period.name)
            bObj.put("warningThresholdRatio", b.warningThresholdRatio)
            budgetsArray.put(bObj)
        }
        root.put("budgets", budgetsArray)

        // Goals
        val goalsArray = JSONArray()
        data.goals.forEach { g ->
            val gObj = JSONObject()
            gObj.put("id", g.id)
            gObj.put("title", g.title)
            gObj.put("targetAmount", g.targetAmount)
            gObj.put("currentAmount", g.currentAmount)
            gObj.put("targetDateMs", g.targetDateMs)
            gObj.put("category", g.category)
            gObj.put("colorHex", g.colorHex)
            gObj.put("priority", g.priority.name)
            goalsArray.put(gObj)
        }
        root.put("goals", goalsArray)

        return root
    }

    private fun parseJsonToAppData(jsonString: String): AppFinancialData {
        val root = JSONObject(jsonString)
        val version = root.optInt("version", 1)
        val currencySymbol = root.optString("currencySymbol", "₹")
        val initialAccountBalance = root.optDouble("initialAccountBalance", 0.0)
        val lastSyncTimestampMs = root.optLong("lastSyncTimestampMs", 0L)
        val lastUpdatedIso = root.optString("lastUpdatedIso", "")

        val processedSmsIds = mutableListOf<Long>()
        val smsIdsArray = root.optJSONArray("processedSmsIds")
        if (smsIdsArray != null) {
            for (i in 0 until smsIdsArray.length()) {
                processedSmsIds.add(smsIdsArray.getLong(i))
            }
        }

        val processedFingerprints = mutableListOf<String>()
        val fingerprintsArray = root.optJSONArray("processedFingerprints")
        if (fingerprintsArray != null) {
            for (i in 0 until fingerprintsArray.length()) {
                processedFingerprints.add(fingerprintsArray.getString(i))
            }
        }

        val transactions = mutableListOf<Transaction>()
        val transactionsArray = root.optJSONArray("transactions")
        if (transactionsArray != null) {
            for (i in 0 until transactionsArray.length()) {
                val tObj = transactionsArray.getJSONObject(i)
                val typeName = tObj.optString("type", TransactionType.EXPENSE.name)
                val type = try { TransactionType.valueOf(typeName) } catch (e: Exception) { TransactionType.EXPENSE }
                val catName = tObj.optString("category", TransactionCategory.OTHER.name)
                val category = TransactionCategory.fromName(catName)

                transactions.add(
                    Transaction(
                        id = tObj.optString("id", System.currentTimeMillis().toString()),
                        amount = tObj.optDouble("amount", 0.0),
                        type = type,
                        category = category,
                        title = tObj.optString("title", "Transaction"),
                        description = tObj.optString("description", ""),
                        timestamp = tObj.optLong("timestamp", System.currentTimeMillis()),
                        accountOrCard = tObj.optString("accountOrCard", ""),
                        isFromSms = tObj.optBoolean("isFromSms", false),
                        smsId = if (tObj.has("smsId")) tObj.optLong("smsId") else null,
                        fingerprint = tObj.optString("fingerprint", ""),
                        paymentMode = tObj.optString("paymentMode", "Manual")
                    )
                )
            }
        }

        val budgets = mutableListOf<CategoryBudget>()
        val budgetsArray = root.optJSONArray("budgets")
        if (budgetsArray != null) {
            for (i in 0 until budgetsArray.length()) {
                val bObj = budgetsArray.getJSONObject(i)
                val catName = bObj.optString("category", TransactionCategory.FOOD_DINING.name)
                val category = TransactionCategory.fromName(catName)
                val periodName = bObj.optString("period", BudgetPeriod.MONTHLY.name)
                val period = try { BudgetPeriod.valueOf(periodName) } catch (e: Exception) { BudgetPeriod.MONTHLY }

                budgets.add(
                    CategoryBudget(
                        category = category,
                        limitAmount = bObj.optDouble("limitAmount", 5000.0),
                        period = period,
                        warningThresholdRatio = bObj.optDouble("warningThresholdRatio", 0.8)
                    )
                )
            }
        }

        val goals = mutableListOf<FinancialGoal>()
        val goalsArray = root.optJSONArray("goals")
        if (goalsArray != null) {
            for (i in 0 until goalsArray.length()) {
                val gObj = goalsArray.getJSONObject(i)
                val priorityName = gObj.optString("priority", GoalPriority.HIGH.name)
                val priority = try { GoalPriority.valueOf(priorityName) } catch (e: Exception) { GoalPriority.HIGH }

                goals.add(
                    FinancialGoal(
                        id = gObj.optString("id", "g_$i"),
                        title = gObj.optString("title", "Savings Goal"),
                        targetAmount = gObj.optDouble("targetAmount", 50000.0),
                        currentAmount = gObj.optDouble("currentAmount", 10000.0),
                        targetDateMs = gObj.optLong("targetDateMs", System.currentTimeMillis() + 90L * 24 * 3600 * 1000),
                        category = gObj.optString("category", "Savings"),
                        colorHex = gObj.optString("colorHex", "#10B981"),
                        priority = priority
                    )
                )
            }
        }

        return AppFinancialData(
            version = version,
            currencySymbol = currencySymbol,
            initialAccountBalance = initialAccountBalance,
            lastSyncTimestampMs = lastSyncTimestampMs,
            lastUpdatedIso = lastUpdatedIso,
            processedSmsIds = processedSmsIds,
            processedFingerprints = processedFingerprints,
            transactions = transactions,
            budgets = budgets,
            goals = goals
        )
    }

    private fun createDefaultInitialData(): AppFinancialData {
        val now = System.currentTimeMillis()
        val dayMs = 24L * 3600 * 1000

        // Default initial budgets for categories (customizable by user)
        val defaultBudgets = listOf(
            CategoryBudget(TransactionCategory.FOOD_DINING, 8000.0, BudgetPeriod.MONTHLY, 0.8),
            CategoryBudget(TransactionCategory.GROCERIES, 6000.0, BudgetPeriod.MONTHLY, 0.8),
            CategoryBudget(TransactionCategory.SHOPPING, 5000.0, BudgetPeriod.MONTHLY, 0.8),
            CategoryBudget(TransactionCategory.BILLS_UTILITIES, 4000.0, BudgetPeriod.MONTHLY, 0.85),
            CategoryBudget(TransactionCategory.TRAVEL_TRANSPORT, 3500.0, BudgetPeriod.MONTHLY, 0.8),
            CategoryBudget(TransactionCategory.ENTERTAINMENT, 2500.0, BudgetPeriod.MONTHLY, 0.75)
        )

        // Default initial goals
        val defaultGoals = listOf(
            FinancialGoal(
                id = "goal_emergency",
                title = "Emergency Fund",
                targetAmount = 100000.0,
                currentAmount = 0.0,
                targetDateMs = now + 180L * dayMs,
                category = "Security",
                colorHex = "#10B981",
                priority = GoalPriority.HIGH
            )
        )

        return AppFinancialData(
            version = 1,
            currencySymbol = "₹",
            initialAccountBalance = 0.0,
            lastSyncTimestampMs = 0L,
            lastUpdatedIso = isoDateFormat.format(Date()),
            processedSmsIds = emptyList(),
            processedFingerprints = emptyList(),
            transactions = emptyList(), // Completely clean, 0 dummy data
            budgets = defaultBudgets,
            goals = defaultGoals
        )
    }
}

data class StorageFileInfo(
    val filePath: String,
    val exists: Boolean,
    val sizeBytes: Long,
    val lastModifiedMs: Long
)
