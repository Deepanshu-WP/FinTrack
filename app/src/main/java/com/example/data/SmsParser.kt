package com.example.data

import com.example.model.Transaction
import com.example.model.TransactionCategory
import com.example.model.TransactionType
import java.security.MessageDigest
import java.util.Locale
import java.util.regex.Pattern

object SmsParser {

    private val nonFinancialKeywords = listOf(
        "otp", "verification code", "code to login", "secret code",
        "do not share", "password", "offer", "congratulations",
        "win", "lottery", "cashback offer", "discount", "survey"
    )

    private val debitKeywords = listOf(
        "debited", "spent", "paid", "withdrawn", "sent", "transferred to",
        "txn of", "charged", "purchase of", "dr", "deducted", "dr.", "payment of"
    )

    private val creditKeywords = listOf(
        "credited", "received", "deposited", "refund", "salary",
        "added", "cr", "cr.", "credited to"
    )

    // Regex for financial amounts: e.g. Rs 1,450.00, Rs. 500, INR 1200, ₹499, $45.90, USD 100
    private val amountPatterns = listOf(
        Pattern.compile("""(?:rs\.?|inr|₹|\$|usd)\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)\s*(?:rs\.?|inr|₹|\$|usd)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:debited|spent|paid|withdrawn|credited|refund|txn of|payment of)\s+(?:by|for|of)?\s*(?:(?:rs\.?|inr|₹|\$|usd)\s*)?([0-9,]+(?:\.[0-9]{1,2})?)""", Pattern.CASE_INSENSITIVE)
    )

    private val merchantPatterns = listOf(
        Pattern.compile("""(?:at|to|towards|vpa|info:)\s+([A-Za-z0-9\s._&@'-]{3,35})(?:\s+on|\s+ref|\s+avl|\s+bal|\.|\,|$)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""paid to\s+([A-Za-z0-9\s._&@'-]{3,35})(?:\s+on|\s+via|\.|\,|$)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""purchase at\s+([A-Za-z0-9\s._&@'-]{3,35})(?:\s+on|\.|\,|$)""", Pattern.CASE_INSENSITIVE)
    )

    private val accountPatterns = listOf(
        Pattern.compile("""(?:a/c|acct|acc|account|card)\s*(?:no\.?)?\s*(?:ending\s*)?[xX*]*([0-9]{4})""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""[xX*]{2,}([0-9]{4})""", Pattern.CASE_INSENSITIVE)
    )

    fun isFinancialMessage(body: String): Boolean {
        val lower = body.lowercase(Locale.ROOT)

        // Ignore pure OTPs and spam messages unless it explicitly mentions debit/credit amount
        val hasOtp = nonFinancialKeywords.any { lower.contains(it) }
        val hasDebit = debitKeywords.any { lower.contains(it) }
        val hasCredit = creditKeywords.any { lower.contains(it) }

        if (hasOtp && !hasDebit && !hasCredit) {
            return false
        }

        return hasDebit || hasCredit
    }

    fun parseSms(
        smsId: Long,
        sender: String,
        body: String,
        timestamp: Long
    ): Transaction? {
        if (!isFinancialMessage(body)) return null

        val amount = extractAmount(body) ?: return null
        if (amount <= 0.0) return null

        val type = determineTransactionType(body)
        val merchant = extractMerchant(body)
        val account = extractAccount(body)
        val category = categorize(merchant, body, type)
        val fingerprint = generateFingerprint(sender, body, amount, type, timestamp)

        val title = when {
            merchant.isNotBlank() -> merchant
            type == TransactionType.INCOME -> "Income Received"
            else -> "Expense / Debit"
        }

        val mode = when {
            body.contains("upi", ignoreCase = true) -> "UPI"
            body.contains("card", ignoreCase = true) || body.contains("pos", ignoreCase = true) -> "Card"
            body.contains("atm", ignoreCase = true) -> "ATM Cash"
            body.contains("netbanking", ignoreCase = true) || body.contains("neft", ignoreCase = true) || body.contains("imps", ignoreCase = true) -> "Net Banking"
            else -> "Bank SMS"
        }

        return Transaction(
            id = "sms_${smsId}_${timestamp}",
            amount = amount,
            type = type,
            category = category,
            title = title,
            description = body.take(160),
            timestamp = timestamp,
            accountOrCard = if (account.isNotBlank()) "A/C XX$account" else sender,
            isFromSms = true,
            smsId = smsId,
            fingerprint = fingerprint,
            paymentMode = mode
        )
    }

