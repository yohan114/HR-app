package com.hr.leave

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hr.employee.EmployeeLeaveProfile
import com.hr.employee.EmployeeLookupService
import com.hr.leave.internal.DefaultLeaveAccrualService
import com.hr.leave.internal.DefaultLeaveBalanceService
import com.hr.leave.internal.EmployeeLeaveEntitlement
import com.hr.leave.internal.EmployeeLeaveEntitlementRepository
import com.hr.leave.internal.LeaveEntitlementRule
import com.hr.leave.internal.LeaveEntitlementRuleRepository
import com.hr.leave.internal.LeaveLedgerEntry
import com.hr.leave.internal.LeaveLedgerRepository
import com.hr.leave.internal.LeaveType
import com.hr.leave.internal.LeaveTypeRepository
import com.hr.leave.internal.LeaveYear
import com.hr.leave.internal.LeaveYearRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.Optional
import java.util.UUID

@DisplayName("Leave balance accrual engine (P2-BE-24 / P2-BE-25)")
class LeaveAccrualEngineTest {

    private val employeeLookupService = mock(EmployeeLookupService::class.java)
    private val leaveYearRepository = mock(LeaveYearRepository::class.java)
    private val leaveTypeRepository = mock(LeaveTypeRepository::class.java)
    private val ruleRepository = mock(LeaveEntitlementRuleRepository::class.java)
    private val entitlementRepository = mock(EmployeeLeaveEntitlementRepository::class.java)
    private val ledgerRepository = mock(LeaveLedgerRepository::class.java)
    private val objectMapper = jacksonObjectMapper()

    private lateinit var accrualService: DefaultLeaveAccrualService
    private lateinit var balanceService: DefaultLeaveBalanceService

    private val tenantId = UUID.randomUUID()
    private val employeeId = UUID.randomUUID()

    private val leaveYear = LeaveYear(
        code = "2026",
        name = "Calendar Year 2026",
        startDate = LocalDate.of(2026, 1, 1),
        endDate = LocalDate.of(2026, 12, 31),
    ).apply {
        tenantId = this@LeaveAccrualEngineTest.tenantId
    }
    private val leaveYearId = leaveYear.id

    private val leaveType = LeaveType(
        code = "ANNUAL",
        name = "Annual Leave",
    ).apply {
        tenantId = this@LeaveAccrualEngineTest.tenantId
    }
    private val leaveTypeId = leaveType.id

    @BeforeEach
    fun setUp() {
        accrualService = DefaultLeaveAccrualService(
            employeeLookupService,
            leaveYearRepository,
            leaveTypeRepository,
            ruleRepository,
            entitlementRepository,
            ledgerRepository,
            objectMapper,
        )

        balanceService = DefaultLeaveBalanceService(
            entitlementRepository,
            ledgerRepository,
            leaveTypeRepository,
        )

        `when`(leaveYearRepository.findById(leaveYearId)).thenReturn(Optional.of(leaveYear))
        `when`(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType))
        `when`(entitlementRepository.save(org.mockito.ArgumentMatchers.any(EmployeeLeaveEntitlement::class.java))).thenAnswer { it.arguments[0] }
        `when`(ledgerRepository.save(org.mockito.ArgumentMatchers.any(LeaveLedgerEntry::class.java))).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `accrueAnnualUpfront gives full entitlement to existing employee`() {
        val employee = EmployeeLeaveProfile(
            id = employeeId,
            tenantId = tenantId,
            employeeCode = "EMP001",
            displayName = "Alice Smith",
            joinDate = LocalDate.of(2024, 5, 10), // Joined 2 years ago
            resignDate = null,
            status = "ACTIVE",
        )
        val rule = LeaveEntitlementRule(
            leaveTypeId = leaveTypeId,
            name = "Standard Annual Upfront",
            accrualMethod = AccrualMethod.ANNUAL_UPFRONT,
        ).apply {
            tenantId = this@LeaveAccrualEngineTest.tenantId
            accrualRate = BigDecimal("14.00")
            maxBalance = BigDecimal("30.00")
            prorateOnJoin = true
        }
        val ruleId = rule.id

        `when`(employeeLookupService.findById(employeeId)).thenReturn(employee)
        `when`(ruleRepository.findById(ruleId)).thenReturn(Optional.of(rule))
        `when`(
            ledgerRepository.existsByEmployeeIdAndLeaveYearIdAndLeaveTypeIdAndReferenceTypeAndReferenceId(
                employeeId, leaveYearId, leaveTypeId, "ACCRUAL_ANNUAL", "year:2026"
            )
        ).thenReturn(false)

        val result = accrualService.accrueAnnualUpfront(employeeId, leaveYearId, ruleId)

        assertThat(result.skipped).isFalse()
        assertThat(result.daysAccrued).isEqualByComparingTo("14.00")
        assertThat(result.newBalance).isEqualByComparingTo("14.00")

        val ledgerCaptor = ArgumentCaptor.forClass(LeaveLedgerEntry::class.java)
        verify(ledgerRepository).save(ledgerCaptor.capture())
        val savedEntry = ledgerCaptor.value
        assertThat(savedEntry.days).isEqualByComparingTo("14.00")
        assertThat(savedEntry.entryType).isEqualTo(LedgerEntryType.ACCRUAL)
        assertThat(savedEntry.referenceType).isEqualTo("ACCRUAL_ANNUAL")
        assertThat(savedEntry.referenceId).isEqualTo("year:2026")
    }

