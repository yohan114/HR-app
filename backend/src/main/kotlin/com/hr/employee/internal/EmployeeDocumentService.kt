package com.hr.employee.internal

import com.hr.identity.Caller
import com.hr.shared.api.NotFoundException
import com.hr.tenancy.TenantContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class EmployeeDocumentService(
    private val documentRepository: EmployeeDocumentRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun getOwnDocuments(caller: Caller, asOfDate: LocalDate = LocalDate.now()): List<EmployeeDocumentResponseDto> {
        val employeeId = caller.employeeId
            ?: throw NotFoundException("NO_EMPLOYEE_RECORD", "User account is not linked to an employee record")
        val tenantId = TenantContext.currentId()

        val documents = documentRepository.findAllByTenantIdAndEmployeeId(tenantId, employeeId)
            .filter { it.status != DocumentStatus.REPLACED }
            .sortedBy { it.expiryDate ?: LocalDate.MAX }

        return documents.map { doc ->
            toDto(doc, asOfDate)
        }
    }

    @Transactional
    fun renewDocument(
        caller: Caller,
        request: RenewDocumentCommand,
        asOfDate: LocalDate = LocalDate.now(),
    ): RenewDocumentResult {
        val employeeId = caller.employeeId
            ?: throw NotFoundException("NO_EMPLOYEE_RECORD", "User account is not linked to an employee record")
        val tenantId = TenantContext.currentId()

        // If replacing an existing document, archive it as REPLACED
        request.previousDocumentId?.let { prevId ->
            val prevDoc = documentRepository.findById(prevId).orElse(null)
            if (prevDoc != null && prevDoc.tenantId == tenantId && prevDoc.employeeId == employeeId) {
                prevDoc.status = DocumentStatus.REPLACED
                documentRepository.save(prevDoc)
                log.info("Archived previous document {} as REPLACED for employee {}", prevId, employeeId)
            }
        }

        val docType = runCatching { DocumentType.valueOf(request.docType.uppercase()) }
            .getOrDefault(DocumentType.OTHER)

        val newDoc = EmployeeDocument(
            employeeId = employeeId,
            docType = docType,
            docNumberEnc = request.docNumber,
            issueDate = request.issueDate,
            expiryDate = request.expiryDate,
            issuingCountry = request.issuingCountry?.take(2)?.uppercase(),
            attachmentKey = request.attachmentKey,
            status = if (request.expiryDate.isBefore(asOfDate)) DocumentStatus.EXPIRED else DocumentStatus.VALID,
        )
        val saved = documentRepository.save(newDoc)

        log.info(
            "Created/renewed document {} of type {} for employee {}",
            saved.id,
            saved.docType,
            employeeId,
        )

        return RenewDocumentResult(
            document = toDto(saved, asOfDate),
            message = "${saved.docType.name.replace('_', ' ')} renewed successfully.",
        )
    }

    private fun toDto(doc: EmployeeDocument, asOfDate: LocalDate): EmployeeDocumentResponseDto {
        val days = doc.expiryDate?.let { ChronoUnit.DAYS.between(asOfDate, it).toInt() }
        val dynamicStatus = when {
            doc.status == DocumentStatus.REPLACED -> "REPLACED"
            doc.expiryDate != null && doc.expiryDate!!.isBefore(asOfDate) -> "EXPIRED"
            days != null && days <= doc.alertDaysBefore -> "EXPIRING"
            else -> doc.status.name
        }

        return EmployeeDocumentResponseDto(
            id = doc.id,
            docType = doc.docType.name,
            docNumberMasked = maskDocNumber(doc.docNumberEnc),
            issueDate = doc.issueDate,
            expiryDate = doc.expiryDate,
            issuingCountry = doc.issuingCountry,
            daysRemaining = days,
            status = dynamicStatus,
            hasAttachment = !doc.attachmentKey.isNullOrBlank(),
            attachmentKey = doc.attachmentKey,
        )
    }

    private fun maskDocNumber(number: String?): String {
        if (number.isNullOrBlank()) return "N/A"
        return if (number.length <= 4) {
            "***" + number
        } else {
            val suffix = number.takeLast(4)
            "*".repeat(minOf(4, number.length - 4)) + suffix
        }
    }
}

data class EmployeeDocumentResponseDto(
    val id: UUID,
    val docType: String,
    val docNumberMasked: String,
    val issueDate: LocalDate?,
    val expiryDate: LocalDate?,
    val issuingCountry: String?,
    val daysRemaining: Int?,
    val status: String,
    val hasAttachment: Boolean,
    val attachmentKey: String?,
)

data class RenewDocumentCommand(
    val docType: String,
    val docNumber: String,
    val issueDate: LocalDate? = null,
    val expiryDate: LocalDate,
    val issuingCountry: String? = null,
    val attachmentKey: String? = null,
    val previousDocumentId: UUID? = null,
)

data class RenewDocumentResult(
    val document: EmployeeDocumentResponseDto,
    val message: String,
)