    private fun extractAmount(body: String): Double? {
        for (pattern in amountPatterns) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val match = matcher.group(1) ?: continue
                val clean = match.replace(",", "").trim()
                val parsed = clean.toDoubleOrNull()
                if (parsed != null && parsed > 0.0 && parsed < 10_000_000.0) {
                    return parsed
                }
            }
        }
        return null
    }

    private fun determineTransactionType(body: String): TransactionType {
        val lower = body.lowercase(Locale.ROOT)

        val creditScore = creditKeywords.count { lower.contains(it) }
        val debitScore = debitKeywords.count { lower.contains(it) }

        return if (creditScore > debitScore) {
            TransactionType.INCOME
        } else {
            TransactionType.EXPENSE
        }
    }

    private fun extractMerchant(body: String): String {
        for (pattern in merchantPatterns) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val candidate = matcher.group(1)?.trim() ?: ""
                val clean = candidate
                    .replace(Regex("""^(on|at|to|for|via)\s+""", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("""\s+(on|at|ref|via|avl|bal)$""", RegexOption.IGNORE_CASE), "")
                    .trim()

                if (clean.length in 2..30 && !clean.contains("account", ignoreCase = true)) {
                    return formatMerchantName(clean)
                }
            }
        }
        return ""
    }

    private fun extractAccount(body: String): String {
        for (pattern in accountPatterns) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val candidate = matcher.group(1)?.trim() ?: ""
                if (candidate.length == 4) {
                    return candidate
                }
            }
        }
        return ""
    }

    private fun formatMerchantName(raw: String): String {
        return raw.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                if (word.length <= 4 && word.all { it.isLetter() && it.isUpperCase() }) word
                else word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }
    }

    fun categorize(merchant: String, body: String, type: TransactionType): TransactionCategory {
        val text = "${merchant.lowercase(Locale.ROOT)} ${body.lowercase(Locale.ROOT)}"

        if (type == TransactionType.INCOME) {
            if (text.contains("salary") || text.contains("payroll") || text.contains("stipend")) {
                return TransactionCategory.SALARY_INCOME
            }
            if (text.contains("dividend") || text.contains("interest") || text.contains("zerodha") || text.contains("groww")) {
                return TransactionCategory.INVESTMENTS
            }
            return TransactionCategory.SALARY_INCOME
        }

        return when {
            // Food & Dining
            listOf("swiggy", "zomato", "starbucks", "mcdonald", "kfc", "dominos", "pizza", "burger", "cafe", "restaurant", "bistro", "eats", "dining", "chai", "coffee").any { text.contains(it) } ->
                TransactionCategory.FOOD_DINING

            // Groceries
            listOf("blinkit", "zepto", "instamart", "bigbasket", "supermarket", "grocery", "dmart", "spencer", "nature's basket", "mart").any { text.contains(it) } ->
                TransactionCategory.GROCERIES

            // Shopping
            listOf("amazon", "flipkart", "myntra", "ajio", "zara", "h&m", "retail", "store", "mall", "shopping", "meesho", "clothing", "apparel").any { text.contains(it) } ->
                TransactionCategory.SHOPPING

            // Bills & Utilities
            listOf("electricity", "water", "gas", "recharge", "jio", "airtel", "vi", "vodafone", "broadband", "bescom", "tata power", "billdesk", "utility", "dth", "tneb").any { text.contains(it) } ->
                TransactionCategory.BILLS_UTILITIES

            // Travel & Transport
            listOf("uber", "ola", "rapido", "metro", "fuel", "petrol", "diesel", "indian oil", "bharat petroleum", "hpcl", "shell", "irctc", "railway", "airline", "indigo", "air india", "flight", "toll", "fastag").any { text.contains(it) } ->
                TransactionCategory.TRAVEL_TRANSPORT

            // Entertainment
            listOf("netflix", "spotify", "prime video", "disney", "hotstar", "cinema", "pvr", "inox", "bookmyshow", "movie", "gaming", "steam").any { text.contains(it) } ->
                TransactionCategory.ENTERTAINMENT

            // Health & Wellness
            listOf("pharmacy", "apollo", "medplus", "1mg", "pharmeasy", "hospital", "clinic", "doctor", "lab", "dental", "gym", "cult.fit").any { text.contains(it) } ->
                TransactionCategory.HEALTH_WELLNESS

            // Investments
            listOf("zerodha", "groww", "angelone", "upstox", "mutual fund", "sip", "stocks", "nse", "bse", "coin").any { text.contains(it) } ->
                TransactionCategory.INVESTMENTS

            else -> TransactionCategory.OTHER
        }
    }

    /**
     * Creates a robust, normalized deduplication fingerprint.
     * Combines sender, amount, type, and normalized body hash.
     * Timestamp is normalized into a 10-minute window bucket so repeated carrier deliveries
     * of the exact same notification are marked as duplicates and ignored.
     */
    fun generateFingerprint(
        sender: String,
        body: String,
        amount: Double,
        type: TransactionType,
        timestamp: Long
    ): String {
        val cleanSender = sender.trim().lowercase(Locale.ROOT)
        val tenMinuteBucket = timestamp / (10 * 60 * 1000)
        // Strip volatile variable parts like reference numbers/OTP/timestamp from body for hashing
        val normalizedBody = body.lowercase(Locale.ROOT)
            .replace(Regex("""\b\d{6,}\b"""), "#NUM#") // normalize long ref/txn numbers
            .replace(Regex("""\s+"""), " ")
            .trim()

        val rawSignature = "${cleanSender}_${amount}_${type.name}_${tenMinuteBucket}_$normalizedBody"
        return sha256(rawSignature)
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(24)
    }
}
