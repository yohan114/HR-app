package com.hr.performance.internal

import com.hr.employee.EmployeeLookupService
import com.hr.performance.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class ContinuousFeedbackService(
    private val feedbackRepository: ContinuousFeedbackRepository,
    private val employeeLookupService: EmployeeLookupService,
) {
    private val defaultTenantId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Transactional(readOnly = true)
    fun getFeedback(
        employeeId: UUID? = null,
        type: String? = null,
        tenantId: UUID = defaultTenantId,
    ): ContinuousFeedbackListResponseDto {
        val items = if (employeeId != null) {
            feedbackRepository.findAllByTenantIdAndRecipientEmployeeIdOrderByCreatedAtDesc(tenantId, employeeId)
        } else {
            feedbackRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId)
        }

        val filtered = if (type != null) {
            items.filter { it.feedbackType.name.equals(type, ignoreCase = true) }
        } else {
            items
        }

        val dtos = filtered.map { mapToDto(it) }
        return ContinuousFeedbackListResponseDto(items = dtos)
    }

    @Transactional
    fun sendFeedback(
        senderEmployeeId: UUID,
        request: SendFeedbackRequestDto,
        tenantId: UUID = defaultTenantId,
    ): ContinuousFeedbackItemDto {
        val entity = ContinuousFeedbackEntity(
            senderEmployeeId = senderEmployeeId,
            recipientEmployeeId = request.recipientEmployeeId,
            feedbackType = request.feedbackType,
            title = request.title,
            content = request.content,
            isPrivate = request.isPrivate ?: false,
            sharedWithManager = request.sharedWithManager ?: true,
        ).apply {
            this.tenantId = tenantId
        }

        val saved = feedbackRepository.save(entity)
        return mapToDto(saved)
    }

    private fun mapToDto(entity: ContinuousFeedbackEntity): ContinuousFeedbackItemDto {
        val senderName = employeeLookupService.findDisplayName(entity.senderEmployeeId) ?: "Sender"
        val recipientName = employeeLookupService.findDisplayName(entity.recipientEmployeeId) ?: "Recipient"

        return ContinuousFeedbackItemDto(
            id = entity.id ?: UUID.randomUUID(),
            senderEmployeeId = entity.senderEmployeeId,
            senderEmployeeName = senderName,
            recipientEmployeeId = entity.recipientEmployeeId,
            recipientEmployeeName = recipientName,
            feedbackType = entity.feedbackType,
            title = entity.title,
            content = entity.content,
            isPrivate = entity.isPrivate,
            sharedWithManager = entity.sharedWithManager,
            createdAt = entity.createdAt ?: Instant.now(),
        )
    }
}