    @Test
    fun `accrueAnnualUpfront pro-rates entitlement for mid-year joiner`() {
        val midYearJoinDate = LocalDate.of(2026, 7, 1) // 184 days remaining out of 365
        val employee = EmployeeLeaveProfile(
            id = employeeId,
            tenantId = tenantId,
            employeeCode = "EMP002",
            displayName = "Bob Jones",
            joinDate = midYearJoinDate,
            resignDate = null,
            status = "ACTIVE",
        )
        val rule = LeaveEntitlementRule(
            leaveTypeId = leaveTypeId,
            name = "Standard Annual Upfront",
            accrualMethod = AccrualMethod.ANNUAL_UPFRONT,
        ).apply {
            tenantId = this@LeaveAccrualEngineTest.tenantId
            accrualRate = BigDecimal("14.00")
            maxBalance = BigDecimal("30.00")
            prorateOnJoin = true
        }
        val ruleId = rule.id

        `when`(employeeLookupService.findById(employeeId)).thenReturn(employee)
        `when`(ruleRepository.findById(ruleId)).thenReturn(Optional.of(rule))

        val result = accrualService.accrueAnnualUpfront(employeeId, leaveYearId, ruleId)

        assertThat(result.skipped).isFalse()
        // 14.00 * 184 / 365 = 7.0575... -> 7.06 days
        assertThat(result.daysAccrued).isEqualByComparingTo("7.06")
        assertThat(result.newBalance).isEqualByComparingTo("7.06")
    }

    @Test
    fun `accrueAnnualUpfront is idempotent and does not double credit on second run`() {
        val employee = EmployeeLeaveProfile(
            id = employeeId,
            tenantId = tenantId,
            employeeCode = "EMP001",
            displayName = "Alice Smith",
            joinDate = LocalDate.of(2024, 1, 1),
            resignDate = null,
            status = "ACTIVE",
        )
        val rule = LeaveEntitlementRule(
            leaveTypeId = leaveTypeId,
            name = "Standard Annual Upfront",
            accrualMethod = AccrualMethod.ANNUAL_UPFRONT,
        ).apply {
            tenantId = this@LeaveAccrualEngineTest.tenantId
            accrualRate = BigDecimal("14.00")
        }
        val ruleId = rule.id

        `when`(employeeLookupService.findById(employeeId)).thenReturn(employee)
        `when`(ruleRepository.findById(ruleId)).thenReturn(Optional.of(rule))

        // First run already recorded in ledger
        `when`(
            ledgerRepository.existsByEmployeeIdAndLeaveYearIdAndLeaveTypeIdAndReferenceTypeAndReferenceId(
                employeeId, leaveYearId, leaveTypeId, "ACCRUAL_ANNUAL", "year:2026"
            )
        ).thenReturn(true)

        val existingEnt = EmployeeLeaveEntitlement(
            tenantId = tenantId,
            employeeId = employeeId,
            leaveYearId = leaveYearId,
            leaveTypeId = leaveTypeId,
        ).apply {
            accrued = BigDecimal("14.00")
            balance = BigDecimal("14.00")
        }
        `when`(entitlementRepository.findByEmployeeIdAndLeaveYearIdAndLeaveTypeId(employeeId, leaveYearId, leaveTypeId))
            .thenReturn(existingEnt)

        val result = accrualService.accrueAnnualUpfront(employeeId, leaveYearId, ruleId)

        assertThat(result.skipped).isTrue()
        assertThat(result.daysAccrued).isEqualByComparingTo("0.00")
        assertThat(result.newBalance).isEqualByComparingTo("14.00")
        assertThat(result.reason).contains("already applied")
    }

