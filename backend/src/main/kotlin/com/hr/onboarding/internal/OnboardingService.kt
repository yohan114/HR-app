package com.hr.onboarding.internal

import com.hr.employee.EmployeeLookupService
import com.hr.organisation.OrganisationLookupService
import com.hr.onboarding.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Service
@Transactional
class OnboardingService(
    private val stageRepository: OnboardingStageRepository,
    private val profileRepository: OnboardingProfileRepository,
    private val actionRepository: OnboardingActionRepository,
    private val instanceRepository: OnboardingInstanceRepository,
    private val taskRepository: OnboardingTaskRepository,
    private val employeeLookupService: EmployeeLookupService,
    private val organisationLookupService: OrganisationLookupService,
) {

    @Transactional(readOnly = true)
    fun getProfiles(): OnboardingProfileListResponse {
        val profiles = profileRepository.findAllByActiveTrue()
        val departments = organisationLookupService.findAllDepartmentNames()

        val items = profiles.map { p ->
            OnboardingProfileItem(
                id = p.id,
                code = p.code,
                name = p.name,
                departmentId = p.departmentId,
                departmentName = p.departmentId?.let { departments[it] },
                description = p.description,
                active = p.active,
            )
        }
        return OnboardingProfileListResponse(items = items, totalCount = items.size)
    }

    @Transactional(readOnly = true)
    fun getInstances(employeeId: UUID?, status: OnboardingInstanceStatus?): OnboardingInstanceListResponse {
        val instances = when {
            employeeId != null -> instanceRepository.findByEmployeeId(employeeId)?.let { listOf(it) } ?: emptyList()
            status != null -> instanceRepository.findByStatus(status)
            else -> instanceRepository.findAll()
        }

        val employees = employeeLookupService.findAll().associateBy { it.id }
        val profiles = profileRepository.findAll().associateBy { it.id }

        val items = instances.map { inst ->
            val tasks = taskRepository.findByInstanceId(inst.id)
            val emp = employees[inst.employeeId]
            val buddy = inst.buddyEmployeeId?.let { employees[it] }
            val prof = profiles[inst.profileId]

            OnboardingInstanceSummary(
                id = inst.id,
                employeeId = inst.employeeId,
                employeeName = emp?.let { "${it.firstName} ${it.lastName}" } ?: "Unknown",
                employeeCode = emp?.employeeCode,
                profileId = inst.profileId,
                profileName = prof?.name ?: "Default Onboarding Track",
                joinDate = inst.joinDate,
                status = inst.status,
                progressPct = inst.progressPct,
                buddyEmployeeId = inst.buddyEmployeeId,
                buddyName = buddy?.let { "${it.firstName} ${it.lastName}" },
                totalTasks = tasks.size,
                completedTasks = tasks.count { it.status == OnboardingTaskStatus.COMPLETED },
            )
        }

        return OnboardingInstanceListResponse(items = items, totalCount = items.size)
    }

    @Transactional(readOnly = true)
    fun getInstanceById(id: UUID): OnboardingInstanceDetail {
        val inst = instanceRepository.findById(id).orElseThrow {
            IllegalArgumentException("Onboarding instance $id not found")
        }
        val emp = employeeLookupService.findById(inst.employeeId)
        val buddy = inst.buddyEmployeeId?.let { employeeLookupService.findById(it) }
        val prof = profileRepository.findById(inst.profileId).orElse(null)
        val tasks = taskRepository.findByInstanceId(id)

        val taskItems = tasks.map { t ->
            val assignee = t.assigneeEmployeeId?.let { employeeLookupService.findById(it) }
            OnboardingTaskItem(
                id = t.id,
                instanceId = t.instanceId,
                actionId = t.actionId,
                title = t.title,
                description = t.description,
                ownerRole = t.ownerRole,
                assigneeEmployeeId = t.assigneeEmployeeId,
                assigneeName = assignee?.let { "${it.firstName} ${it.lastName}" },
                dueDate = t.dueDate,
                status = t.status,
                completedAt = t.completedAt?.atOffset(ZoneOffset.UTC),
                notes = t.notes,
                attachmentUrl = t.attachmentUrl,
            )
        }

        return OnboardingInstanceDetail(
            id = inst.id,
            candidateId = inst.candidateId,
            employeeId = inst.employeeId,
            employeeName = emp?.let { "${it.firstName} ${it.lastName}" } ?: "New Hire",
            employeeCode = emp?.employeeCode,
            profileId = inst.profileId,
            profileName = prof?.name ?: "Standard Onboarding",
            joinDate = inst.joinDate,
            status = inst.status,
            progressPct = inst.progressPct,
            buddyEmployeeId = inst.buddyEmployeeId,
            buddyName = buddy?.let { "${it.firstName} ${it.lastName}" },
            completedAt = inst.completedAt?.atOffset(ZoneOffset.UTC),
            tasks = taskItems,
        )
    }

    fun createInstance(request: OnboardingInstanceCreateRequest): OnboardingInstanceDetail {
        val emp = employeeLookupService.findById(request.employeeId)
            ?: throw IllegalArgumentException("Employee ${request.employeeId} not found")
        val prof = profileRepository.findById(request.profileId).orElseThrow {
            IllegalArgumentException("Onboarding profile ${request.profileId} not found")
        }

        val existing = instanceRepository.findByEmployeeId(request.employeeId)
        if (existing != null) {
            return getInstanceById(existing.id)
        }

        val instance = instanceRepository.save(
            OnboardingInstanceEntity(
                candidateId = request.candidateId,
                employeeId = request.employeeId,
                profileId = request.profileId,
                joinDate = request.joinDate,
                status = OnboardingInstanceStatus.IN_PROGRESS,
                progressPct = BigDecimal.ZERO,
                buddyEmployeeId = request.buddyEmployeeId,
            )
        )

        val actions = actionRepository.findByProfileId(prof.id)
        val defaultActions = if (actions.isEmpty()) {
            listOf(
                Triple("Welcome & Document Submission", OnboardingOwnerRole.NEW_HIRE, 0),
                Triple("Hardware & Laptop Provisioning", OnboardingOwnerRole.IT_OPS, -1),
                Triple("Security Access Card Issuance", OnboardingOwnerRole.HR_OPS, 0),
                Triple("Buddy Welcome & Office Walkthrough", OnboardingOwnerRole.BUDDY, 1),
                Triple("Manager Milestone & Goals Check-in", OnboardingOwnerRole.MANAGER, 30),
            )
        } else emptyList()

        if (actions.isNotEmpty()) {
            actions.forEach { act ->
                val assignee = when (act.ownerRole) {
                    OnboardingOwnerRole.NEW_HIRE -> request.employeeId
                    OnboardingOwnerRole.BUDDY -> request.buddyEmployeeId ?: request.employeeId
                    else -> null
                }
                taskRepository.save(
                    OnboardingTaskEntity(
                        instanceId = instance.id,
                        actionId = act.id,
                        title = act.name,
                        description = act.description,
                        ownerRole = act.ownerRole,
                        assigneeEmployeeId = assignee,
                        dueDate = request.joinDate.plusDays(act.dueDaysOffset.toLong()),
                        status = OnboardingTaskStatus.PENDING,
                    )
                )
            }
        } else {
            defaultActions.forEach { (title, role, offset) ->
                val assignee = when (role) {
                    OnboardingOwnerRole.NEW_HIRE -> request.employeeId
                    OnboardingOwnerRole.BUDDY -> request.buddyEmployeeId ?: request.employeeId
                    else -> null
                }
                taskRepository.save(
                    OnboardingTaskEntity(
                        instanceId = instance.id,
                        actionId = null,
                        title = title,
                        description = "Standard checklist action for $title",
                        ownerRole = role,
                        assigneeEmployeeId = assignee,
                        dueDate = request.joinDate.plusDays(offset.toLong()),
                        status = OnboardingTaskStatus.PENDING,
                    )
                )
            }
        }

        return getInstanceById(instance.id)
    }

    fun completeTask(taskId: UUID, request: OnboardingTaskCompleteRequest?, actorEmployeeId: UUID?): OnboardingTaskItem {
        val task = taskRepository.findById(taskId).orElseThrow {
            IllegalArgumentException("Onboarding task $taskId not found")
        }

        task.status = OnboardingTaskStatus.COMPLETED
        task.completedAt = Instant.now()
        if (request?.notes != null) {
            task.notes = request.notes
        }
        if (request?.attachmentUrl != null) {
            task.attachmentUrl = request.attachmentUrl
        }
        val savedTask = taskRepository.save(task)

        // Recalculate instance progress
        val allTasks = taskRepository.findByInstanceId(task.instanceId)
        val completedCount = allTasks.count { it.status == OnboardingTaskStatus.COMPLETED }
        val progress = if (allTasks.isNotEmpty()) {
            BigDecimal(completedCount).multiply(BigDecimal(100)).divide(BigDecimal(allTasks.size), 2, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        val instance = instanceRepository.findById(task.instanceId).orElse(null)
        if (instance != null) {
            instance.progressPct = progress
            if (completedCount == allTasks.size) {
                instance.status = OnboardingInstanceStatus.COMPLETED
                instance.completedAt = Instant.now()
            }
            instanceRepository.save(instance)
        }

        val assignee = savedTask.assigneeEmployeeId?.let { employeeLookupService.findById(it) }
        return OnboardingTaskItem(
            id = savedTask.id,
            instanceId = savedTask.instanceId,
            actionId = savedTask.actionId,
            title = savedTask.title,
            description = savedTask.description,
            ownerRole = savedTask.ownerRole,
            assigneeEmployeeId = savedTask.assigneeEmployeeId,
            assigneeName = assignee?.let { "${it.firstName} ${it.lastName}" },
            dueDate = savedTask.dueDate,
            status = savedTask.status,
            completedAt = savedTask.completedAt?.atOffset(ZoneOffset.UTC),
            notes = savedTask.notes,
            attachmentUrl = savedTask.attachmentUrl,
        )
    }
}
