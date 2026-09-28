package com.example.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.example.model.SyncResult
import com.example.model.Transaction
import com.example.model.TransactionCategory
import com.example.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmsReaderRepository(private val context: Context) {

    private var lastAttemptTimestampMs: Long = 0L

    fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Reads inbox SMS using an incremental query cursor (date > lastSyncTimestampMs)
     * for minimal battery consumption.
     * Deduplicates by both provider SMS ID and semantic message fingerprint.
     */
    suspend fun syncInboxMessages(
        existingSmsIds: Set<Long>,
        existingFingerprints: Set<String>,
        lastSyncTimestampMs: Long,
        force: Boolean = false,
        fullScan: Boolean = false
    ): Pair<List<Transaction>, SyncResult> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        // Battery optimization throttle: avoid spamming ContentProvider within 10 seconds unless forced
        if (!force && !fullScan && startTime - lastAttemptTimestampMs < 10_000L) {
            return@withContext Pair(
                emptyList(),
                SyncResult(
                    newTransactionsCount = 0,
                    duplicatesSkippedCount = 0,
                    nonFinancialSkippedCount = 0,
                    lastSyncTimestampMs = lastSyncTimestampMs,
                    executionTimeMs = 0,
                    message = "Battery-saver: sync throttled (already checked recently)"
                )
            )
        }
        lastAttemptTimestampMs = startTime

        if (!hasSmsPermission()) {
            return@withContext Pair(
                emptyList(),
                SyncResult(
                    newTransactionsCount = 0,
                    duplicatesSkippedCount = 0,
                    nonFinancialSkippedCount = 0,
                    lastSyncTimestampMs = lastSyncTimestampMs,
                    executionTimeMs = System.currentTimeMillis() - startTime,
                    message = "SMS Permission not granted"
                )
            )
        }

        val uri: Uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.THREAD_ID
        )

        // Cutoff timestamp: Data 1-Sep 2026 se lena hai, isse pahle ka nahi lena
        val minCutoff = com.example.model.DateCutoffUtils.CUTOFF_1_SEP_2026_MS
        val cutoffTimestamp = if (fullScan || lastSyncTimestampMs < minCutoff) {
            minCutoff
        } else {
            lastSyncTimestampMs
        }

        val selection = "${Telephony.Sms.DATE} >= ?"
        val selectionArgs = arrayOf(cutoffTimestamp.toString())
        val sortOrder = "${Telephony.Sms.DATE} DESC"

        val newTransactions = mutableListOf<Transaction>()
        val seenSmsIds = existingSmsIds.toMutableSet()
        val seenFingerprints = existingFingerprints.toMutableSet()
        val distinctThreads = mutableSetOf<Long>()

        var duplicatesSkipped = 0
        var nonFinancialSkipped = 0
        var totalMessagesScanned = 0
        var maxObservedTimestamp = lastSyncTimestampMs

        try {
            val cursor = context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressCol = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyCol = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateCol = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val threadIdCol = it.getColumnIndex(Telephony.Sms.THREAD_ID)

                while (it.moveToNext()) {
                    val smsId = it.getLong(idCol)
                    val sender = it.getString(addressCol) ?: "Unknown"
                    val body = it.getString(bodyCol) ?: ""
                    val date = it.getLong(dateCol)

                    // Strictly ignore any message before 1-Sep-2026
                    if (date < minCutoff) {
                        continue
                    }

                    totalMessagesScanned++
                    if (threadIdCol >= 0) {
                        distinctThreads.add(it.getLong(threadIdCol))
                    }

                    if (date > maxObservedTimestamp) {
                        maxObservedTimestamp = date
                    }

                    // 1. Ek baar jis message ko read kar liya usko baar baar read na kare
                    if (seenSmsIds.contains(smsId)) {
                        duplicatesSkipped++
                        continue
                    }
                    seenSmsIds.add(smsId)

                    // 2. Check if body is financial
                    if (!SmsParser.isFinancialMessage(body)) {
                        nonFinancialSkipped++
                        continue
                    }

                    // 3. Parse financial fields
                    val parsedTx = SmsParser.parseSms(
                        smsId = smsId,
                        sender = sender,
                        body = body,
                        timestamp = date
                    )

                    if (parsedTx == null) {
                        nonFinancialSkipped++
                        continue
                    }

                    // 4. Ho sakta hai koi message multiple times hoto usko ignore kre (Fingerprint check)
                    if (seenFingerprints.contains(parsedTx.fingerprint)) {
                        duplicatesSkipped++
                        continue
                    }

                    seenFingerprints.add(parsedTx.fingerprint)
                    newTransactions.add(parsedTx)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Pair(
                emptyList(),
                SyncResult(
                    newTransactionsCount = 0,
                    duplicatesSkippedCount = duplicatesSkipped,
                    nonFinancialSkippedCount = nonFinancialSkipped,
                    lastSyncTimestampMs = lastSyncTimestampMs,
                    executionTimeMs = System.currentTimeMillis() - startTime,
                    message = "Error scanning SMS groups: ${e.localizedMessage ?: "Unknown error"}"
                )
            )
        }

        val totalTime = System.currentTimeMillis() - startTime
        val threadsCount = if (distinctThreads.isNotEmpty()) distinctThreads.size else 1
        val summaryMsg = if (newTransactions.isNotEmpty()) {
            "Read all ${totalMessagesScanned} messages across ${threadsCount} conversation groups: Found ${newTransactions.size} transactions ($duplicatesSkipped duplicates ignored)"
        } else {
            "Scanned ${totalMessagesScanned} messages across ${threadsCount} groups: No new transactions ($duplicatesSkipped duplicates ignored)"
        }

        Pair(
            newTransactions,
            SyncResult(
                newTransactionsCount = newTransactions.size,
                duplicatesSkippedCount = duplicatesSkipped,
                nonFinancialSkippedCount = nonFinancialSkipped,
                lastSyncTimestampMs = if (maxObservedTimestamp > 0) maxObservedTimestamp else System.currentTimeMillis(),
                executionTimeMs = totalTime,
                message = summaryMsg
            )
        )
    }

    /**
     * Provides sample realistic bank & UPI SMS messages for testing on emulators
     * or devices without real transaction SMS.
     * Demonstrates parsing, deduplication, and budget alerts!
     */
    fun simulateSampleSync(
        existingSmsIds: Set<Long>,
        existingFingerprints: Set<String>,
        lastSyncTimestampMs: Long
    ): Pair<List<Transaction>, SyncResult> {
        val startTime = System.currentTimeMillis()
        val dayMs = 24L * 3600 * 1000

        val sampleBankMessages = listOf(
            SampleSmsItem(
                id = 901L,
                sender = "VM-HDFCBK",
                body = "Rs 1,890.00 debited from A/C XX4019 on 27-Sep-26 towards SWIGGY. Info: UPI/328910482910. Avl Bal: Rs 48,110.00.",
                timestamp = System.currentTimeMillis() - 2 * 3600 * 1000
            ),
            SampleSmsItem(
                id = 902L,
                sender = "AX-SBINB",
                body = "Dear Customer, INR 3,450.00 has been spent on your CREDIT CARD XX9102 at ZEPTO on 27-Sep-26. Avl Limit: Rs 92,000.",
                timestamp = System.currentTimeMillis() - 5 * 3600 * 1000
            ),
            SampleSmsItem(
                id = 903L,
                sender = "AD-ICICIB",
                body = "A/C 1234 debited for INR 450.00 on 26-Sep-26 by UPI: uber@kotak. Avl Bal: Rs 44,210.",
                timestamp = System.currentTimeMillis() - 1 * dayMs
            ),
            SampleSmsItem(
                id = 904L,
                sender = "VK-AXISBK",
                body = "Txn of INR 1,299.00 made on card ending 4421 at NETFLIX COM on 25-Sep-26. Avl limit: Rs 45,000.",
                timestamp = System.currentTimeMillis() - 2 * dayMs
            ),
            SampleSmsItem(
                id = 905L,
                sender = "AD-HDFCBK",
                body = "ALERT: You've made a payment of Rs.2,150.00 to Apollo Pharmacy via PhonePe UPI. Ref: 82910283.",
                timestamp = System.currentTimeMillis() - 3 * dayMs
            ),
            SampleSmsItem(
                id = 906L,
                sender = "BZ-PAYTMB",
                body = "Paid Rs 3,100 to Indian Oil Petrol Pump for Fuel via Paytm UPI. Avl Bal Rs 12,400.",
                timestamp = System.currentTimeMillis() - 4 * dayMs
            ),
            SampleSmsItem(
                id = 907L,
                sender = "VM-HDFCBK",
                body = "Your A/C XX4019 is credited by Rs 15,000.00 on 26-Sep-26 by A/C linked to Freelance Project Refund.",
                timestamp = System.currentTimeMillis() - 2 * dayMs
            ),
            // Duplicate sample message sent twice by carrier
            SampleSmsItem(
                id = 908L,
                sender = "VM-HDFCBK",
                body = "Rs 1,890.00 debited from A/C XX4019 on 27-Sep-26 towards SWIGGY. Info: UPI/328910482910. Avl Bal: Rs 48,110.00.",
                timestamp = System.currentTimeMillis() - 2 * 3600 * 1000
            ),
            // Non financial SMS (OTP)
            SampleSmsItem(
                id = 909L,
                sender = "BZ-HDFCBK",
                body = "Your OTP for netbanking login is 482910. Do not share OTP with anyone.",
                timestamp = System.currentTimeMillis() - 1 * 3600 * 1000
            )
        )

        val newTransactions = mutableListOf<Transaction>()
        val seenSmsIds = existingSmsIds.toMutableSet()
        val seenFingerprints = existingFingerprints.toMutableSet()
        var duplicatesSkipped = 0
        var nonFinancialSkipped = 0

        for (item in sampleBankMessages) {
            if (seenSmsIds.contains(item.id)) {
                duplicatesSkipped++
                continue
            }
            seenSmsIds.add(item.id)

            if (!SmsParser.isFinancialMessage(item.body)) {
                nonFinancialSkipped++
                continue
            }

            val parsed = SmsParser.parseSms(
                smsId = item.id,
                sender = item.sender,
                body = item.body,
                timestamp = item.timestamp
            )

            if (parsed == null) {
                nonFinancialSkipped++
                continue
            }

            if (seenFingerprints.contains(parsed.fingerprint)) {
                duplicatesSkipped++
                continue
            }

            seenFingerprints.add(parsed.fingerprint)
            newTransactions.add(parsed)
        }

        val totalTime = System.currentTimeMillis() - startTime
        val summaryMsg = if (newTransactions.isNotEmpty()) {
            "Simulated sync: Parsed ${newTransactions.size} new bank SMS ($duplicatesSkipped duplicate/repeat messages skipped, $nonFinancialSkipped OTP/promos ignored)"
        } else {
            "All simulated SMS were already read! ($duplicatesSkipped duplicate messages ignored)"
        }

        return Pair(
            newTransactions,
            SyncResult(
                newTransactionsCount = newTransactions.size,
                duplicatesSkippedCount = duplicatesSkipped,
                nonFinancialSkippedCount = nonFinancialSkipped,
                lastSyncTimestampMs = System.currentTimeMillis(),
                executionTimeMs = totalTime,
                message = summaryMsg
            )
        )
    }

    private data class SampleSmsItem(
        val id: Long,
        val sender: String,
        val body: String,
        val timestamp: Long
    )
}
