package com.hr.app.ui.profile

import com.hr.client.api.EmployeesApi
import com.hr.client.model.EmployeeDocumentItem
import com.hr.client.model.EmployeeProfile
import com.hr.client.model.RenewDocumentRequest
import com.hr.client.model.RenewDocumentResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val employeesApi = mockk<EmployeesApi>()
    private val testDispatcher = StandardTestDispatcher()

    private val sampleDoc = EmployeeDocumentItem(
        id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        docType = "PASSPORT",
        docNumberMasked = "****6543",
        status = "EXPIRING",
        hasAttachment = true,
        issueDate = LocalDate.of(2021, 10, 5),
        expiryDate = LocalDate.now().plusDays(25),
        issuingCountry = "LK",
        daysRemaining = 25,
        attachmentKey = "passport_scan.pdf",
    )

    private val sampleProfile = EmployeeProfile(
        id = UUID.fromString("00000000-0000-0000-0000-000000000010"),
        version = 1L,
        employeeCode = "EMP001",
        displayName = "Nimali Wickramasinghe",
        workEmail = "nimali@demo.local",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load own profile loads profile and compliance documents successfully`() = runTest {
        coEvery { employeesApi.getOwnEmployeeProfile() } returns Response.success(sampleProfile)
        coEvery { employeesApi.getOwnDocuments() } returns Response.success(listOf(sampleDoc))

        val viewModel = ProfileViewModel(employeesApi)
        viewModel.load(null)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.loading)
        assertNull(state.error)
        assertNotNull(state.profile)
        assertEquals("Nimali Wickramasinghe", state.profile?.displayName)
        assertEquals(1, state.documents.size)
        assertEquals("PASSPORT", state.documents[0].docType)
        assertEquals(25, state.documents[0].daysRemaining)
    }

    @Test
    fun `openRenewalDialog and closeRenewalDialog update state properly`() = runTest {
        val viewModel = ProfileViewModel(employeesApi)
        assertNull(viewModel.state.value.selectedDocumentForRenewal)

        viewModel.openRenewalDialog(sampleDoc)
        assertEquals(sampleDoc, viewModel.state.value.selectedDocumentForRenewal)

        viewModel.closeRenewalDialog()
        assertNull(viewModel.state.value.selectedDocumentForRenewal)
    }

    @Test
    fun `openRenewalDialogById selects matching document from state`() = runTest {
        coEvery { employeesApi.getOwnEmployeeProfile() } returns Response.success(sampleProfile)
        coEvery { employeesApi.getOwnDocuments() } returns Response.success(listOf(sampleDoc))

        val viewModel = ProfileViewModel(employeesApi)
        viewModel.load(null)
        advanceUntilIdle()

        viewModel.openRenewalDialogById("00000000-0000-0000-0000-000000000001")
        assertEquals(sampleDoc, viewModel.state.value.selectedDocumentForRenewal)
    }

    @Test
    fun `submitRenewal executes api call, updates document list, and displays success message`() = runTest {
        coEvery { employeesApi.getOwnEmployeeProfile() } returns Response.success(sampleProfile)
        coEvery { employeesApi.getOwnDocuments() } returns Response.success(listOf(sampleDoc))

        val renewedDoc = sampleDoc.copy(
            id = UUID.randomUUID(),
            docNumberMasked = "****9988",
            expiryDate = LocalDate.now().plusYears(5),
            status = "VALID",
            daysRemaining = 1825,
        )
        val responseBody = RenewDocumentResponse(
            document = renewedDoc,
            message = "Document renewed successfully",
        )
        coEvery { employeesApi.renewOwnDocument(any()) } returns Response.success(responseBody)

        val viewModel = ProfileViewModel(employeesApi)
        viewModel.load(null)
        advanceUntilIdle()

        viewModel.openRenewalDialog(sampleDoc)
        viewModel.submitRenewal(
            docType = "PASSPORT",
            docNumber = "N1239988",
            expiryDate = LocalDate.now().plusYears(5),
            issuingCountry = "LK",
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.renewalInProgress)
        assertNull(state.selectedDocumentForRenewal)
        assertEquals("Document renewed successfully", state.renewalMessage)
        assertEquals(1, state.documents.size)
        assertEquals(renewedDoc.id, state.documents[0].id)
        assertEquals("VALID", state.documents[0].status)

        coVerify {
            employeesApi.renewOwnDocument(
                match {
                    it.docType == "PASSPORT" &&
                    it.docNumber == "N1239988" &&
                    it.previousDocumentId == sampleDoc.id
                }
            )
        }
    }
}