    @Test
    fun `accrueMonthly credits fixed rate and pro-rates for partial month`() {
        val employee = EmployeeLeaveProfile(
            id = employeeId,
            tenantId = tenantId,
            employeeCode = "EMP003",
            displayName = "Charlie Brown",
            joinDate = LocalDate.of(2026, 3, 16), // Joined March 16 (16 days active out of 31)
            resignDate = null,
            status = "ACTIVE",
        )
        val rule = LeaveEntitlementRule(
            leaveTypeId = leaveTypeId,
            name = "Monthly Accrual",
            accrualMethod = AccrualMethod.MONTHLY,
        ).apply {
            tenantId = this@LeaveAccrualEngineTest.tenantId
            accrualRate = BigDecimal("1.25")
            maxBalance = BigDecimal("30.00")
            prorateOnJoin = true
        }
        val ruleId = rule.id

        `when`(employeeLookupService.findById(employeeId)).thenReturn(employee)
        `when`(ruleRepository.findById(ruleId)).thenReturn(Optional.of(rule))

        val result = accrualService.accrueMonthly(employeeId, leaveYearId, ruleId, YearMonth.of(2026, 3))

        assertThat(result.skipped).isFalse()
        // 1.25 * 16 / 31 = 0.645... -> 0.65 days
        assertThat(result.daysAccrued).isEqualByComparingTo("0.65")
    }

    @Test
    fun `accrueServiceSlab evaluates completed service tenure brackets`() {
        val slabsJson = """
            [
              { "minYears": 0, "maxYears": 2, "entitlementDays": 14.0 },
              { "minYears": 3, "maxYears": 5, "entitlementDays": 18.0 },
              { "minYears": 6, "maxYears": 99, "entitlementDays": 21.0 }
            ]
        """.trimIndent()

        val rule = LeaveEntitlementRule(
            leaveTypeId = leaveTypeId,
            name = "Tenure Tiered Annual",
            accrualMethod = AccrualMethod.SERVICE_SLAB,
        ).apply {
            tenantId = this@LeaveAccrualEngineTest.tenantId
            serviceBasedSlabs = slabsJson
            accrualRate = BigDecimal("14.00")
            maxBalance = BigDecimal("40.00")
        }
        val ruleId = rule.id

        // Employee joined 4 years ago (falls in 3-5 years bracket -> 18.0 days)
        val employee = EmployeeLeaveProfile(
            id = employeeId,
            tenantId = tenantId,
            employeeCode = "EMP004",
            displayName = "David Miller",
            joinDate = LocalDate.of(2022, 1, 1),
            resignDate = null,
            status = "ACTIVE",
        )

        `when`(employeeLookupService.findById(employeeId)).thenReturn(employee)
        `when`(ruleRepository.findById(ruleId)).thenReturn(Optional.of(rule))

        val asOfDate = LocalDate.of(2026, 3, 1)
        val result = accrualService.accrueServiceSlab(employeeId, leaveYearId, ruleId, asOfDate)

        assertThat(result.skipped).isFalse()
        assertThat(result.daysAccrued).isEqualByComparingTo("18.00")
        assertThat(result.newBalance).isEqualByComparingTo("18.00")
    }

