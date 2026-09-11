package com.hr.leave.internal

import com.hr.leave.LeaveBalanceDto
import com.hr.leave.LeaveBalanceService
import com.hr.leave.LedgerEntryDto
import com.hr.leave.LedgerEntryType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Service
@Transactional(readOnly = true)
class DefaultLeaveBalanceService(
    private val entitlementRepository: EmployeeLeaveEntitlementRepository,
    private val ledgerRepository: LeaveLedgerRepository,
    private val leaveTypeRepository: LeaveTypeRepository,
) : LeaveBalanceService {

    override fun getBalance(
        employeeId: UUID,
        leaveYearId: UUID,
        leaveTypeId: UUID,
    ): LeaveBalanceDto? {
        val ent =
            entitlementRepository.findByEmployeeIdAndLeaveYearIdAndLeaveTypeId(
                employeeId,
                leaveYearId,
                leaveTypeId,
            ) ?: return null

        val leaveType = leaveTypeRepository.findById(leaveTypeId).orElse(null)

        return LeaveBalanceDto(
            employeeId = ent.employeeId,
            leaveYearId = ent.leaveYearId,
            leaveTypeId = ent.leaveTypeId,
            leaveTypeCode = leaveType?.code ?: "UNKNOWN",
            leaveTypeName = leaveType?.name ?: "Unknown",
            openingBalance = ent.openingBalance,
            accrued = ent.accrued,
            taken = ent.taken,
            adjusted = ent.adjusted,
            carriedForward = ent.carriedForward,
            encashed = ent.encashed,
            expired = ent.expired,
            balance = ent.balance,
            lastAccruedAt = ent.lastAccruedAt,
        )
    }

    override fun getAllBalancesForEmployee(
        employeeId: UUID,
        leaveYearId: UUID,
    ): List<LeaveBalanceDto> {
        val entitlements = entitlementRepository.findByEmployeeIdAndLeaveYearId(employeeId, leaveYearId)
        val types = leaveTypeRepository.findAll().associateBy { it.id }

        return entitlements.map { ent ->
            val type = types[ent.leaveTypeId]
            LeaveBalanceDto(
                employeeId = ent.employeeId,
                leaveYearId = ent.leaveYearId,
                leaveTypeId = ent.leaveTypeId,
                leaveTypeCode = type?.code ?: "UNKNOWN",
                leaveTypeName = type?.name ?: "Unknown",
                openingBalance = ent.openingBalance,
                accrued = ent.accrued,
                taken = ent.taken,
                adjusted = ent.adjusted,
                carriedForward = ent.carriedForward,
                encashed = ent.encashed,
                expired = ent.expired,
                balance = ent.balance,
                lastAccruedAt = ent.lastAccruedAt,
            )
        }
    }

    override fun getLedgerStatement(
        employeeId: UUID,
        leaveYearId: UUID,
        leaveTypeId: UUID,
    ): List<LedgerEntryDto> {
        val entries =
            ledgerRepository.findByEmployeeIdAndLeaveYearIdAndLeaveTypeIdOrderByEffectiveDateAscCreatedAtAsc(
                employeeId,
                leaveYearId,
                leaveTypeId,
            )

        return entries.map { e ->
            LedgerEntryDto(
                id = e.id,
                employeeId = e.employeeId,
                leaveYearId = e.leaveYearId,
                leaveTypeId = e.leaveTypeId,
                entryType = e.entryType,
                days = e.days,
                referenceType = e.referenceType,
                referenceId = e.referenceId,
                effectiveDate = e.effectiveDate,
                balanceAfter = e.balanceAfter,
                remarks = e.remarks,
                createdAt = e.createdAt ?: java.time.Instant.EPOCH,
            )
        }
    }

    override fun reconstructBalanceFromLedger(
        employeeId: UUID,
        leaveYearId: UUID,
        leaveTypeId: UUID,
        asOfDate: LocalDate,
    ): BigDecimal {
        val entries =
            ledgerRepository.findByEmployeeIdAndLeaveYearIdAndLeaveTypeIdAndEffectiveDateLessThanEqualOrderByEffectiveDateAscCreatedAtAsc(
                employeeId,
                leaveYearId,
                leaveTypeId,
                asOfDate,
            )

        var running = BigDecimal.ZERO
        for (e in entries) {
            running =
                when (e.entryType) {
                    LedgerEntryType.OPENING,
                    LedgerEntryType.ACCRUAL,
                    LedgerEntryType.CARRY_FORWARD,
                    LedgerEntryType.CANCELLED -> running.add(e.days.abs())

                    LedgerEntryType.TAKEN,
                    LedgerEntryType.ENCASHMENT,
                    LedgerEntryType.EXPIRY -> running.subtract(e.days.abs())

                    LedgerEntryType.ADJUSTMENT -> running.add(e.days)
                }
        }
        return running
    }
}
