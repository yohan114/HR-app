package com.hr.payroll.internal

import com.hr.payroll.BankAdviceFile
import com.hr.payroll.BankAdviceRecord
import com.hr.payroll.BankAdviceService
import com.hr.payroll.BankFileFormat
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class DefaultBankAdviceService : BankAdviceService {

    override fun generateBankAdvice(
        companyName: String,
        companyAccount: String,
        paymentDate: LocalDate,
        currency: String,
        records: List<BankAdviceRecord>,
        format: BankFileFormat,
    ): BankAdviceFile {
        require(records.isNotEmpty()) { "Cannot generate bank advice file with empty employee records" }

        val totalAmount = records
            .fold(BigDecimal.ZERO) { acc, r -> acc.add(r.amount) }
            .setScale(2, RoundingMode.HALF_UP)

        val dateStr = paymentDate.format(DateTimeFormatter.BASIC_ISO_DATE)

        val (filename, content) = when (format) {
            BankFileFormat.CSV_STANDARD -> {
                val fname = "bank_advice_${currency.lowercase()}_$dateStr.csv"
                val bytes = generateCsvFormat(companyName, companyAccount, paymentDate, currency, records, totalAmount)
                Pair(fname, bytes)
            }
            BankFileFormat.ACH_NACHA -> {
                val fname = "nacha_ach_$dateStr.txt"
                val bytes = generateNachaFormat(companyName, companyAccount, paymentDate, records, totalAmount)
                Pair(fname, bytes)
            }
            BankFileFormat.SLIPS_STANDARD -> {
                val fname = "slips_${currency.lowercase()}_$dateStr.txt"
                val bytes = generateSlipsFormat(companyName, companyAccount, paymentDate, currency, records, totalAmount)
                Pair(fname, bytes)
            }
        }

        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(content)
        val hashHex = hashBytes.joinToString("") { "%02x".format(it) }

        return BankAdviceFile(
            filename = filename,
            format = format,
            content = content,
            totalRecords = records.size,
            totalAmount = totalAmount,
            batchHash = hashHex,
        )
    }

    private fun generateCsvFormat(
        companyName: String,
        companyAccount: String,
        paymentDate: LocalDate,
        currency: String,
        records: List<BankAdviceRecord>,
        totalAmount: BigDecimal,
    ): ByteArray {
        val sb = StringBuilder()
        sb.append("# COMPANY: ").append(escapeCsv(companyName)).append("\n")
        sb.append("# COMPANY_ACCOUNT: ").append(escapeCsv(companyAccount)).append("\n")
        sb.append("# PAYMENT_DATE: ").append(paymentDate).append("\n")
        sb.append("# CURRENCY: ").append(currency).append("\n")
        sb.append("EmployeeCode,EmployeeName,BankCode,BranchCode,AccountNumber,Amount,PaymentReference\n")

        for (r in records) {
            sb.append(escapeCsv(r.employeeCode)).append(",")
                .append(escapeCsv(r.employeeName)).append(",")
                .append(escapeCsv(r.bankCode)).append(",")
                .append(escapeCsv(r.branchCode)).append(",")
                .append(escapeCsv(r.accountNumber)).append(",")
                .append(r.amount.setScale(2, RoundingMode.HALF_UP)).append(",")
                .append(escapeCsv(r.paymentReference)).append("\n")
        }

        sb.append("# SUMMARY: TOTAL_COUNT=").append(records.size)
            .append(",TOTAL_AMOUNT=").append(totalAmount).append("\n")

        return sb.toString().toByteArray(StandardCharsets.UTF_8)
    }

    private fun generateNachaFormat(
        companyName: String,
        companyAccount: String,
        paymentDate: LocalDate,
        records: List<BankAdviceRecord>,
        totalAmount: BigDecimal,
    ): ByteArray {
        val lines = mutableListOf<String>()
        val dateYYMMDD = paymentDate.format(DateTimeFormatter.ofPattern("yyMMdd"))
        val timeHHMM = "0900"

        // 1. File Header (Record Type '1') - 94 chars
        val fileHeader = StringBuilder()
            .append("1") // Record Type Code
            .append("01") // Priority Code
            .append(" 123456789") // Immediate Destination (10 chars)
            .append(padRight(companyAccount.take(10), 10)) // Immediate Origin
            .append(dateYYMMDD) // File Creation Date
            .append(timeHHMM) // File Creation Time
            .append("A") // File ID Modifier
            .append("094") // Record Size
            .append("10") // Blocking Factor
            .append("1") // Format Code
            .append(padRight("BANK OF COMMERCE", 23)) // Destination Name
            .append(padRight(companyName.take(23), 23)) // Origin Name
            .append(padRight("PAYROLL", 8)) // Reference Code
        lines.add(ensureLength94(fileHeader.toString()))

        // 2. Batch Header (Record Type '5') - 94 chars
        val batchHeader = StringBuilder()
            .append("5") // Record Type
            .append("220") // Service Class: Credits only
            .append(padRight(companyName.take(16), 16)) // Company Name
            .append(padRight("PAYROLL", 20)) // Discretionary Data
            .append(padRight(companyAccount.take(10), 10)) // Company ID
            .append("PPD") // Standard Entry Class Code
            .append(padRight("SALARY", 10)) // Entry Description
            .append(dateYYMMDD) // Descriptive Date
            .append(dateYYMMDD) // Effective Entry Date
            .append("   ") // Settlement Date (Julian, blank for creation)
            .append("1") // Originator Status Code
            .append("12345678") // Originating DFI ID
            .append("0000001") // Batch Number
        lines.add(ensureLength94(batchHeader.toString()))

        // 3. Entry Detail Records (Record Type '6') - 94 chars each
        var entryHashSum = 0L
        for ((idx, r) in records.withIndex()) {
            val amountCents = r.amount.multiply(BigDecimal(100)).toLong()
            val routing9 = padRight(r.bankCode.filter { it.isDigit() }.take(9), 9, '0')
            val routing8 = routing9.take(8)
            val checkDigit = routing9.last()

            entryHashSum += routing8.toLongOrNull() ?: 0L

            val detail = StringBuilder()
                .append("6") // Record Type
                .append("22") // Transaction Code (22 = Checking Credit, Direct Deposit)
                .append(routing8) // Receiving DFI ID (8 digits)
                .append(checkDigit) // Check Digit (1 digit)
                .append(padRight(r.accountNumber.take(17), 17)) // DFI Account Number
                .append(padLeft(amountCents.toString(), 10, '0')) // Amount in cents
                .append(padRight(r.employeeCode.take(15), 15)) // Individual ID
                .append(padRight(r.employeeName.take(22), 22)) // Individual Name
                .append("  ") // Discretionary Data
                .append("0") // Addenda Record Indicator
                .append(padLeft((idx + 1).toString(), 15, '0')) // Trace Number
            lines.add(ensureLength94(detail.toString()))
        }

        // 4. Batch Control Record (Record Type '8') - 94 chars
        val totalCents = totalAmount.multiply(BigDecimal(100)).toLong()
        val entryHash10 = padLeft((entryHashSum % 10000000000L).toString(), 10, '0')

        val batchControl = StringBuilder()
            .append("8") // Record Type
            .append("220") // Service Class
            .append(padLeft(records.size.toString(), 6, '0')) // Entry/Addenda Count
            .append(entryHash10) // Entry Hash
            .append(padLeft("0", 12, '0')) // Total Debit Amount
            .append(padLeft(totalCents.toString(), 12, '0')) // Total Credit Amount
            .append(padRight(companyAccount.take(10), 10)) // Company ID
            .append(padRight("", 19)) // Message Authentication Code
            .append(padRight("", 6)) // Reserved
            .append("12345678") // Originating DFI ID
            .append("0000001") // Batch Number
        lines.add(ensureLength94(batchControl.toString()))

        // 5. File Control Record (Record Type '9') - 94 chars
        val totalLinesExcludingNines = lines.size + 1 // including Type 9 line
        val blockFactor = 10
        val blockCount = (totalLinesExcludingNines + blockFactor - 1) / blockFactor

        val fileControl = StringBuilder()
            .append("9") // Record Type
            .append("000001") // Batch Count
            .append(padLeft(blockCount.toString(), 6, '0')) // Block Count
            .append(padLeft(records.size.toString(), 8, '0')) // Total Entry Addenda Count
            .append(entryHash10) // Entry Hash
            .append(padLeft("0", 12, '0')) // Total Debit Amount
            .append(padLeft(totalCents.toString(), 12, '0')) // Total Credit Amount
            .append(padRight("", 39)) // Reserved
        lines.add(ensureLength94(fileControl.toString()))

        // NACHA Block padding: File line count must be multiple of 10 with Type 9 fill lines
        val paddingNeeded = (blockFactor - (lines.size % blockFactor)) % blockFactor
        val ninesLine = "9".repeat(94)
        repeat(paddingNeeded) {
            lines.add(ninesLine)
        }

        return lines.joinToString("\r\n", postfix = "\r\n").toByteArray(StandardCharsets.US_ASCII)
    }

    private fun generateSlipsFormat(
        companyName: String,
        companyAccount: String,
        paymentDate: LocalDate,
        currency: String,
        records: List<BankAdviceRecord>,
        totalAmount: BigDecimal,
    ): ByteArray {
        val lines = mutableListOf<String>()
        val dateYYYYMMDD = paymentDate.format(DateTimeFormatter.BASIC_ISO_DATE)
        val totalCents = totalAmount.multiply(BigDecimal(100)).toLong()

        // 1. Header Record (Record Type '20') - 150 chars
        val originatingBank = records.firstOrNull()?.bankCode?.take(4) ?: "7010"
        val originatingBranch = records.firstOrNull()?.branchCode?.take(3) ?: "001"
        val header = StringBuilder()
            .append("20")
            .append(padRight(originatingBank, 4))
            .append(padRight(originatingBranch, 3))
            .append(padRight(companyAccount.take(15), 15))
            .append(padRight(companyName.take(30), 30))
            .append(dateYYYYMMDD)
            .append(padRight(currency.take(3).uppercase(), 3))
            .append(padLeft(totalCents.toString(), 12, '0'))
            .append(padLeft("0", 12, '0'))
            .append(padLeft(records.size.toString(), 6, '0'))
            .append(padRight("", 55))
        lines.add(ensureLength150(header.toString()))

        // 2. Detail Records (Record Type '23') - 150 chars each
        for (r in records) {
            val amountCents = r.amount.multiply(BigDecimal(100)).toLong()
            val detail = StringBuilder()
                .append("23")
                .append("22") // Transaction Code 22 = Direct Credit
                .append(padRight(r.bankCode.take(4), 4))
                .append(padRight(r.branchCode.take(3), 3))
                .append(padRight(r.accountNumber.take(15), 15))
                .append(padRight(r.employeeName.take(30), 30))
                .append(padLeft(amountCents.toString(), 12, '0'))
                .append(padRight(originatingBank, 4))
                .append(padRight(originatingBranch, 3))
                .append(padRight(companyAccount.take(15), 15))
                .append(padRight(r.paymentReference.take(20), 20))
                .append(padRight(r.employeeCode.take(15), 15))
                .append(padRight("", 25))
            lines.add(ensureLength150(detail.toString()))
        }

        // 3. Trailer Record (Record Type '29') - 150 chars
        val trailer = StringBuilder()
            .append("29")
            .append(padLeft(records.size.toString(), 6, '0'))
            .append(padLeft(totalCents.toString(), 12, '0'))
            .append(padRight("", 132))
        lines.add(ensureLength150(trailer.toString()))

        return lines.joinToString("\r\n", postfix = "\r\n").toByteArray(StandardCharsets.US_ASCII)
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    private fun ensureLength94(str: String): String {
        return if (str.length >= 94) str.take(94) else padRight(str, 94)
    }

    private fun ensureLength150(str: String): String {
        return if (str.length >= 150) str.take(150) else padRight(str, 150)
    }

    private fun padRight(s: String, width: Int, padChar: Char = ' '): String {
        return if (s.length >= width) s.take(width) else s + padChar.toString().repeat(width - s.length)
    }

    private fun padLeft(s: String, width: Int, padChar: Char = ' '): String {
        return if (s.length >= width) s.takeLast(width) else padChar.toString().repeat(width - s.length) + s
    }
}
