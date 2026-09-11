package com.hr.payroll

import com.hr.attendance.DayStatus
import com.hr.attendance.internal.DailyAttendance
import com.hr.attendance.internal.DailyAttendanceRepository
import com.hr.attendance.internal.DefaultAttendancePayrollSummaryService
import com.hr.payroll.internal.DefaultBankAdviceService
import com.hr.payroll.internal.DefaultGrossToNetCalculationService
import com.hr.payroll.internal.statutory.PhilippinesStatutoryCalculator
import com.hr.payroll.internal.statutory.SriLankaStatutoryCalculator
import com.hr.payroll.internal.statutory.StatutoryCalculatorRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.UUID

@DisplayName("End-to-End Payroll Run Verification (Attendance -> Gross-to-Net -> Bank Advice)")
class EndToEndPayrollRunWorkflowTest {

    private lateinit var dailyAttendanceRepository: DailyAttendanceRepository
    private lateinit var attendanceSummaryService: DefaultAttendancePayrollSummaryService
    private lateinit var grossToNetService: DefaultGrossToNetCalculationService
    private lateinit var bankAdviceService: DefaultBankAdviceService

    private val payPeriodId = UUID.randomUUID()
    private val periodStart = LocalDate.of(2026, 3, 1)
    private val periodEnd = LocalDate.of(2026, 3, 31)
    private val paymentDate = LocalDate.of(2026, 3, 25)
    private val workingDays = BigDecimal("22.00")
    private val standardHoursPerDay = BigDecimal("8.00")

    @BeforeEach
    fun setUp() {
        dailyAttendanceRepository = mock(DailyAttendanceRepository::class.java)
        attendanceSummaryService = DefaultAttendancePayrollSummaryService(dailyAttendanceRepository)

        val registry = StatutoryCalculatorRegistry(
            listOf(
                SriLankaStatutoryCalculator(),
                PhilippinesStatutoryCalculator(),
            )
        )
        grossToNetService = DefaultGrossToNetCalculationService(registry)
        bankAdviceService = DefaultBankAdviceService()
    }