    @Test
    fun `accruePerWorkedDays calculates leave earned by attendance`() {
        val employee = EmployeeLeaveProfile(
            id = employeeId,
            tenantId = tenantId,
            employeeCode = "EMP005",
            displayName = "Emma Watson",
            joinDate = LocalDate.of(2025, 1, 1),
            resignDate = null,
            status = "ACTIVE",
        )
        val rule = LeaveEntitlementRule(
            leaveTypeId = leaveTypeId,
            name = "Daily Attendance Earned",
            accrualMethod = AccrualMethod.PER_WORKED_DAY,
        ).apply {
            tenantId = this@LeaveAccrualEngineTest.tenantId
            accrualRate = BigDecimal("0.05") // 1 day per 20 worked days
            maxBalance = BigDecimal("20.00")
        }
        val ruleId = rule.id

        `when`(employeeLookupService.findById(employeeId)).thenReturn(employee)
        `when`(ruleRepository.findById(ruleId)).thenReturn(Optional.of(rule))

        val result = accrualService.accruePerWorkedDays(
            employeeId = employeeId,
            leaveYearId = leaveYearId,
            ruleId = ruleId,
            yearMonth = YearMonth.of(2026, 3),
            workedDays = 22,
        )

        assertThat(result.skipped).isFalse()
        // 0.05 * 22 = 1.10 days
        assertThat(result.daysAccrued).isEqualByComparingTo("1.10")
    }

    @Test
    fun `accrual respects maxBalance ceiling and caps credit`() {
        val employee = EmployeeLeaveProfile(
            id = employeeId,
            tenantId = tenantId,
            employeeCode = "EMP006",
            displayName = "Fiona Gallagher",
            joinDate = LocalDate.of(2024, 1, 1),
            resignDate = null,
            status = "ACTIVE",
        )
        val rule = LeaveEntitlementRule(
            leaveTypeId = leaveTypeId,
            name = "Capped Annual",
            accrualMethod = AccrualMethod.ANNUAL_UPFRONT,
        ).apply {
            tenantId = this@LeaveAccrualEngineTest.tenantId
            accrualRate = BigDecimal("10.00")
            maxBalance = BigDecimal("15.00") // Ceiling is 15
        }
        val ruleId = rule.id

        val existingEnt = EmployeeLeaveEntitlement(
            tenantId = tenantId,
            employeeId = employeeId,
            leaveYearId = leaveYearId,
            leaveTypeId = leaveTypeId,
        ).apply {
            balance = BigDecimal("12.00") // 12 + 10 = 22 > 15
        }

        `when`(employeeLookupService.findById(employeeId)).thenReturn(employee)
        `when`(ruleRepository.findById(ruleId)).thenReturn(Optional.of(rule))
        `when`(entitlementRepository.findByEmployeeIdAndLeaveYearIdAndLeaveTypeId(employeeId, leaveYearId, leaveTypeId))
            .thenReturn(existingEnt)

        val result = accrualService.accrueAnnualUpfront(employeeId, leaveYearId, ruleId)

        assertThat(result.skipped).isFalse()
        // Room allowed: 15.00 - 12.00 = 3.00 days
        assertThat(result.daysAccrued).isEqualByComparingTo("3.00")
        assertThat(result.newBalance).isEqualByComparingTo("15.00")
    }

