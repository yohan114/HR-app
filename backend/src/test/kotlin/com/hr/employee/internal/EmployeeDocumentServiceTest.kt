package com.hr.employee.internal

import com.hr.identity.Caller
import com.hr.tenancy.IsolationTier
import com.hr.tenancy.TenantContext
import com.hr.tenancy.TenantHandle
import com.hr.tenancy.TenantStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@DisplayName("Employee document and compliance service")
class EmployeeDocumentServiceTest {
    private val repository = mock(EmployeeDocumentRepository::class.java)
    private val service = EmployeeDocumentService(repository)

    private val tenantId = UUID.randomUUID()
    private val employeeId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val today = LocalDate.of(2026, 9, 7)

    private val tenantHandle = TenantHandle(
        id = tenantId,
        code = "TEST_ORG",
        name = "Test Org",
        dataRegion = "ap-south-1",
        defaultCurrency = "USD",
        timezone = "UTC",
        locale = "en",
        isolationTier = IsolationTier.SHARED,
        status = TenantStatus.ACTIVE,
    )

    private val caller = Caller(
        userId = userId,
        employeeId = employeeId,
        deviceId = null,
    )

    @Test
    fun `getOwnDocuments returns masked document numbers and calculated days remaining`() {
        val passportDoc = EmployeeDocument(
            employeeId = employeeId,
            docType = DocumentType.PASSPORT,
            docNumberEnc = "N9876543",
            issueDate = LocalDate.of(2020, 1, 1),
            expiryDate = today.plusDays(25),
            issuingCountry = "LK",
            attachmentKey = "passport_scan.pdf",
            alertDaysBefore = 30,
            status = DocumentStatus.VALID,
        ).apply {
            this.tenantId = this@EmployeeDocumentServiceTest.tenantId
        }

        val drivingLicenceDoc = EmployeeDocument(
            employeeId = employeeId,
            docType = DocumentType.DRIVING_LICENCE,
            docNumberEnc = "B1234567",
            issueDate = LocalDate.of(2022, 5, 10),
            expiryDate = today.plusDays(400),
            issuingCountry = "LK",
            attachmentKey = null,
            alertDaysBefore = 30,
            status = DocumentStatus.VALID,
        ).apply {
            this.tenantId = this@EmployeeDocumentServiceTest.tenantId
        }

        `when`(repository.findAllByTenantIdAndEmployeeId(tenantId, employeeId))
            .thenReturn(listOf(passportDoc, drivingLicenceDoc))

        val result = TenantContext.runAs(tenantHandle) {
            service.getOwnDocuments(caller, asOfDate = today)
        }

        assertThat(result).hasSize(2)
        val passport = result[0]
        assertThat(passport.docType).isEqualTo("PASSPORT")
        assertThat(passport.docNumberMasked).isEqualTo("****6543")
        assertThat(passport.daysRemaining).isEqualTo(25)
        assertThat(passport.status).isEqualTo("EXPIRING")
        assertThat(passport.hasAttachment).isTrue()

        val licence = result[1]
        assertThat(licence.docType).isEqualTo("DRIVING_LICENCE")
        assertThat(licence.docNumberMasked).isEqualTo("****4567")
        assertThat(licence.daysRemaining).isEqualTo(400)
        assertThat(licence.status).isEqualTo("VALID")
        assertThat(licence.hasAttachment).isFalse()
    }

    @Test
    fun `renewDocument replaces previous document and creates valid new record`() {
        val oldDocId = UUID.randomUUID()
        val oldPassport = EmployeeDocument(
            employeeId = employeeId,
            docType = DocumentType.PASSPORT,
            docNumberEnc = "N1111111",
            expiryDate = today.minusDays(5),
            status = DocumentStatus.EXPIRED,
        ).apply {
            this.tenantId = this@EmployeeDocumentServiceTest.tenantId
        }

        `when`(repository.findById(oldDocId)).thenReturn(Optional.of(oldPassport))
        `when`(repository.save(oldPassport)).thenReturn(oldPassport)

        val captor = ArgumentCaptor.forClass(EmployeeDocument::class.java)
        `when`(repository.save(captor.capture())).thenAnswer { invocation ->
            invocation.getArgument(0)
        }

        val request = RenewDocumentCommand(
            docType = "PASSPORT",
            docNumber = "N2222222",
            issueDate = today,
            expiryDate = today.plusYears(10),
            issuingCountry = "LK",
            attachmentKey = "new_passport.pdf",
            previousDocumentId = oldDocId,
        )

        val result = TenantContext.runAs(tenantHandle) {
            service.renewDocument(caller, request, asOfDate = today)
        }

        assertThat(oldPassport.status).isEqualTo(DocumentStatus.REPLACED)
        assertThat(result.document.docType).isEqualTo("PASSPORT")
        assertThat(result.document.status).isEqualTo("VALID")
        assertThat(result.document.hasAttachment).isTrue()
        assertThat(result.message).contains("PASSPORT renewed successfully")
    }
}
