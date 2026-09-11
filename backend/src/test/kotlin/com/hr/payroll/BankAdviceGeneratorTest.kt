package com.hr.payroll

import com.hr.payroll.internal.DefaultBankAdviceService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.time.LocalDate

@DisplayName("Bank Advice Generator (CSV & ACH / NACHA)")
class BankAdviceGeneratorTest {

    private val bankAdviceService = DefaultBankAdviceService()

    private val records = listOf(
        BankAdviceRecord(
            employeeCode = "EMP001",
            employeeName = "Alice Smith",
            bankCode = "7010",
            branchCode = "001",
            accountNumber = "1234567890",
            amount = BigDecimal("125000.50"),
            paymentReference = "SAL-MAR26-001",
        ),
        BankAdviceRecord(
            employeeCode = "EMP002",
            employeeName = "Bob, Jones Jr.",
            bankCode = "7080",
            branchCode = "015",
            accountNumber = "9876543210",
            amount = BigDecimal("85400.00"),
            paymentReference = "SAL-MAR26-002",
        ),
    )

    @Test
    fun `generates valid Standard CSV bank advice file with headers and totals`() {
        val result = bankAdviceService.generateBankAdvice(
            companyName = "Acme Global Solutions",
            companyAccount = "9988776655",
            paymentDate = LocalDate.of(2026, 3, 25),
            currency = "LKR",
            records = records,
            format = BankFileFormat.CSV_STANDARD,
        )

        assertThat(result.filename).isEqualTo("bank_advice_lkr_20260325.csv")
        assertThat(result.totalRecords).isEqualTo(2)
        // 125,000.50 + 85,400.00 = 210,400.50
        assertThat(result.totalAmount).isEqualByComparingTo("210400.50")
        assertThat(result.batchHash).hasSize(64) // Valid SHA-256 hex string

        val csvString = String(result.content, StandardCharsets.UTF_8)
        assertThat(csvString).contains("# COMPANY: Acme Global Solutions")
        assertThat(csvString).contains("# PAYMENT_DATE: 2026-03-25")
        assertThat(csvString).contains("EmployeeCode,EmployeeName,BankCode,BranchCode,AccountNumber,Amount,PaymentReference")
        assertThat(csvString).contains("\"Bob, Jones Jr.\"") // Quotes comma-separated name
        assertThat(csvString).contains("TOTAL_AMOUNT=210400.50")
    }

    @Test
    fun `generates valid NACHA ACH file with strict 94-char records and block factor 10`() {
        val result = bankAdviceService.generateBankAdvice(
            companyName = "Acme Global US",
            companyAccount = "1122334455",
            paymentDate = LocalDate.of(2026, 3, 25),
            currency = "USD",
            records = records,
            format = BankFileFormat.ACH_NACHA,
        )

        assertThat(result.filename).isEqualTo("nacha_ach_20260325.txt")
        val lines = String(result.content, StandardCharsets.US_ASCII).split("\r\n").filter { it.isNotEmpty() }

        // Every line must be exactly 94 characters
        for ((index, line) in lines.withIndex()) {
            assertThat(line.length)
                .describedAs("Line $index length")
                .isEqualTo(94)
        }

        // Must be a multiple of 10 records (NACHA standard blocking factor)
        assertThat(lines.size % 10).isEqualTo(0)

        // Line 0: File Header (Type 1)
        assertThat(lines[0].startsWith("1")).isTrue()

        // Line 1: Batch Header (Type 5, PPD)
        assertThat(lines[1].startsWith("5220")).isTrue()
        assertThat(lines[1]).contains("PPD")

        // Lines 2 & 3: Entry Detail Records (Type 6)
        assertThat(lines[2].startsWith("622")).isTrue()
        assertThat(lines[3].startsWith("622")).isTrue()

        // Batch Control (Type 8)
        val batchControl = lines.find { it.startsWith("8220") }
        assertThat(batchControl).isNotNull

        // File Control (Type 9)
        val fileControl = lines.find { it.startsWith("9000001") }
        assertThat(fileControl).isNotNull
    }

    @Test
    fun `generates valid LankaPay SLIPS format file with strict 150-char records and batch control`() {
        val result = bankAdviceService.generateBankAdvice(
            companyName = "Acme Global Lanka",
            companyAccount = "1002003004",
            paymentDate = LocalDate.of(2026, 3, 25),
            currency = "LKR",
            records = records,
            format = BankFileFormat.SLIPS_STANDARD,
        )

        assertThat(result.filename).isEqualTo("slips_lkr_20260325.txt")
        assertThat(result.totalRecords).isEqualTo(2)
        assertThat(result.totalAmount).isEqualByComparingTo("210400.50")
        assertThat(result.batchHash).hasSize(64)

        val lines = String(result.content, StandardCharsets.US_ASCII).split("\r\n").filter { it.isNotEmpty() }
        assertThat(lines).hasSize(4) // Header (20) + 2 Detail (23) + Trailer (29)

        for ((index, line) in lines.withIndex()) {
            assertThat(line.length)
                .describedAs("SLIPS Line $index length")
                .isEqualTo(150)
        }

        // Header Record: Type 20
        assertThat(lines[0].startsWith("20")).isTrue()
        assertThat(lines[0]).contains("Acme Global Lanka")
        assertThat(lines[0]).contains("20260325")
        assertThat(lines[0]).contains("LKR")

        // Detail Records: Type 23, Transaction Code 22
        assertThat(lines[1].startsWith("2322")).isTrue()
        assertThat(lines[1]).contains("EMP001")
        assertThat(lines[2].startsWith("2322")).isTrue()
        assertThat(lines[2]).contains("EMP002")

        // Trailer Record: Type 29
        assertThat(lines[3].startsWith("29")).isTrue()
        assertThat(lines[3]).contains("000002") // 2 records
    }
}