    @Test
    fun `reconstructBalanceFromLedger exactly matches balance explainability guarantee`() {
        val asOfDate = LocalDate.of(2026, 6, 1)

        val entries = listOf(
            LeaveLedgerEntry(
                employeeId = employeeId,
                leaveYearId = leaveYearId,
                leaveTypeId = leaveTypeId,
                entryType = LedgerEntryType.OPENING,
                days = BigDecimal("5.00"),
                referenceType = "OPENING",
                referenceId = "init",
                effectiveDate = LocalDate.of(2026, 1, 1),
                balanceAfter = BigDecimal("5.00"),
            ),
            LeaveLedgerEntry(
                employeeId = employeeId,
                leaveYearId = leaveYearId,
                leaveTypeId = leaveTypeId,
                entryType = LedgerEntryType.ACCRUAL,
                days = BigDecimal("1.25"),
                referenceType = "ACCRUAL_MONTHLY",
                referenceId = "month:2026-01",
                effectiveDate = LocalDate.of(2026, 1, 31),
                balanceAfter = BigDecimal("6.25"),
            ),
            LeaveLedgerEntry(
                employeeId = employeeId,
                leaveYearId = leaveYearId,
                leaveTypeId = leaveTypeId,
                entryType = LedgerEntryType.ACCRUAL,
                days = BigDecimal("1.25"),
                referenceType = "ACCRUAL_MONTHLY",
                referenceId = "month:2026-02",
                effectiveDate = LocalDate.of(2026, 2, 28),
                balanceAfter = BigDecimal("7.50"),
            ),
            LeaveLedgerEntry(
                employeeId = employeeId,
                leaveYearId = leaveYearId,
                leaveTypeId = leaveTypeId,
                entryType = LedgerEntryType.TAKEN,
                days = BigDecimal("2.00"),
                referenceType = "LEAVE_APPLICATION",
                referenceId = "app-001",
                effectiveDate = LocalDate.of(2026, 3, 10),
                balanceAfter = BigDecimal("5.50"),
            ),
            LeaveLedgerEntry(
                employeeId = employeeId,
                leaveYearId = leaveYearId,
                leaveTypeId = leaveTypeId,
                entryType = LedgerEntryType.ADJUSTMENT,
                days = BigDecimal("1.00"), // Credit adjustment
                referenceType = "HR_CORRECTION",
                referenceId = "adj-001",
                effectiveDate = LocalDate.of(2026, 4, 1),
                balanceAfter = BigDecimal("6.50"),
            ),
        )

        `when`(
            ledgerRepository.findByEmployeeIdAndLeaveYearIdAndLeaveTypeIdAndEffectiveDateLessThanEqualOrderByEffectiveDateAscCreatedAtAsc(
                employeeId, leaveYearId, leaveTypeId, asOfDate
            )
        ).thenReturn(entries)

        val reconstructed = balanceService.reconstructBalanceFromLedger(employeeId, leaveYearId, leaveTypeId, asOfDate)

        // 5.00 + 1.25 + 1.25 - 2.00 + 1.00 = 6.50
        assertThat(reconstructed).isEqualByComparingTo("6.50")
    }

    @Test
    fun `rolloverYearEnd carries forward up to cap and expires excess`() {
        val nextYear = LeaveYear(
            code = "2027",
            name = "Calendar Year 2027",
            startDate = LocalDate.of(2027, 1, 1),
            endDate = LocalDate.of(2027, 12, 31),
        ).apply {
            tenantId = this@LeaveAccrualEngineTest.tenantId
        }
        val nextYearId = nextYear.id

        `when`(leaveYearRepository.findById(nextYearId)).thenReturn(Optional.of(nextYear))

        val rule = LeaveEntitlementRule(
            leaveTypeId = leaveTypeId,
            name = "Annual with Carry Forward Cap",
            accrualMethod = AccrualMethod.ANNUAL_UPFRONT,
        ).apply {
            tenantId = this@LeaveAccrualEngineTest.tenantId
            carryForwardEnabled = true
            carryForwardMax = BigDecimal("5.00") // Max 5 can be carried forward
        }
        val ruleId = rule.id

        `when`(ruleRepository.findAll()).thenReturn(listOf(rule))

        // Existing closing balance in 2026 is 8.00 days (exceeds cap of 5.00 by 3.00)
        val ent2026 = EmployeeLeaveEntitlement(
            tenantId = tenantId,
            employeeId = employeeId,
            leaveYearId = leaveYearId,
            leaveTypeId = leaveTypeId,
        ).apply {
            balance = BigDecimal("8.00")
        }

        `when`(entitlementRepository.findAll()).thenReturn(listOf(ent2026))

        val rolloverResults = accrualService.rolloverYearEnd(tenantId, leaveYearId, nextYearId)

        assertThat(rolloverResults).hasSize(1)
        val r = rolloverResults.first()
        assertThat(r.closingBalancePreviousYear).isEqualByComparingTo("8.00")
        assertThat(r.carriedForward).isEqualByComparingTo("5.00")
        assertThat(r.expired).isEqualByComparingTo("3.00")

        // 2026 entitlement expired 3.00, balance reduced to 5.00
        assertThat(ent2026.expired).isEqualByComparingTo("3.00")
        assertThat(ent2026.balance).isEqualByComparingTo("5.00")
    }
}
