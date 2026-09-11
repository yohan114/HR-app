package com.hr.employee.internal

import com.hr.employee.DocumentExpiringEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDate
import java.util.UUID

@DisplayName("Document expiry sweeper (P1-BE-40)")
class DocumentExpirySweeperTest {
    private val repository = mock(EmployeeDocumentRepository::class.java)
    private val eventPublisher = mock(ApplicationEventPublisher::class.java)
    private val jdbc = mock(JdbcTemplate::class.java)
    private val sweeper = DocumentExpirySweeper(repository, eventPublisher, jdbc)

    private val tenantId = UUID.randomUUID()
    private val employeeId = UUID.randomUUID()
    private val today = LocalDate.of(2026, 9, 6)

    @Test
    fun `marks document as EXPIRED and publishes event when expiry date is in the past`() {
        val expiredDoc =
            EmployeeDocument(
                employeeId = employeeId,
                docType = DocumentType.VISA,
                expiryDate = today.minusDays(2),
                alertDaysBefore = 30,
                status = DocumentStatus.VALID,
            ).apply {
                this.tenantId = this@DocumentExpirySweeperTest.tenantId
            }

        `when`(repository.findTrackableDocuments(tenantId)).thenReturn(listOf(expiredDoc))

        val result = sweeper.sweepTenant(tenantId, asOfDate = today)

        assertThat(result.scannedCount).isEqualTo(1)
        assertThat(result.expiredCount).isEqualTo(1)
        assertThat(result.expiringCount).isEqualTo(0)
        assertThat(result.eventsPublished).isEqualTo(1)
        assertThat(expiredDoc.status).isEqualTo(DocumentStatus.EXPIRED)

        verify(repository).save(expiredDoc)

        val captor = ArgumentCaptor.forClass(DocumentExpiringEvent::class.java)
        verify(eventPublisher).publishEvent(captor.capture())

        val event = captor.value
        assertThat(event.tenantId).isEqualTo(tenantId)
        assertThat(event.employeeId).isEqualTo(employeeId)
        assertThat(event.docType).isEqualTo("VISA")
        assertThat(event.isExpired).isTrue()
        assertThat(event.daysRemaining).isEqualTo(-2L)
    }

    @Test
    fun `marks document as EXPIRING and publishes event when within alert window`() {
        val expiringDoc =
            EmployeeDocument(
                employeeId = employeeId,
                docType = DocumentType.PASSPORT,
                expiryDate = today.plusDays(15),
                alertDaysBefore = 30,
                status = DocumentStatus.VALID,
            ).apply {
                this.tenantId = this@DocumentExpirySweeperTest.tenantId
            }

        `when`(repository.findTrackableDocuments(tenantId)).thenReturn(listOf(expiringDoc))

        val result = sweeper.sweepTenant(tenantId, asOfDate = today)

        assertThat(result.scannedCount).isEqualTo(1)
        assertThat(result.expiringCount).isEqualTo(1)
        assertThat(result.expiredCount).isEqualTo(0)
        assertThat(result.eventsPublished).isEqualTo(1)
        assertThat(expiringDoc.status).isEqualTo(DocumentStatus.EXPIRING)

        verify(repository).save(expiringDoc)

        val captor = ArgumentCaptor.forClass(DocumentExpiringEvent::class.java)
        verify(eventPublisher).publishEvent(captor.capture())

        val event = captor.value
        assertThat(event.tenantId).isEqualTo(tenantId)
        assertThat(event.employeeId).isEqualTo(employeeId)
        assertThat(event.docType).isEqualTo("PASSPORT")
        assertThat(event.isExpired).isFalse()
        assertThat(event.daysRemaining).isEqualTo(15L)
    }

    @Test
    fun `leaves document unchanged when expiry date is outside alert window`() {
        val safeDoc =
            EmployeeDocument(
                employeeId = employeeId,
                docType = DocumentType.DRIVING_LICENCE,
                expiryDate = today.plusDays(90),
                alertDaysBefore = 30,
                status = DocumentStatus.VALID,
            ).apply {
                this.tenantId = this@DocumentExpirySweeperTest.tenantId
            }

        `when`(repository.findTrackableDocuments(tenantId)).thenReturn(listOf(safeDoc))

        val result = sweeper.sweepTenant(tenantId, asOfDate = today)

        assertThat(result.scannedCount).isEqualTo(1)
        assertThat(result.expiringCount).isEqualTo(0)
        assertThat(result.expiredCount).isEqualTo(0)
        assertThat(result.eventsPublished).isEqualTo(0)
        assertThat(safeDoc.status).isEqualTo(DocumentStatus.VALID)

        verify(repository, times(0)).save(safeDoc)
        verify(eventPublisher, times(0)).publishEvent(org.mockito.ArgumentMatchers.any())
    }
}
