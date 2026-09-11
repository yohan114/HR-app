package com.hr.leave.internal

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.hr.employee.EmployeeLookupService
import com.hr.leave.AccrualMethod
import com.hr.leave.AccrualResult
import com.hr.leave.LeaveAccrualService
import com.hr.leave.LedgerEntryType
import com.hr.leave.ServiceSlab
import com.hr.leave.YearRolloverResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
@Transactional
class DefaultLeaveAccrualService(
    private val employeeLookupService: EmployeeLookupService,
    private val leaveYearRepository: LeaveYearRepository,
    private val leaveTypeRepository: LeaveTypeRepository,
    private val ruleRepository: LeaveEntitlementRuleRepository,
    private val entitlementRepository: EmployeeLeaveEntitlementRepository,
    private val ledgerRepository: LeaveLedgerRepository,
    private val objectMapper: ObjectMapper,
) : LeaveAccrualService {

    override fun accrueAnnualUpfront(
        employeeId: UUID,
        leaveYearId: UUID,
        ruleId: UUID,
        asOfDate: LocalDate,
    ): AccrualResult {
        val rule = ruleRepository.findById(ruleId).orElseThrow { IllegalArgumentException("Rule not found: $ruleId") }
        val year = leaveYearRepository.findById(leaveYearId).orElseThrow { IllegalArgumentException("Year not found: $leaveYearId") }
        val employee = employeeLookupService.findById(employeeId) ?: throw IllegalArgumentException("Employee not found: $employeeId")

        val refType = "ACCRUAL_ANNUAL"
        val refId = "year:${year.code}"

        // Idempotency check
        if (ledgerRepository.existsByEmployeeIdAndLeaveYearIdAndLeaveTypeIdAndReferenceTypeAndReferenceId(
                employeeId, leaveYearId, rule.leaveTypeId, refType, refId
            )
        ) {
            val current = getOrCreateEntitlement(employee.tenantId, employeeId, leaveYearId, rule.leaveTypeId)
            return AccrualResult(
                employeeId = employeeId,
                leaveTypeId = rule.leaveTypeId,
                daysAccrued = BigDecimal.ZERO,
                previousBalance = current.balance,
                newBalance = current.balance,
                skipped = true,
                reason = "Annual upfront accrual already applied for year ${year.code}",
            )
        }

        // Employee joined after year ended?
        if (employee.joinDate.isAfter(year.endDate)) {
            val current = getOrCreateEntitlement(employee.tenantId, employeeId, leaveYearId, rule.leaveTypeId)
            return AccrualResult(
                employeeId = employeeId,
                leaveTypeId = rule.leaveTypeId,
                daysAccrued = BigDecimal.ZERO,
                previousBalance = current.balance,
                newBalance = current.balance,
                skipped = true,
                reason = "Employee join date ${employee.joinDate} is after year end ${year.endDate}",
            )
        }

        var entitlement = rule.accrualRate

        // Pro-rata on mid-year join
        if (rule.prorateOnJoin && employee.joinDate.isAfter(year.startDate)) {
            val totalDays = ChronoUnit.DAYS.between(year.startDate, year.endDate) + 1
            val activeDays = ChronoUnit.DAYS.between(employee.joinDate, year.endDate) + 1
            entitlement = rule.accrualRate
                .multiply(BigDecimal.valueOf(activeDays))
                .divide(BigDecimal.valueOf(totalDays), 4, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP)
        }

        return postCredit(
            tenantId = employee.tenantId,
            employeeId = employeeId,
            leaveYearId = leaveYearId,
            leaveTypeId = rule.leaveTypeId,
            days = entitlement,
            maxBalance = rule.maxBalance,
            refType = refType,
            refId = refId,
            effectiveDate = maxOf(year.startDate, employee.joinDate),
            remarks = "Annual upfront accrual for year ${year.code}",
        )
    }

    override fun accrueMonthly(
        employeeId: UUID,
        leaveYearId: UUID,
        ruleId: UUID,
        yearMonth: YearMonth,
    ): AccrualResult {
        val rule = ruleRepository.findById(ruleId).orElseThrow { IllegalArgumentException("Rule not found: $ruleId") }
        val year = leaveYearRepository.findById(leaveYearId).orElseThrow { IllegalArgumentException("Year not found: $leaveYearId") }
        val employee = employeeLookupService.findById(employeeId) ?: throw IllegalArgumentException("Employee not found: $employeeId")

        val refType = "ACCRUAL_MONTHLY"
        val refId = "month:$yearMonth"

        // Idempotency check
        if (ledgerRepository.existsByEmployeeIdAndLeaveYearIdAndLeaveTypeIdAndReferenceTypeAndReferenceId(
                employeeId, leaveYearId, rule.leaveTypeId, refType, refId
            )
        ) {
            val current = getOrCreateEntitlement(employee.tenantId, employeeId, leaveYearId, rule.leaveTypeId)
            return AccrualResult(
                employeeId = employeeId,
                leaveTypeId = rule.leaveTypeId,
                daysAccrued = BigDecimal.ZERO,
                previousBalance = current.balance,
                newBalance = current.balance,
                skipped = true,
                reason = "Monthly accrual already applied for $yearMonth",
            )
        }

        val monthStart = yearMonth.atDay(1)
        val monthEnd = yearMonth.atEndOfMonth()

        // Outside active tenure?
        if (employee.joinDate.isAfter(monthEnd) || (employee.resignDate != null && employee.resignDate.isBefore(monthStart))) {
            val current = getOrCreateEntitlement(employee.tenantId, employeeId, leaveYearId, rule.leaveTypeId)
            return AccrualResult(
                employeeId = employeeId,
                leaveTypeId = rule.leaveTypeId,
                daysAccrued = BigDecimal.ZERO,
                previousBalance = current.balance,
                newBalance = current.balance,
                skipped = true,
                reason = "Employee not active during $yearMonth",
            )
        }

        val effectiveStart = maxOf(monthStart, employee.joinDate)
        val effectiveEnd = if (employee.resignDate != null && employee.resignDate.isBefore(monthEnd)) employee.resignDate else monthEnd

        val totalDaysInMonth = yearMonth.lengthOfMonth().toLong()
        val activeDaysInMonth = ChronoUnit.DAYS.between(effectiveStart, effectiveEnd) + 1

        var credit = rule.accrualRate
        if (activeDaysInMonth < totalDaysInMonth && (rule.prorateOnJoin || rule.prorateOnExit)) {
            credit = rule.accrualRate
                .multiply(BigDecimal.valueOf(activeDaysInMonth))
                .divide(BigDecimal.valueOf(totalDaysInMonth), 4, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP)
        }

        return postCredit(
            tenantId = employee.tenantId,
            employeeId = employeeId,
            leaveYearId = leaveYearId,
            leaveTypeId = rule.leaveTypeId,
            days = credit,
            maxBalance = rule.maxBalance,
            refType = refType,
            refId = refId,
            effectiveDate = effectiveEnd,
            remarks = "Monthly accrual for $yearMonth ($activeDaysInMonth/$totalDaysInMonth days)",
        )
    }

    override fun accrueServiceSlab(
        employeeId: UUID,
        leaveYearId: UUID,
        ruleId: UUID,
        asOfDate: LocalDate,
    ): AccrualResult {
        val rule = ruleRepository.findById(ruleId).orElseThrow { IllegalArgumentException("Rule not found: $ruleId") }
        val year = leaveYearRepository.findById(leaveYearId).orElseThrow { IllegalArgumentException("Year not found: $leaveYearId") }
        val employee = employeeLookupService.findById(employeeId) ?: throw IllegalArgumentException("Employee not found: $employeeId")

        val completedYears = ChronoUnit.YEARS.between(employee.joinDate, asOfDate).toInt().coerceAtLeast(0)

        val refType = "ACCRUAL_SERVICE_SLAB"
        val refId = "year:${year.code}:tenure:$completedYears"

        // Idempotency check
        if (ledgerRepository.existsByEmployeeIdAndLeaveYearIdAndLeaveTypeIdAndReferenceTypeAndReferenceId(
                employeeId, leaveYearId, rule.leaveTypeId, refType, refId
            )
        ) {
            val current = getOrCreateEntitlement(employee.tenantId, employeeId, leaveYearId, rule.leaveTypeId)
            return AccrualResult(
                employeeId = employeeId,
                leaveTypeId = rule.leaveTypeId,
                daysAccrued = BigDecimal.ZERO,
                previousBalance = current.balance,
                newBalance = current.balance,
                skipped = true,
                reason = "Service slab accrual already applied for year ${year.code} at tenure $completedYears",
            )
        }

        val slabs: List<ServiceSlab> = parseServiceSlabs(rule.serviceBasedSlabs)
        val slab = slabs.find { completedYears >= it.minYears && completedYears <= it.maxYears }
            ?: slabs.lastOrNull()
            ?: ServiceSlab(0, 999, rule.accrualRate)

        val targetEntitlement = slab.entitlementDays
        val current = getOrCreateEntitlement(employee.tenantId, employeeId, leaveYearId, rule.leaveTypeId)

        // Only credit delta if entitlement grew with service tenure
        val delta = targetEntitlement.subtract(current.accrued)
        if (delta <= BigDecimal.ZERO) {
            return AccrualResult(
                employeeId = employeeId,
                leaveTypeId = rule.leaveTypeId,
                daysAccrued = BigDecimal.ZERO,
                previousBalance = current.balance,
                newBalance = current.balance,
                skipped = true,
                reason = "No accrual delta for tenure $completedYears years (already accrued ${current.accrued})",
            )
        }

        return postCredit(
            tenantId = employee.tenantId,
            employeeId = employeeId,
            leaveYearId = leaveYearId,
            leaveTypeId = rule.leaveTypeId,
            days = delta,
            maxBalance = rule.maxBalance,
            refType = refType,
            refId = refId,
            effectiveDate = asOfDate,
            remarks = "Service slab tenure credit ($completedYears completed years)",
        )
    }

    override fun accruePerWorkedDays(
        employeeId: UUID,
        leaveYearId: UUID,
        ruleId: UUID,
        yearMonth: YearMonth,
        workedDays: Int,
    ): AccrualResult {
        val rule = ruleRepository.findById(ruleId).orElseThrow { IllegalArgumentException("Rule not found: $ruleId") }
        val year = leaveYearRepository.findById(leaveYearId).orElseThrow { IllegalArgumentException("Year not found: $leaveYearId") }
        val employee = employeeLookupService.findById(employeeId) ?: throw IllegalArgumentException("Employee not found: $employeeId")

        val refType = "ACCRUAL_WORKED"
        val refId = "month:$yearMonth"

        if (ledgerRepository.existsByEmployeeIdAndLeaveYearIdAndLeaveTypeIdAndReferenceTypeAndReferenceId(
                employeeId, leaveYearId, rule.leaveTypeId, refType, refId
            )
        ) {
            val current = getOrCreateEntitlement(employee.tenantId, employeeId, leaveYearId, rule.leaveTypeId)
            return AccrualResult(
                employeeId = employeeId,
                leaveTypeId = rule.leaveTypeId,
                daysAccrued = BigDecimal.ZERO,
                previousBalance = current.balance,
                newBalance = current.balance,
                skipped = true,
                reason = "Worked day accrual already applied for $yearMonth",
            )
        }

        if (workedDays <= 0) {
            val current = getOrCreateEntitlement(employee.tenantId, employeeId, leaveYearId, rule.leaveTypeId)
            return AccrualResult(
                employeeId = employeeId,
                leaveTypeId = rule.leaveTypeId,
                daysAccrued = BigDecimal.ZERO,
                previousBalance = current.balance,
                newBalance = current.balance,
                skipped = true,
                reason = "No worked days recorded in $yearMonth",
            )
        }

        val credit = rule.accrualRate
            .multiply(BigDecimal.valueOf(workedDays.toLong()))
            .setScale(2, RoundingMode.HALF_UP)

        return postCredit(
            tenantId = employee.tenantId,
            employeeId = employeeId,
            leaveYearId = leaveYearId,
            leaveTypeId = rule.leaveTypeId,
            days = credit,
            maxBalance = rule.maxBalance,
            refType = refType,
            refId = refId,
            effectiveDate = yearMonth.atEndOfMonth(),
            remarks = "Earned leave for $workedDays worked days in $yearMonth",
        )
    }

    override fun runAccrualBatch(
        tenantId: UUID,
        leaveYearId: UUID,
        asOfDate: LocalDate,
    ): List<AccrualResult> {
        val rules = ruleRepository.findAll()
        val employees = employeeLookupService.findActiveByTenant(tenantId)
        val currentMonth = YearMonth.from(asOfDate)

        val results = mutableListOf<AccrualResult>()

        for (emp in employees) {
            for (rule in rules) {
                val res = when (rule.accrualMethod) {
                    AccrualMethod.ANNUAL_UPFRONT -> accrueAnnualUpfront(emp.id, leaveYearId, rule.id, asOfDate)
                    AccrualMethod.MONTHLY -> accrueMonthly(emp.id, leaveYearId, rule.id, currentMonth)
                    AccrualMethod.SERVICE_SLAB -> accrueServiceSlab(emp.id, leaveYearId, rule.id, asOfDate)
                    AccrualMethod.PER_WORKED_DAY,
                    AccrualMethod.EARNED -> null // Requires worked day input
                }
                if (res != null) {
                    results.add(res)
                }
            }
        }

        return results
    }

    override fun rolloverYearEnd(
        tenantId: UUID,
        fromYearId: UUID,
        toYearId: UUID,
    ): List<YearRolloverResult> {
        val fromYear = leaveYearRepository.findById(fromYearId).orElseThrow { IllegalArgumentException("From year not found: $fromYearId") }
        val toYear = leaveYearRepository.findById(toYearId).orElseThrow { IllegalArgumentException("To year not found: $toYearId") }

        val oldEntitlements = entitlementRepository.findAll().filter { it.tenantId == tenantId && it.leaveYearId == fromYearId }
        val rulesByType = ruleRepository.findAll().associateBy { it.leaveTypeId }

        val results = mutableListOf<YearRolloverResult>()

        for (oldEnt in oldEntitlements) {
            val closingBalance = oldEnt.balance
            if (closingBalance <= BigDecimal.ZERO) continue

            val rule = rulesByType[oldEnt.leaveTypeId]
            val carryForwardAllowed = rule?.carryForwardEnabled == true
            val maxCarry = rule?.carryForwardMax ?: BigDecimal.ZERO

            val carriedForwardDays = if (carryForwardAllowed) {
                closingBalance.min(maxCarry)
            } else {
                BigDecimal.ZERO
            }

            val expiredDays = closingBalance.subtract(carriedForwardDays)

            // 1. In old year: expire unused balance if any
            if (expiredDays > BigDecimal.ZERO) {
                val newClosing = closingBalance.subtract(expiredDays)
                oldEnt.expired = oldEnt.expired.add(expiredDays)
                oldEnt.balance = newClosing
                oldEnt.updatedAt = Instant.now()
                entitlementRepository.save(oldEnt)

                val expiryEntry = LeaveLedgerEntry(
                    employeeId = oldEnt.employeeId,
                    leaveYearId = fromYearId,
                    leaveTypeId = oldEnt.leaveTypeId,
                    entryType = LedgerEntryType.EXPIRY,
                    days = expiredDays.negate(),
                    referenceType = "YEAR_END_EXPIRY",
                    referenceId = "from:${fromYear.code}:to:${toYear.code}",
                    effectiveDate = fromYear.endDate,
                    balanceAfter = newClosing,
                    remarks = "Expired year-end balance exceeding carry-forward cap",
                ).also { it.tenantId = tenantId }
                ledgerRepository.save(expiryEntry)
            }

            // 2. In new year: credit carried-forward balance if any
            if (carriedForwardDays > BigDecimal.ZERO) {
                val newEnt = getOrCreateEntitlement(tenantId, oldEnt.employeeId, toYearId, oldEnt.leaveTypeId)
                val newBalance = newEnt.balance.add(carriedForwardDays)
                newEnt.openingBalance = newEnt.openingBalance.add(carriedForwardDays)
                newEnt.carriedForward = newEnt.carriedForward.add(carriedForwardDays)
                newEnt.balance = newBalance
                newEnt.updatedAt = Instant.now()
                entitlementRepository.save(newEnt)

                val carryEntry = LeaveLedgerEntry(
                    employeeId = oldEnt.employeeId,
                    leaveYearId = toYearId,
                    leaveTypeId = oldEnt.leaveTypeId,
                    entryType = LedgerEntryType.CARRY_FORWARD,
                    days = carriedForwardDays,
                    referenceType = "YEAR_END_CARRY_FORWARD",
                    referenceId = "rollover:from:${fromYear.code}",
                    effectiveDate = toYear.startDate,
                    balanceAfter = newBalance,
                    remarks = "Carried forward from year ${fromYear.code}",
                ).also { it.tenantId = tenantId }
                ledgerRepository.save(carryEntry)
            }

            results.add(
                YearRolloverResult(
                    employeeId = oldEnt.employeeId,
                    leaveTypeId = oldEnt.leaveTypeId,
                    closingBalancePreviousYear = closingBalance,
                    carriedForward = carriedForwardDays,
                    expired = expiredDays,
                )
            )
        }

        return results
    }

    private fun postCredit(
        tenantId: UUID,
        employeeId: UUID,
        leaveYearId: UUID,
        leaveTypeId: UUID,
        days: BigDecimal,
        maxBalance: BigDecimal,
        refType: String,
        refId: String,
        effectiveDate: LocalDate,
        remarks: String,
    ): AccrualResult {
        val entitlement = getOrCreateEntitlement(tenantId, employeeId, leaveYearId, leaveTypeId)
        val prevBalance = entitlement.balance

        // Cap credit if it exceeds maxBalance
        val allowedRoom = maxBalance.subtract(prevBalance).max(BigDecimal.ZERO)
        val creditDays = days.min(allowedRoom)

        val newBalance = prevBalance.add(creditDays)

        // Update aggregate projection
        entitlement.accrued = entitlement.accrued.add(creditDays)
        entitlement.balance = newBalance
        entitlement.lastAccruedAt = Instant.now()
        entitlement.updatedAt = Instant.now()
        entitlementRepository.save(entitlement)

        // Write append-only ledger transaction
        val ledgerEntry = LeaveLedgerEntry(
            employeeId = employeeId,
            leaveYearId = leaveYearId,
            leaveTypeId = leaveTypeId,
            entryType = LedgerEntryType.ACCRUAL,
            days = creditDays,
            referenceType = refType,
            referenceId = refId,
            effectiveDate = effectiveDate,
            balanceAfter = newBalance,
            remarks = if (creditDays < days) "$remarks (capped by max balance $maxBalance)" else remarks,
        ).also { it.tenantId = tenantId }

        ledgerRepository.save(ledgerEntry)

        return AccrualResult(
            employeeId = employeeId,
            leaveTypeId = leaveTypeId,
            daysAccrued = creditDays,
            previousBalance = prevBalance,
            newBalance = newBalance,
        )
    }

    private fun getOrCreateEntitlement(
        tenantId: UUID,
        employeeId: UUID,
        leaveYearId: UUID,
        leaveTypeId: UUID,
    ): EmployeeLeaveEntitlement {
        return entitlementRepository.findByEmployeeIdAndLeaveYearIdAndLeaveTypeId(
            employeeId, leaveYearId, leaveTypeId
        ) ?: run {
            val fresh = EmployeeLeaveEntitlement(
                tenantId = tenantId,
                employeeId = employeeId,
                leaveYearId = leaveYearId,
                leaveTypeId = leaveTypeId,
            )
            entitlementRepository.save(fresh) ?: fresh
        }
    }

    private fun parseServiceSlabs(json: String): List<ServiceSlab> {
        return try {
            if (json.isBlank()) emptyList() else objectMapper.readValue(json)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