    @Test
    fun `end-to-end single employee payroll run from attendance variable inputs to bank advice files`() {
        // =========================================================================
        // Step 1: Employee & Payroll Profile Setup (Sri Lanka)
        // =========================================================================
        val employeeId = UUID.randomUUID()
        val employee = EmployeePayrollProfile(
            id = employeeId,
            employeeCode = "LK010",
            displayName = "Kasun Mendis",
            countryCode = "LK",
            currency = "LKR",
            taxIdentificationNumber = "TIN-987654321",
            socialSecurityNumber = "NIC-199012345678",
            bankName = "Commercial Bank of Ceylon",
            bankCode = "7010",
            branchCode = "001",
            bankAccountNumber = "1002345678",
            joinDate = LocalDate.of(2022, 1, 15),
        )

        val contractualBasicSalary = BigDecimal("150000.00")

        // =========================================================================
        // Step 2: Ingest Daily Attendance Records with OT, Lateness, and Unapproved Absence
        // =========================================================================
        // Simulation for March 2026:
        // - 18 Normal Present Days (8h net worked, 0 OT, 0 late)
        // - 1 Day with 60 minutes lateness (late arrival penalty)
        // - 2 Days with 120 minutes normal overtime (Weekday 1.50x rate = 4.0 hrs OT)
        // - 1 Rest Day (Saturday) with 240 minutes overtime (Weekend 2.00x rate = 4.0 hrs OT)
        // - 1 Unapproved Absence Day (Loss of Pay)
        val dailyRecords = mutableListOf<DailyAttendance>()

        // 18 standard days (March 2 to March 19, weekdays)
        for (day in 2..19) {
            dailyRecords.add(
                DailyAttendance(
                    employeeId = employeeId,
                    workDate = LocalDate.of(2026, 3, day),
                    netWorkedMinutes = 480,
                    overtimeMinutesNormal = 0,
                    lateMinutes = 0,
                    dayStatus = DayStatus.PRESENT,
                )
            )
        }

        // 1 day with 60m lateness (March 20)
        dailyRecords.add(
            DailyAttendance(
                employeeId = employeeId,
                workDate = LocalDate.of(2026, 3, 20),
                netWorkedMinutes = 420,
                lateMinutes = 60,
                dayStatus = DayStatus.PRESENT,
            )
        )

        // 2 days with 120m normal weekday OT (March 23 & 24)
        dailyRecords.add(
            DailyAttendance(
                employeeId = employeeId,
                workDate = LocalDate.of(2026, 3, 23),
                netWorkedMinutes = 600,
                overtimeMinutesNormal = 120,
                dayStatus = DayStatus.PRESENT,
            )
        )
        dailyRecords.add(
            DailyAttendance(
                employeeId = employeeId,
                workDate = LocalDate.of(2026, 3, 24),
                netWorkedMinutes = 600,
                overtimeMinutesNormal = 120,
                dayStatus = DayStatus.PRESENT,
            )
        )

        // 1 Rest Day with 240m OT (Saturday March 28)
        dailyRecords.add(
            DailyAttendance(
                employeeId = employeeId,
                workDate = LocalDate.of(2026, 3, 28),
                netWorkedMinutes = 240,
                overtimeMinutesRestDay = 240,
                dayStatus = DayStatus.REST_DAY,
            )
        )

        // 1 Unapproved Absence (March 30)
        dailyRecords.add(
            DailyAttendance(
                employeeId = employeeId,
                workDate = LocalDate.of(2026, 3, 30),
                netWorkedMinutes = 0,
                dayStatus = DayStatus.ABSENT,
            )
        )

        `when`(
            dailyAttendanceRepository.findAllByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(
                employeeId,
                periodStart,
                periodEnd
            )
        ).thenReturn(dailyRecords)

        // =========================================================================
        // Step 3: Compute Attendance Payroll Variable Summary
        // =========================================================================
        val attendanceSummary = attendanceSummaryService.computePayrollItems(
            employeeId = employeeId,
            periodStartDate = periodStart,
            periodEndDate = periodEnd,
            basicSalary = contractualBasicSalary,
            standardWorkingDays = workingDays,
            standardHoursPerDay = standardHoursPerDay,
        )

        // Standard hours = 22 * 8 = 176 hrs
        // Hourly rate = 150,000 / 176 = 852.2727... -> 852.27 LKR
        assertThat(attendanceSummary.hourlyRate).isEqualByComparingTo("852.27")

        // Normal OT: 2 days x 120m = 240m = 4.0 hrs @ 1.50x = 4.0 * 852.2727 * 1.50 = 5,113.64 LKR
        val normalOt = attendanceSummary.overtimeEarnings.find { it.code == "OT_NORMAL" }
        assertThat(normalOt).isNotNull
        assertThat(normalOt!!.hours).isEqualByComparingTo("4.00")
        assertThat(normalOt.amount).isEqualByComparingTo("5113.64")

        // Rest Day OT: 1 day x 240m = 4.0 hrs @ 2.00x = 4.0 * 852.2727 * 2.00 = 6,818.18 LKR
        val restDayOt = attendanceSummary.overtimeEarnings.find { it.code == "OT_REST_DAY" }
        assertThat(restDayOt).isNotNull
        assertThat(restDayOt!!.hours).isEqualByComparingTo("4.00")
        assertThat(restDayOt.amount).isEqualByComparingTo("6818.18")

        // Lateness penalty: 60m (1.0 hr) @ 1.00x = 852.27 LKR
        val latePenalty = attendanceSummary.latenessDeductions.find { it.code == "LATE_PENALTY" }
        assertThat(latePenalty).isNotNull
        assertThat(latePenalty!!.lateMinutes).isEqualTo(60)
        assertThat(latePenalty.amount).isEqualByComparingTo("852.27")

        // Unapproved absence: 1.00 day
        assertThat(attendanceSummary.unpaidAbsenceDays).isEqualByComparingTo("1.00")

        // =========================================================================
        // Step 4: Feed into Gross-to-Net Salary Calculation Pipeline
        // =========================================================================
        val allowances = attendanceSummary.overtimeEarnings.map {
            AllowanceInput(
                code = it.code,
                name = it.name,
                amount = it.amount,
                isTaxable = true,
                isStatutoryBase = true,
            )
        }

        val deductions = attendanceSummary.latenessDeductions.map {
            DeductionInput(
                code = it.code,
                name = it.name,
                amount = it.amount,
                isPreTax = false,
            )
        }

        val gtnInput = GrossToNetInput(
            employee = employee,
            payPeriodId = payPeriodId,
            periodStartDate = periodStart,
            periodEndDate = periodEnd,
            basicSalary = contractualBasicSalary,
            allowances = allowances,
            deductions = deductions,
            workingDaysInMonth = workingDays,
            unpaidLeaveDays = attendanceSummary.unpaidAbsenceDays,
        )

        val result = grossToNetService.calculateEmployee(gtnInput)

        // Mathematical Verification:
        // 1. Loss of Pay (LOP): 150,000 * (1 / 22) = 6,818.18 LKR
        assertThat(result.lossOfPayDeduction).isEqualByComparingTo("6818.18")

        // 2. Effective Basic: 150,000 - 6,818.18 = 143,181.82 LKR
        // 3. Gross Pay: 143,181.82 (effective basic) + 5,113.64 (OT_NORMAL) + 6,818.18 (OT_REST_DAY) = 155,113.64 LKR
        assertThat(result.grossPay).isEqualByComparingTo("155113.64")

        // 4. Statutory Base: 155,113.64 LKR
        //    EPF Employee (8%): 155,113.64 * 0.08 = 12,409.09 LKR
        //    EPF Employer (12%): 155,113.64 * 0.12 = 18,613.64 LKR
        //    ETF Employer (3%): 155,113.64 * 0.03 = 4,653.41 LKR
        assertThat(result.totalStatutoryEmployee).isEqualByComparingTo("12409.09")
        assertThat(result.totalStatutoryEmployer).isEqualByComparingTo("23267.05") // 18,613.64 + 4,653.41

        // 5. APIT Tax on Taxable Income (155,113.64 LKR):
        //    - 100,000.00 tax-free
        //    - Tier 1 (41,666.67 @ 6%) = 2,500.00 LKR
        //    - Tier 2 ((155,113.64 - 141,666.67) = 13,446.97 @ 12%) = 1,613.64 LKR
        //    Total APIT = 2,500.00 + 1,613.64 = 4,113.64 LKR
        assertThat(result.taxableIncome).isEqualByComparingTo("155113.64")
        assertThat(result.taxWithheld).isEqualByComparingTo("4113.64")

        // 6. Voluntary Deductions: Lateness penalty = 852.27 LKR
        assertThat(result.totalVoluntaryDeductions).isEqualByComparingTo("852.27")

        // 7. Net Pay:
        //    Gross (155,113.64) - EPF EE (12,409.09) - APIT (4,113.64) - Lateness (852.27) = 137,738.64 LKR
        assertThat(result.netPay).isEqualByComparingTo("137738.64")

        // 8. Employer Total Cost:
        //    Gross (155,113.64) + Employer Statutory (23,267.05) = 178,380.69 LKR
        assertThat(result.employerTotalCost).isEqualByComparingTo("178380.69")

        // Verify itemized audit calculation traces
        val itemCodes = result.lines.map { it.itemCode }
        assertThat(itemCodes).contains(
            "BASIC",
            "OT_NORMAL",
            "OT_REST_DAY",
            "EPF_EE",
            "EPF_ER",
            "ETF_ER",
            "TAX_WITHHOLDING",
            "LATE_PENALTY"
        )
        assertThat(result.lines.find { it.itemCode == "BASIC" }?.calculationTrace)
            .contains("LOP (6818.18 = 150000.00 × 1.00/22.00 days)")

        // =========================================================================
        // Step 5: Bank Advice Generation & Format Validation
        // =========================================================================
        val bankRecord = BankAdviceRecord(
            employeeCode = result.employeeCode,
            employeeName = result.employeeName,
            bankCode = employee.bankCode!!,
            branchCode = employee.branchCode!!,
            accountNumber = employee.bankAccountNumber!!,
            amount = result.netPay,
            paymentReference = "SAL-202603-LK010",
        )

        // 5a. Validate CSV_STANDARD Format
        val csvAdvice = bankAdviceService.generateBankAdvice(
            companyName = "Apex Technologies Lanka (Pvt) Ltd",
            companyAccount = "998877665544",
            paymentDate = paymentDate,
            currency = "LKR",
            records = listOf(bankRecord),
            format = BankFileFormat.CSV_STANDARD,
        )

        assertThat(csvAdvice.filename).isEqualTo("bank_advice_lkr_20260325.csv")
        assertThat(csvAdvice.totalRecords).isEqualTo(1)
        assertThat(csvAdvice.totalAmount).isEqualByComparingTo("137738.64")
        assertThat(csvAdvice.batchHash).hasSize(64)

        val csvContent = String(csvAdvice.content, StandardCharsets.UTF_8)
        assertThat(csvContent).contains("# COMPANY: Apex Technologies Lanka (Pvt) Ltd")
        assertThat(csvContent).contains("# PAYMENT_DATE: 2026-03-25")
        assertThat(csvContent).contains("# CURRENCY: LKR")
        assertThat(csvContent).contains("LK010,Kasun Mendis,7010,001,1002345678,137738.64,SAL-202603-LK010")
        assertThat(csvContent).contains("# SUMMARY: TOTAL_COUNT=1,TOTAL_AMOUNT=137738.64")

        // 5b. Validate LankaPay SLIPS_STANDARD Format (150 chars fixed-width)
        val slipsAdvice = bankAdviceService.generateBankAdvice(
            companyName = "Apex Technologies Lanka (Pvt) Ltd",
            companyAccount = "998877665544",
            paymentDate = paymentDate,
            currency = "LKR",
            records = listOf(bankRecord),
            format = BankFileFormat.SLIPS_STANDARD,
        )

        assertThat(slipsAdvice.filename).isEqualTo("slips_lkr_20260325.txt")
        assertThat(slipsAdvice.totalRecords).isEqualTo(1)
        assertThat(slipsAdvice.totalAmount).isEqualByComparingTo("137738.64")
        assertThat(slipsAdvice.batchHash).hasSize(64)

        val slipsLines = String(slipsAdvice.content, StandardCharsets.US_ASCII).split("\r\n").filter { it.isNotEmpty() }
        assertThat(slipsLines).hasSize(3) // Header (20) + 1 Detail (23) + Trailer (29)

        for ((idx, line) in slipsLines.withIndex()) {
            assertThat(line.length)
                .describedAs("SLIPS line $idx length")
                .isEqualTo(150)
        }

        // Header (Type 20)
        assertThat(slipsLines[0].startsWith("20")).isTrue()
        assertThat(slipsLines[0]).contains("Apex Technologies Lanka (Pvt)")
        assertThat(slipsLines[0]).contains("20260325")
        assertThat(slipsLines[0]).contains("LKR")
        assertThat(slipsLines[0]).contains("000013773864") // Cents representation

        // Detail (Type 23, TxCode 22)
        assertThat(slipsLines[1].startsWith("2322")).isTrue()
        assertThat(slipsLines[1]).contains("7010") // Dest Bank
        assertThat(slipsLines[1]).contains("001") // Dest Branch
        assertThat(slipsLines[1]).contains("1002345678") // Dest Account
        assertThat(slipsLines[1]).contains("Kasun Mendis")
        assertThat(slipsLines[1]).contains("000013773864") // Amount in cents
        assertThat(slipsLines[1]).contains("SAL-202603-LK010")
        assertThat(slipsLines[1]).contains("LK010")

        // Trailer (Type 29)
        assertThat(slipsLines[2].startsWith("29")).isTrue()
        assertThat(slipsLines[2]).contains("000001") // Record count
        assertThat(slipsLines[2]).contains("000013773864") // Total credit in cents

        // 5c. Validate ACH_NACHA Format (94 chars fixed-width, block factor 10)
        val nachaAdvice = bankAdviceService.generateBankAdvice(
            companyName = "Apex Global US",
            companyAccount = "998877665544",
            paymentDate = paymentDate,
            currency = "USD",
            records = listOf(bankRecord),
            format = BankFileFormat.ACH_NACHA,
        )

        assertThat(nachaAdvice.filename).isEqualTo("nacha_ach_20260325.txt")
        assertThat(nachaAdvice.totalRecords).isEqualTo(1)
        assertThat(nachaAdvice.totalAmount).isEqualByComparingTo("137738.64")
        assertThat(nachaAdvice.batchHash).hasSize(64)

        val nachaLines = String(nachaAdvice.content, StandardCharsets.US_ASCII).split("\r\n").filter { it.isNotEmpty() }
        assertThat(nachaLines.size % 10).isEqualTo(0) // Strict blocking factor of 10

        for ((idx, line) in nachaLines.withIndex()) {
            assertThat(line.length)
                .describedAs("NACHA line $idx length")
                .isEqualTo(94)
        }

        assertThat(nachaLines[0].startsWith("1")).isTrue() // File Header
        assertThat(nachaLines[1].startsWith("5220")).isTrue() // Batch Header
        assertThat(nachaLines[2].startsWith("622")).isTrue() // Entry Detail
        assertThat(nachaLines[3].startsWith("8220")).isTrue() // Batch Control
        assertThat(nachaLines[4].startsWith("9000001")).isTrue() // File Control
    }

