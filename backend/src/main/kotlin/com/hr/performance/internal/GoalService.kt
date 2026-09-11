package com.hr.performance.internal

import com.hr.employee.EmployeeLookupService
import com.hr.performance.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

@Service
class GoalService(
    private val goalRepository: GoalRepository,
    private val goalCheckInRepository: GoalCheckInRepository,
    private val goalCycleRepository: GoalCycleRepository,
    private val employeeLookupService: EmployeeLookupService,
) {
    private val defaultTenantId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Transactional(readOnly = true)
    fun getGoals(
        employeeId: UUID? = null,
        cycleId: UUID? = null,
        status: String? = null,
        tenantId: UUID = defaultTenantId,
    ): GoalListResponseDto {
        val allGoals = when {
            employeeId != null && cycleId != null ->
                goalRepository.findAllByTenantIdAndEmployeeIdAndCycleId(tenantId, employeeId, cycleId)
            employeeId != null ->
                goalRepository.findAllByTenantIdAndEmployeeId(tenantId, employeeId)
            cycleId != null ->
                goalRepository.findAllByTenantIdAndCycleId(tenantId, cycleId)
            else ->
                goalRepository.findAllByTenantId(tenantId)
        }

        val filteredGoals = if (status != null) {
            allGoals.filter { it.status.name.equals(status, ignoreCase = true) }
        } else {
            allGoals
        }

        val goalDtos = filteredGoals.map { mapToDto(it, tenantId) }
        val completedCount = goalDtos.count { it.status == GoalStatus.COMPLETED }

        return GoalListResponseDto(
            goals = goalDtos,
            totalCount = goalDtos.size,
            completedCount = completedCount,
        )
    }

    @Transactional
    fun createGoal(
        request: GoalCreateRequestDto,
        tenantId: UUID = defaultTenantId,
    ): GoalItemDto {
        val initialProgress = if (request.targetValue > BigDecimal.ZERO) {
            BigDecimal.ZERO
        } else {
            BigDecimal.ZERO
        }

        val goal = GoalEntity(
            employeeId = request.employeeId,
            cycleId = request.cycleId,
            parentGoalId = request.parentGoalId,
            title = request.title,
            description = request.description,
            category = request.category,
            weight = request.weight,
            targetValue = request.targetValue,
            currentValue = BigDecimal.ZERO,
            unit = request.unit,
            startDate = request.startDate,
            dueDate = request.dueDate,
            status = GoalStatus.NOT_STARTED,
            progressPercentage = initialProgress,
        ).apply {
            this.tenantId = tenantId
        }

        val saved = goalRepository.save(goal)
        return mapToDto(saved, tenantId)
    }

    @Transactional
    fun recordGoalCheckIn(
        goalId: UUID,
        request: GoalCheckInRequestDto,
        actorId: UUID,
        tenantId: UUID = defaultTenantId,
    ): GoalItemDto {
        val goal = goalRepository.findByIdAndTenantId(goalId, tenantId)
            ?: throw NoSuchElementException("Goal not found: $goalId")

        val previousValue = goal.currentValue
        val newValue = request.newValue
        goal.currentValue = newValue

        val progress = if (goal.targetValue > BigDecimal.ZERO) {
            newValue.divide(goal.targetValue, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal("100.00"))
                .setScale(2, RoundingMode.HALF_UP)
                .min(BigDecimal("100.00"))
                .max(BigDecimal.ZERO)
        } else {
            BigDecimal.ZERO
        }
        goal.progressPercentage = progress

        goal.status = when {
            progress >= BigDecimal("100.00") -> GoalStatus.COMPLETED
            progress >= BigDecimal("70.00") -> GoalStatus.ON_TRACK
            progress > BigDecimal.ZERO -> GoalStatus.IN_PROGRESS
            else -> GoalStatus.NOT_STARTED
        }

        val checkIn = GoalCheckInEntity(
            goalId = goal.id ?: goalId,
            previousValue = previousValue,
            newValue = newValue,
            progressPercentage = progress,
            note = request.note,
            checkedInBy = actorId,
        ).apply {
            this.tenantId = tenantId
        }

        goalCheckInRepository.save(checkIn)
        val updatedGoal = goalRepository.save(goal)

        return mapToDto(updatedGoal, tenantId)
    }

    private fun mapToDto(goal: GoalEntity, tenantId: UUID): GoalItemDto {
        val goalId = goal.id ?: UUID.randomUUID()
        val empName = employeeLookupService.findDisplayName(goal.employeeId) ?: "Employee ${goal.employeeId.toString().take(8)}"
        val cycle = goalCycleRepository.findByIdAndTenantId(goal.cycleId, tenantId)
        val checkIns = goalCheckInRepository.findAllByTenantIdAndGoalIdOrderByCreatedAtDesc(tenantId, goalId)

        val checkInDtos = checkIns.map { ci ->
            val actorName = employeeLookupService.findDisplayName(ci.checkedInBy) ?: "Unknown Employee"
            GoalCheckInItemDto(
                id = ci.id ?: UUID.randomUUID(),
                goalId = ci.goalId,
                previousValue = ci.previousValue,
                newValue = ci.newValue,
                progressPercentage = ci.progressPercentage,
                note = ci.note,
                checkedInBy = ci.checkedInBy,
                checkedInByName = actorName,
                createdAt = ci.createdAt ?: Instant.now(),
            )
        }

        return GoalItemDto(
            id = goalId,
            employeeId = goal.employeeId,
            employeeName = empName,
            cycleId = goal.cycleId,
            cycleName = cycle?.name ?: "Performance Cycle",
            parentGoalId = goal.parentGoalId,
            title = goal.title,
            description = goal.description,
            category = goal.category,
            weight = goal.weight,
            targetValue = goal.targetValue,
            currentValue = goal.currentValue,
            unit = goal.unit,
            startDate = goal.startDate,
            dueDate = goal.dueDate,
            status = goal.status,
            progressPercentage = goal.progressPercentage,
            checkIns = checkInDtos,
        )
    }
}
