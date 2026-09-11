package com.hr.document

import com.hr.document.internal.*
import com.hr.employee.EmployeeLeaveProfile
import com.hr.employee.EmployeeLookupService
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@DisplayName("Signature Service Unit Tests")
class SignatureServiceTest {

    private val signatureRequestRepository = mockk<SignatureRequestRepository>()
    private val signerRepository = mockk<SignatureSignerRepository>()
    private val auditLogRepository = mockk<SignatureAuditLogRepository>()
    private val companyDocumentRepository = mockk<CompanyDocumentRepository>()
    private val employeeLookupService = mockk<EmployeeLookupService>(relaxed = true)

    private lateinit var signatureService: SignatureService

    private val creatorId = UUID.randomUUID()
    private val signer1Id = UUID.randomUUID()
    private val signer2Id = UUID.randomUUID()
    private val docId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        signatureService = SignatureService(
            signatureRequestRepository = signatureRequestRepository,
            signerRepository = signerRepository,
            auditLogRepository = auditLogRepository,
            companyDocumentRepository = companyDocumentRepository,
            employeeLookupService = employeeLookupService,
        )
    }

    @Test
    fun `createSignatureRequest persists request, signers, and CREATED audit log`() {
        val creator = EmployeeLeaveProfile(
            id = creatorId,
            tenantId = UUID.randomUUID(),
            employeeCode = "EMP-000",
            firstName = "Admin",
            lastName = "HR",
            displayName = "HR Administrator",
            joinDate = LocalDate.of(2020, 1, 1),
            status = "ACTIVE",
        )
        every { employeeLookupService.findById(creatorId) } returns creator
        every { companyDocumentRepository.findById(docId) } returns Optional.empty()
        every { signatureRequestRepository.save(any()) } answers { firstArg() }
        every { signerRepository.save(any()) } answers { firstArg() }
        every { auditLogRepository.save(any()) } answers { firstArg() }
        every { signerRepository.findAllByRequestIdOrderBySigningOrderAsc(any()) } returns emptyList()

        val request = SignatureRequestCreateRequest(
            title = "Executive Non-Disclosure Agreement",
            documentId = docId,
            fileUrl = "https://storage.acmecorp.com/contracts/nda.pdf",
            fileChecksumSha256 = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
            signers = listOf(
                SignatureSignerInput(
                    signerEmployeeId = signer1Id,
                    signerName = "Kasun Mendis",
                    signerEmail = "kasun@acmecorp.com",
                    role = "Employee",
                    signingOrder = 1,
                ),
                SignatureSignerInput(
                    signerEmployeeId = signer2Id,
                    signerName = "Amanda Perera",
                    signerEmail = "amanda@acmecorp.com",
                    role = "HR Director",
                    signingOrder = 2,
                )
            )
        )

        val created = signatureService.createSignatureRequest(request, creatorId)

        assertEquals("Executive Non-Disclosure Agreement", created.title)
        assertEquals("PENDING", created.status)
        verify(exactly = 2) { signerRepository.save(any()) }
        verify(exactly = 1) { auditLogRepository.save(match { it.eventType == "CREATED" }) }
    }

    @Test
    fun `signDocument completes signature and updates status to COMPLETED when final signer signs`() {
        val sigReq = SignatureRequestEntity(
            documentId = docId,
            title = "Annual Bonus Agreement",
            status = "PENDING",
            requestedBy = creatorId,
        )

        val signer1 = SignatureSignerEntity(
            requestId = sigReq.id,
            signerEmployeeId = signer1Id,
            signerName = "Kasun Mendis",
            signerEmail = "kasun@acmecorp.com",
            signingOrder = 1,
            status = "PENDING",
        )

        every { signatureRequestRepository.findById(sigReq.id) } returns Optional.of(sigReq)
        every { signerRepository.findAllByRequestIdOrderBySigningOrderAsc(sigReq.id) } returns listOf(signer1)
        every { signerRepository.save(any()) } answers { firstArg() }
        every { auditLogRepository.save(any()) } answers { firstArg() }
        every { signatureRequestRepository.save(any()) } answers { firstArg() }
        every { employeeLookupService.findById(any()) } returns null
        every { companyDocumentRepository.findById(any()) } returns Optional.empty()
        every { auditLogRepository.findAllByRequestIdOrderByCreatedAtAsc(sigReq.id) } returns emptyList()

        val signRequest = SignatureSignRequest(
            signerEmployeeId = signer1Id,
            signatureType = "CANVAS_DRAWING",
            signatureData = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
            consentConfirmed = true,
            ipAddress = "192.168.1.100",
        )

        val result = signatureService.signDocument(sigReq.id, signRequest)

        assertEquals("COMPLETED", result.request.status)
        assertEquals(1, result.signers.size)
        assertEquals("SIGNED", result.signers[0].status)
        assertEquals("CANVAS_DRAWING", result.signers[0].signatureType)
        verify { auditLogRepository.save(match { it.eventType == "SIGNED" }) }
        verify { auditLogRepository.save(match { it.eventType == "COMPLETED" }) }
    }

    @Test
    fun `signDocument throws exception when request is already completed`() {
        val sigReq = SignatureRequestEntity(
            documentId = docId,
            title = "Executed Agreement",
            status = "COMPLETED",
            requestedBy = creatorId,
        )
        every { signatureRequestRepository.findById(sigReq.id) } returns Optional.of(sigReq)

        val signRequest = SignatureSignRequest(
            signerEmployeeId = signer1Id,
            signatureType = "TYPED_NAME",
            signatureData = "Kasun Mendis",
            consentConfirmed = true,
        )

        assertThrows(IllegalStateException::class.java) {
            signatureService.signDocument(sigReq.id, signRequest)
        }
    }
}