    @Test
    fun `end-to-end multi-employee pay period run verifying batch aggregation and bank advice controls`() {
        // Employee 1: Sri Lanka (Contractual Basic 150,000.00 LKR)
        val emp1 = EmployeePayrollProfile(
            id = UUID.randomUUID(),
            employeeCode = "LK001",
            displayName = "Nuwan Perera",
            countryCode = "LK",
            currency = "LKR",
            bankName = "Bank of Ceylon",
            bankCode = "7010",
            branchCode = "001",
            bankAccountNumber = "1112223334",
            joinDate = LocalDate.of(2021, 6, 1),
        )

        // Employee 2: Sri Lanka (Contractual Basic 250,000.00 LKR)
        val emp2 = EmployeePayrollProfile(
            id = UUID.randomUUID(),
            employeeCode = "LK002",
            displayName = "Dilani Fernando",
            countryCode = "LK",
            currency = "LKR",
            bankName = "Hatton National Bank",
            bankCode = "7083",
            branchCode = "015",
            bankAccountNumber = "5556667778",
            joinDate = LocalDate.of(2020, 3, 15),
        )

        // Calculate Emp 1: Perfect attendance, 2 hours normal OT
        // Basic = 150,000 / 176 = 852.2727... * 2 * 1.50 = 2,556.82 LKR
        val gtn1 = grossToNetService.calculateEmployee(
            GrossToNetInput(
                employee = emp1,
                payPeriodId = payPeriodId,
                periodStartDate = periodStart,
                periodEndDate = periodEnd,
                basicSalary = BigDecimal("150000.00"),
                allowances = listOf(
                    AllowanceInput("OT_NORMAL", "Normal Overtime", BigDecimal("2556.82"), isTaxable = true, isStatutoryBase = true)
                ),
                workingDaysInMonth = workingDays,
                unpaidLeaveDays = BigDecimal.ZERO,
            )
        )

        // Calculate Emp 2: Basic 250,000, 1 unpaid absence day (LOP = 250,000 / 22 = 11,363.64 LKR)
        val gtn2 = grossToNetService.calculateEmployee(
            GrossToNetInput(
                employee = emp2,
                payPeriodId = payPeriodId,
                periodStartDate = periodStart,
                periodEndDate = periodEnd,
                basicSalary = BigDecimal("250000.00"),
                workingDaysInMonth = workingDays,
                unpaidLeaveDays = BigDecimal("1.00"),
            )
        )

        assertThat(gtn1.netPay).isGreaterThan(BigDecimal.ZERO)
        assertThat(gtn2.netPay).isGreaterThan(BigDecimal.ZERO)

        val batchRecords = listOf(
            BankAdviceRecord(
                employeeCode = emp1.employeeCode,
                employeeName = emp1.displayName,
                bankCode = emp1.bankCode!!,
                branchCode = emp1.branchCode!!,
                accountNumber = emp1.bankAccountNumber!!,
                amount = gtn1.netPay,
                paymentReference = "SAL-202603-LK001",
            ),
            BankAdviceRecord(
                employeeCode = emp2.employeeCode,
                employeeName = emp2.displayName,
                bankCode = emp2.bankCode!!,
                branchCode = emp2.branchCode!!,
                accountNumber = emp2.bankAccountNumber!!,
                amount = gtn2.netPay,
                paymentReference = "SAL-202603-LK002",
            ),
        )

        val totalExpectedNet = gtn1.netPay.add(gtn2.netPay)

        val slipsBatch = bankAdviceService.generateBankAdvice(
            companyName = "Acme Lanka Pvt Ltd",
            companyAccount = "9988776655",
            paymentDate = paymentDate,
            currency = "LKR",
            records = batchRecords,
            format = BankFileFormat.SLIPS_STANDARD,
        )

        assertThat(slipsBatch.totalRecords).isEqualTo(2)
        assertThat(slipsBatch.totalAmount).isEqualByComparingTo(totalExpectedNet)
        assertThat(slipsBatch.batchHash).hasSize(64)

        val slipsLines = String(slipsBatch.content, StandardCharsets.US_ASCII).split("\r\n").filter { it.isNotEmpty() }
        assertThat(slipsLines).hasSize(4) // Header (20) + 2 Detail (23) + Trailer (29)
        assertThat(slipsLines[3].startsWith("29")).isTrue()
        assertThat(slipsLines[3]).contains("000002") // 2 records
    }
}
