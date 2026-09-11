package com.hr.onboarding.internal

import com.hr.employee.EmployeeLookupService
import com.hr.onboarding.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@Service
@Transactional
class OffboardingService(
    private val exitTypeRepository: ExitTypeRepository,
    private val exitReasonRepository: ExitReasonRepository,
    private val exitNoticeRepository: ExitNoticeRepository,
    private val exitInterviewRepository: ExitInterviewRepository,
    private val clearanceItemRepository: ClearanceItemRepository,
    private val clearanceTaskRepository: ClearanceTaskRepository,
    private val employeeLookupService: EmployeeLookupService,
) {

    @Transactional(readOnly = true)
    fun getExitTypes(): ExitTypeListResponse {
        val types = exitTypeRepository.findAll()
        val allReasons = exitReasonRepository.findAll().groupBy { it.exitTypeId }

        val items = types.map { t ->
            val reasons = allReasons[t.id]?.map { r ->
                ExitReasonItem(
                    id = r.id,
                    code = r.code,
                    name = r.name,
                    category = r.category,
                )
            } ?: emptyList()

            ExitTypeItem(
                id = t.id,
                code = t.code,
                name = t.name,
                voluntary = t.voluntary,
                noticeDays = t.noticeDays,
                requiresInterview = t.requiresInterview,
                requiresClearance = t.requiresClearance,
                reasons = reasons,
            )
        }
        return ExitTypeListResponse(items = items)
    }

    @Transactional(readOnly = true)
    fun getExitNotices(employeeId: UUID?, status: ExitNoticeStatus?): ExitNoticeListResponse {
        val notices = when {
            employeeId != null && status != null -> exitNoticeRepository.findByEmployeeIdAndStatus(employeeId, status)
            employeeId != null -> exitNoticeRepository.findByEmployeeId(employeeId)
            status != null -> exitNoticeRepository.findByStatus(status)
            else -> exitNoticeRepository.findAll()
        }

        val employees = employeeLookupService.findAll().associateBy { it.id }
        val types = exitTypeRepository.findAll().associateBy { it.id }
        val reasons = exitReasonRepository.findAll().associateBy { it.id }

        val items = notices.map { n ->
            val emp = employees[n.employeeId]
            val approver = n.approvedBy?.let { employees[it] }
            val type = types[n.exitTypeId]
            val reason = n.exitReasonId?.let { reasons[it] }

            ExitNoticeItem(
                id = n.id,
                noticeNumber = n.noticeNumber,
                employeeId = n.employeeId,
                employeeName = emp?.let { "${it.firstName} ${it.lastName}" } ?: "Departing Employee",
                employeeCode = emp?.employeeCode,
                exitTypeId = n.exitTypeId,
                exitTypeCode = type?.code ?: "RESIGNATION",
                exitTypeName = type?.name ?: "Voluntary Resignation",
                exitReasonId = n.exitReasonId,
                exitReasonName = reason?.name,
                noticeDate = n.noticeDate,
                requestedLastWorkingDate = n.requestedLastWorkingDate,
                approvedLastWorkingDate = n.approvedLastWorkingDate,
                remarks = n.remarks,
                status = n.status,
                approvedBy = n.approvedBy,
                approvedByName = approver?.let { "${it.firstName} ${it.lastName}" },
                approvedAt = n.approvedAt?.atOffset(ZoneOffset.UTC),
            )
        }
        return ExitNoticeListResponse(items = items, totalCount = items.size)
    }

    fun createExitNotice(employeeId: UUID, request: ExitNoticeCreateRequest): ExitNoticeItem {
        val emp = employeeLookupService.findById(employeeId)
            ?: throw IllegalArgumentException("Employee $employeeId not found")
        val type = exitTypeRepository.findById(request.exitTypeId).orElseThrow {
            IllegalArgumentException("Exit type ${request.exitTypeId} not found")
        }
        val reason = request.exitReasonId?.let {
            exitReasonRepository.findById(it).orElse(null)
        }

        val noticeDate = request.noticeDate ?: LocalDate.now()
        val noticeCount = exitNoticeRepository.count() + 1
        val noticeNumber = "EXIT-${LocalDate.now().year}-${noticeCount.toString().padStart(3, '0')}"

        val entity = exitNoticeRepository.save(
            ExitNoticeEntity(
                noticeNumber = noticeNumber,
                employeeId = employeeId,
                exitTypeId = request.exitTypeId,
                exitReasonId = request.exitReasonId,
                noticeDate = noticeDate,
                requestedLastWorkingDate = request.requestedLastWorkingDate,
                remarks = request.remarks,
                status = ExitNoticeStatus.SUBMITTED,
            )
        )

        return ExitNoticeItem(
            id = entity.id,
            noticeNumber = entity.noticeNumber,
            employeeId = entity.employeeId,
            employeeName = "${emp.firstName} ${emp.lastName}",
            employeeCode = emp.employeeCode,
            exitTypeId = entity.exitTypeId,
            exitTypeCode = type.code,
            exitTypeName = type.name,
            exitReasonId = entity.exitReasonId,
            exitReasonName = reason?.name,
            noticeDate = entity.noticeDate,
            requestedLastWorkingDate = entity.requestedLastWorkingDate,
            approvedLastWorkingDate = entity.approvedLastWorkingDate,
            remarks = entity.remarks,
            status = entity.status,
        )
    }

    fun approveExitNotice(id: UUID, request: ExitNoticeApproveRequest, actorEmployeeId: UUID): ExitNoticeItem {
        val notice = exitNoticeRepository.findById(id).orElseThrow {
            IllegalArgumentException("Exit notice $id not found")
        }
        val approver = employeeLookupService.findById(actorEmployeeId)

        notice.status = ExitNoticeStatus.APPROVED
        notice.approvedLastWorkingDate = request.approvedLastWorkingDate
        notice.approvedBy = actorEmployeeId
        notice.approvedAt = Instant.now()
        if (request.remarks != null) {
            notice.remarks = request.remarks
        }
        val saved = exitNoticeRepository.save(notice)

        // Initialize cross-departmental clearance tasks if not already created
        val existingTasks = clearanceTaskRepository.findByExitNoticeId(saved.id)
        if (existingTasks.isEmpty()) {
            val catalogItems = clearanceItemRepository.findAll()
            val defaultClearances = if (catalogItems.isEmpty()) {
                listOf(
                    ClearanceDepartment.IT_INFRASTRUCTURE to "Laptop, monitors & access tokens return",
                    ClearanceDepartment.FINANCE_PAYROLL to "Outstanding travel advances & company loans settlement",
                    ClearanceDepartment.HR_OPERATIONS to "Medical insurance card surrender & exit documentation",
                    ClearanceDepartment.ADMIN_FACILITIES to "Building RFID proximity badge & pedestal keys return",
                    ClearanceDepartment.LINE_MANAGER to "Knowledge transfer, git repos handoff & code documentation",
                )
            } else emptyList()

            if (catalogItems.isNotEmpty()) {
                catalogItems.forEach { item ->
                    clearanceTaskRepository.save(
                        ClearanceTaskEntity(
                            exitNoticeId = saved.id,
                            employeeId = saved.employeeId,
                            itemId = item.id,
                            department = item.department,
                            title = item.name,
                            assigneeEmployeeId = null,
                            status = ClearanceTaskStatus.PENDING,
                            recoverableAmount = BigDecimal.ZERO,
                        )
                    )
                }
            } else {
                defaultClearances.forEach { (dept, title) ->
                    clearanceTaskRepository.save(
                        ClearanceTaskEntity(
                            exitNoticeId = saved.id,
                            employeeId = saved.employeeId,
                            itemId = null,
                            department = dept,
                            title = title,
                            assigneeEmployeeId = null,
                            status = ClearanceTaskStatus.PENDING,
                            recoverableAmount = BigDecimal.ZERO,
                        )
                    )
                }
            }
        }

        val emp = employeeLookupService.findById(saved.employeeId)
        val type = exitTypeRepository.findById(saved.exitTypeId).orElse(null)
        val reason = saved.exitReasonId?.let { exitReasonRepository.findById(it).orElse(null) }

        return ExitNoticeItem(
            id = saved.id,
            noticeNumber = saved.noticeNumber,
            employeeId = saved.employeeId,
            employeeName = emp?.let { "${it.firstName} ${it.lastName}" } ?: "Departing Employee",
            employeeCode = emp?.employeeCode,
            exitTypeId = saved.exitTypeId,
            exitTypeCode = type?.code ?: "RESIGNATION",
            exitTypeName = type?.name ?: "Voluntary Resignation",
            exitReasonId = saved.exitReasonId,
            exitReasonName = reason?.name,
            noticeDate = saved.noticeDate,
            requestedLastWorkingDate = saved.requestedLastWorkingDate,
            approvedLastWorkingDate = saved.approvedLastWorkingDate,
            remarks = saved.remarks,
            status = saved.status,
            approvedBy = saved.approvedBy,
            approvedByName = approver?.let { "${it.firstName} ${it.lastName}" },
            approvedAt = saved.approvedAt?.atOffset(ZoneOffset.UTC),
        )
    }

    fun rejectExitNotice(id: UUID, reason: String, actorEmployeeId: UUID): ExitNoticeItem {
        val notice = exitNoticeRepository.findById(id).orElseThrow {
            IllegalArgumentException("Exit notice $id not found")
        }
        val approver = employeeLookupService.findById(actorEmployeeId)

        notice.status = ExitNoticeStatus.REJECTED
        notice.approvedBy = actorEmployeeId
        notice.approvedAt = Instant.now()
        notice.remarks = reason
        val saved = exitNoticeRepository.save(notice)

        val emp = employeeLookupService.findById(saved.employeeId)
        val type = exitTypeRepository.findById(saved.exitTypeId).orElse(null)
        val exitReason = saved.exitReasonId?.let { exitReasonRepository.findById(it).orElse(null) }

        return ExitNoticeItem(
            id = saved.id,
            noticeNumber = saved.noticeNumber,
            employeeId = saved.employeeId,
            employeeName = emp?.let { "${it.firstName} ${it.lastName}" } ?: "Departing Employee",
            employeeCode = emp?.employeeCode,
            exitTypeId = saved.exitTypeId,
            exitTypeCode = type?.code ?: "RESIGNATION",
            exitTypeName = type?.name ?: "Voluntary Resignation",
            exitReasonId = saved.exitReasonId,
            exitReasonName = exitReason?.name,
            noticeDate = saved.noticeDate,
            requestedLastWorkingDate = saved.requestedLastWorkingDate,
            approvedLastWorkingDate = saved.approvedLastWorkingDate,
            remarks = saved.remarks,
            status = saved.status,
            approvedBy = saved.approvedBy,
            approvedByName = approver?.let { "${it.firstName} ${it.lastName}" },
            approvedAt = saved.approvedAt?.atOffset(ZoneOffset.UTC),
        )
    }

    @Transactional(readOnly = true)
    fun getClearance(exitNoticeId: UUID): ClearanceDetailResponse {
        val notice = exitNoticeRepository.findById(exitNoticeId).orElseThrow {
            IllegalArgumentException("Exit notice $exitNoticeId not found")
        }
        val emp = employeeLookupService.findById(notice.employeeId)
        val tasks = clearanceTaskRepository.findByExitNoticeId(exitNoticeId)
        val employees = employeeLookupService.findAll().associateBy { it.id }

        val taskItems = tasks.map { t ->
            val assignee = t.assigneeEmployeeId?.let { employees[it] }
            ClearanceTaskItem(
                id = t.id,
                exitNoticeId = t.exitNoticeId,
                employeeId = t.employeeId,
                department = t.department,
                title = t.title,
                assigneeEmployeeId = t.assigneeEmployeeId,
                assigneeName = assignee?.let { "${it.firstName} ${it.lastName}" },
                status = t.status,
                clearedAt = t.clearedAt?.atOffset(ZoneOffset.UTC),
                remarks = t.remarks,
                recoverableAmount = t.recoverableAmount,
            )
        }

        val totalRecoverable = tasks.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.recoverableAmount) }
        val clearedCount = tasks.count { it.status == ClearanceTaskStatus.CLEARED || it.status == ClearanceTaskStatus.WAIVED }
        val pendingCount = tasks.size - clearedCount

        return ClearanceDetailResponse(
            exitNoticeId = exitNoticeId,
            employeeId = notice.employeeId,
            employeeName = emp?.let { "${it.firstName} ${it.lastName}" } ?: "Departing Employee",
            totalTasks = tasks.size,
            clearedTasks = clearedCount,
            pendingTasks = pendingCount,
            totalRecoverableAmount = totalRecoverable,
            tasks = taskItems,
        )
    }

    fun updateClearanceTaskStatus(taskId: UUID, request: ClearanceTaskStatusUpdateRequest): ClearanceTaskItem {
        val task = clearanceTaskRepository.findById(taskId).orElseThrow {
            IllegalArgumentException("Clearance task $taskId not found")
        }

        task.status = request.status
        if (request.status == ClearanceTaskStatus.CLEARED || request.status == ClearanceTaskStatus.WAIVED) {
            task.clearedAt = Instant.now()
        }
        if (request.remarks != null) {
            task.remarks = request.remarks
        }
        if (request.recoverableAmount != null) {
            task.recoverableAmount = request.recoverableAmount
        }

        val saved = clearanceTaskRepository.save(task)
        val assignee = saved.assigneeEmployeeId?.let { employeeLookupService.findById(it) }

        return ClearanceTaskItem(
            id = saved.id,
            exitNoticeId = saved.exitNoticeId,
            employeeId = saved.employeeId,
            department = saved.department,
            title = saved.title,
            assigneeEmployeeId = saved.assigneeEmployeeId,
            assigneeName = assignee?.let { "${it.firstName} ${it.lastName}" },
            status = saved.status,
            clearedAt = saved.clearedAt?.atOffset(ZoneOffset.UTC),
            remarks = saved.remarks,
            recoverableAmount = saved.recoverableAmount,
        )
    }

    @Transactional(readOnly = true)
    fun getExitInterview(exitNoticeId: UUID): ExitInterviewItem {
        val interview = exitInterviewRepository.findByExitNoticeId(exitNoticeId)
            ?: throw IllegalArgumentException("Exit interview for notice $exitNoticeId not found")

        val emp = employeeLookupService.findById(interview.employeeId)
        val interviewer = interview.interviewerEmployeeId?.let { employeeLookupService.findById(it) }

        return ExitInterviewItem(
            id = interview.id,
            exitNoticeId = interview.exitNoticeId,
            employeeId = interview.employeeId,
            employeeName = emp?.let { "${it.firstName} ${it.lastName}" } ?: "Employee",
            interviewerEmployeeId = interview.interviewerEmployeeId,
            interviewerName = interviewer?.let { "${it.firstName} ${it.lastName}" },
            conductedAt = interview.conductedAt.atOffset(ZoneOffset.UTC),
            overallExperienceRating = interview.overallExperienceRating,
            managementRating = interview.managementRating,
            cultureRating = interview.cultureRating,
            reasonDetails = interview.reasonDetails,
            suggestions = interview.suggestions,
            wouldRecommend = interview.wouldRecommend,
        )
    }

    fun submitExitInterview(exitNoticeId: UUID, request: ExitInterviewSubmitRequest, actorEmployeeId: UUID): ExitInterviewItem {
        val notice = exitNoticeRepository.findById(exitNoticeId).orElseThrow {
            IllegalArgumentException("Exit notice $exitNoticeId not found")
        }

        val existing = exitInterviewRepository.findByExitNoticeId(exitNoticeId)
        val entity = if (existing != null) {
            existing.overallExperienceRating = request.overallExperienceRating
            existing.managementRating = request.managementRating
            existing.cultureRating = request.cultureRating
            existing.reasonDetails = request.reasonDetails
            existing.suggestions = request.suggestions
            existing.wouldRecommend = request.wouldRecommend
            existing.conductedAt = Instant.now()
            exitInterviewRepository.save(existing)
        } else {
            exitInterviewRepository.save(
                ExitInterviewEntity(
                    exitNoticeId = exitNoticeId,
                    employeeId = notice.employeeId,
                    interviewerEmployeeId = actorEmployeeId,
                    conductedAt = Instant.now(),
                    overallExperienceRating = request.overallExperienceRating,
                    managementRating = request.managementRating,
                    cultureRating = request.cultureRating,
                    reasonDetails = request.reasonDetails,
                    suggestions = request.suggestions,
                    wouldRecommend = request.wouldRecommend,
                )
            )
        }

        val emp = employeeLookupService.findById(notice.employeeId)
        val interviewer = employeeLookupService.findById(actorEmployeeId)

        return ExitInterviewItem(
            id = entity.id,
            exitNoticeId = entity.exitNoticeId,
            employeeId = entity.employeeId,
            employeeName = emp?.let { "${it.firstName} ${it.lastName}" } ?: "Employee",
            interviewerEmployeeId = entity.interviewerEmployeeId,
            interviewerName = interviewer?.let { "${it.firstName} ${it.lastName}" },
            conductedAt = entity.conductedAt.atOffset(ZoneOffset.UTC),
            overallExperienceRating = entity.overallExperienceRating,
            managementRating = entity.managementRating,
            cultureRating = entity.cultureRating,
            reasonDetails = entity.reasonDetails,
            suggestions = entity.suggestions,
            wouldRecommend = entity.wouldRecommend,
        )
    }
}
