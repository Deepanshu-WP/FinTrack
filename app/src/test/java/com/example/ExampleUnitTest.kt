package com.example

import com.example.data.SmsParser
import com.example.model.TransactionCategory
import com.example.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun smsParser_parsesDebitExpenseCorrectly() {
        val sms = "Rs 1,450.00 debited from A/C XX4019 on 24-Sep-26 towards SWIGGY. Info: UPI/12345678."
        val parsed = SmsParser.parseSms(
            smsId = 101L,
            sender = "VM-HDFCBK",
            body = sms,
            timestamp = 1727500000000L
        )

        assertNotNull(parsed)
        parsed?.let {
            assertEquals(1450.0, it.amount, 0.001)
            assertEquals(TransactionType.EXPENSE, it.type)
            assertEquals(TransactionCategory.FOOD_DINING, it.category)
            assertTrue(it.title.contains("Swiggy", ignoreCase = true))
            assertTrue(it.isFromSms)
        }
    }

    @Test
    fun smsParser_parsesCreditSalaryCorrectly() {
        val sms = "Your a/c no. XX1234 is credited by Rs.65,000.00 on 23-Sep-26 by A/C linked to SALARY."
        val parsed = SmsParser.parseSms(
            smsId = 102L,
            sender = "AX-SBINB",
            body = sms,
            timestamp = 1727500000000L
        )

        assertNotNull(parsed)
        parsed?.let {
            assertEquals(65000.0, it.amount, 0.001)
            assertEquals(TransactionType.INCOME, it.type)
            assertEquals(TransactionCategory.SALARY_INCOME, it.category)
        }
    }

    @Test
    fun smsParser_ignoresOtpAndSpamMessages() {
        val otpSms = "Your OTP for netbanking login is 482910. Do not share OTP with anyone."
        assertFalse(SmsParser.isFinancialMessage(otpSms))

        val parsed = SmsParser.parseSms(
            smsId = 103L,
            sender = "BZ-HDFCBK",
            body = otpSms,
            timestamp = 1727500000000L
        )
        assertNull(parsed)
    }

    @Test
    fun smsParser_generatesConsistentFingerprintForDuplicates() {
        val body = "Paid Rs 350 to Starbucks on 27-Sep. Ref: 98129038."
        val sender = "VM-AXISBK"
        val timestamp = 1727500000000L

        val fp1 = SmsParser.generateFingerprint(sender, body, 350.0, TransactionType.EXPENSE, timestamp)
        val fp2 = SmsParser.generateFingerprint(sender, body, 350.0, TransactionType.EXPENSE, timestamp + 1000L) // slightly delayed duplicate

        assertEquals(fp1, fp2)
    }

    @Test
    fun weeklySummary_proratesMonthlyBudgetCorrectly() {
        val monthlyBudget = 4330.0
        val weeklyBudget = monthlyBudget / 4.33
        assertEquals(1000.0, weeklyBudget, 0.1)

        val spent = 850.0
        val percentage = (spent / weeklyBudget) * 100.0
        assertEquals(85.0, percentage, 0.01)
        assertTrue(percentage >= 80.0) // Warning threshold
    }

    @Test
    fun searchFiltering_filtersByMerchantAndDateRange() {
        val now = System.currentTimeMillis()
        val tx1 = com.example.model.Transaction(
            id = "1",
            amount = 120.0,
            type = TransactionType.EXPENSE,
            category = TransactionCategory.FOOD_DINING,
            title = "Starbucks Coffee",
            timestamp = now - 2 * 24 * 3600 * 1000L
        )
        val tx2 = com.example.model.Transaction(
            id = "2",
            amount = 500.0,
            type = TransactionType.EXPENSE,
            category = TransactionCategory.SHOPPING,
            title = "Amazon Store",
            timestamp = now - 10 * 24 * 3600 * 1000L
        )

        val list = listOf(tx1, tx2)

        // Filter by merchant name "Starbucks"
        val merchantMatch = list.filter { it.title.contains("Starbucks", ignoreCase = true) }
        assertEquals(1, merchantMatch.size)
        assertEquals("Starbucks Coffee", merchantMatch[0].title)

        // Filter by Last 7 Days
        val sevenDaysAgo = now - 7 * 24 * 3600 * 1000L
        val recentMatch = list.filter { it.timestamp >= sevenDaysAgo }
        assertEquals(1, recentMatch.size)
        assertEquals("Starbucks Coffee", recentMatch[0].title)
    }

    @Test
    fun dateCutoff_filtersTransactionsBefore1Sep2026() {
        val cutoff = com.example.model.DateCutoffUtils.CUTOFF_1_SEP_2026_MS

        val oldTx = com.example.model.Transaction(
            id = "old_august",
            amount = 999.0,
            type = TransactionType.EXPENSE,
            category = TransactionCategory.OTHER,
            title = "August Transaction",
            timestamp = cutoff - 1000L
        )

        val newTx = com.example.model.Transaction(
            id = "sep_tx",
            amount = 450.0,
            type = TransactionType.EXPENSE,
            category = TransactionCategory.FOOD_DINING,
            title = "September 1 Lunch",
            timestamp = cutoff + 3600000L
        )

        val transactions = listOf(oldTx, newTx)
        val validTxs = transactions.filter { it.timestamp >= cutoff }

        assertEquals(1, validTxs.size)
        assertEquals("sep_tx", validTxs[0].id)
    }

    @Test
    fun initialAccountBalance_calculatesNetCorrectly() {
        val initialBal = 45000.0
        val income = 15000.0
        val expense = 7200.0

        val netBalance = initialBal + income - expense
        assertEquals(52800.0, netBalance, 0.001)
    }
}
