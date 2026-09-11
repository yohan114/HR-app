package com.hr.app.ui.disciplinary

import com.hr.app.data.disciplinary.DisciplinaryRepository
import com.hr.app.data.disciplinary.GrievanceRepository
import com.hr.client.model.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class DisciplinaryViewModelTest {

    private val grievanceRepository = mockk<GrievanceRepository>(relaxed = true)
    private val disciplinaryRepository = mockk<DisciplinaryRepository>(relaxed = true)

    private val groundsFlow = MutableStateFlow<GrievanceGroundsResponse?>(null)
    private val channelsFlow = MutableStateFlow<List<GrievanceChannelItem>>(emptyList())
    private val grievancesFlow = MutableStateFlow<List<GrievanceItem>>(emptyList())
    private val selectedGrievanceFlow = MutableStateFlow<GrievanceDetailResponse?>(null)
    private val pendingGrievanceCountFlow = MutableStateFlow(0)

    private val typesFlow = MutableStateFlow<List<IncidentTypeItem>>(emptyList())
    private val incidentsFlow = MutableStateFlow<List<DisciplinaryIncidentItem>>(emptyList())
    private val selectedIncidentFlow = MutableStateFlow<DisciplinaryIncidentDetailResponse?>(null)
    private val openIncidentCountFlow = MutableStateFlow(0)

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: DisciplinaryViewModel

    private val sampleGround = GrievanceGroundItem(
        id = UUID.randomUUID(),
        groupId = UUID.randomUUID(),
        code = "SAFETY",
        name = "Safety Issue",
        description = "Safety issue",
        severity = GrievanceGroundItem.Severity.HIGH,
        slaDays = 3,
        defaultHandlerRole = "HR",
        groupName = "Environment",
    )

    private val sampleChannel = GrievanceChannelItem(
        id = UUID.randomUUID(),
        code = "DIRECT",
        name = "Direct Portal",
        isConfidential = true,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { grievanceRepository.grounds } returns groundsFlow
        every { grievanceRepository.channels } returns channelsFlow
        every { grievanceRepository.grievances } returns grievancesFlow
        every { grievanceRepository.selectedGrievance } returns selectedGrievanceFlow
        every { grievanceRepository.pendingCount } returns pendingGrievanceCountFlow

        every { disciplinaryRepository.types } returns typesFlow
        every { disciplinaryRepository.incidents } returns incidentsFlow
        every { disciplinaryRepository.selectedIncident } returns selectedIncidentFlow
        every { disciplinaryRepository.openCount } returns openIncidentCountFlow

        viewModel = DisciplinaryViewModel(
            grievanceRepository = grievanceRepository,
            disciplinaryRepository = disciplinaryRepository,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initialization triggers repository refreshes`() = runTest {
        advanceUntilIdle()

        coVerify { grievanceRepository.refreshGrounds() }
        coVerify { grievanceRepository.refreshChannels() }
        coVerify { grievanceRepository.refreshMyGrievances() }
        coVerify { disciplinaryRepository.refreshTypes() }
        coVerify { disciplinaryRepository.refreshIncidents() }
    }

    @Test
    fun `selectTab updates activeTab and clears banners`() {
        viewModel.selectTab(DisciplinaryGrievanceTab.FILE_GRIEVANCE)
        assertEquals(DisciplinaryGrievanceTab.FILE_GRIEVANCE, viewModel.uiState.value.activeTab)

        viewModel.selectTab(DisciplinaryGrievanceTab.DISCIPLINARY_NOTICES)
        assertEquals(DisciplinaryGrievanceTab.DISCIPLINARY_NOTICES, viewModel.uiState.value.activeTab)

        viewModel.selectTab(DisciplinaryGrievanceTab.HOTLINE)
        assertEquals(DisciplinaryGrievanceTab.HOTLINE, viewModel.uiState.value.activeTab)
    }

    @Test
    fun `updateGrievanceForm updates form fields and validation blocks blank title`() = runTest {
        viewModel.updateGrievanceForm(
            title = "",
            description = "Some description",
        )

        viewModel.submitGrievance()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.errorBanner)
        assertEquals("Please specify a grievance title", viewModel.uiState.value.errorBanner)
        coVerify(exactly = 0) { grievanceRepository.submitGrievance(any()) }
    }

    @Test
    fun `submitGrievance successfully submits when form is valid`() = runTest {
        groundsFlow.value = GrievanceGroundsResponse(groups = emptyList(), grounds = listOf(sampleGround))
        channelsFlow.value = listOf(sampleChannel)

        viewModel.updateGrievanceForm(
            title = "Unfair penalty on roster",
            description = "Shift deducted incorrectly",
            groundId = sampleGround.id,
            channelId = sampleChannel.id,
        )
        viewModel.toggleAnonymous(true)

        val detail = GrievanceDetailResponse(
            id = UUID.randomUUID(),
            grievanceNumber = "GRV-2026-0001",
            ground = sampleGround,
            channel = sampleChannel,
            title = "Unfair penalty on roster",
            description = "Shift deducted incorrectly",
            anonymous = true,
            status = GrievanceDetailResponse.Status.SUBMITTED,
            raisedAt = OffsetDateTime.now(),
            targetResolutionDate = OffsetDateTime.now().plusDays(3),
            raisedByEmployeeName = "Confidential (Anonymous Whistleblower)",
            resolution = null,
            appeal = null,
        )

        coEvery { grievanceRepository.submitGrievance(any()) } returns Result.success(detail)

        viewModel.submitGrievance()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.errorBanner)
        assertNotNull(viewModel.uiState.value.successBanner)
        assertTrue(viewModel.uiState.value.successBanner!!.contains("GRV-2026-0001"))
        assertEquals(DisciplinaryGrievanceTab.MY_GRIEVANCES, viewModel.uiState.value.activeTab)
        assertFalse(viewModel.uiState.value.isAnonymous)
        assertEquals("", viewModel.uiState.value.grievanceTitle)
    }

    @Test
    fun `openGrievanceAppeal and submitGrievanceAppeal flow`() = runTest {
        val grievanceId = UUID.randomUUID()
        viewModel.openGrievanceAppeal(grievanceId)

        assertTrue(viewModel.uiState.value.showGrievanceAppealDialog)
        assertEquals(grievanceId, viewModel.uiState.value.appealGrievanceId)

        viewModel.updateGrievanceAppealReason("Inadequate investigation findings")

        val appealDetail = GrievanceDetailResponse(
            id = grievanceId,
            grievanceNumber = "GRV-2026-0001",
            ground = sampleGround,
            channel = sampleChannel,
            title = "Sample",
            description = "Sample",
            anonymous = false,
            status = GrievanceDetailResponse.Status.APPEALED,
            raisedAt = OffsetDateTime.now(),
            targetResolutionDate = OffsetDateTime.now().plusDays(3),
            raisedByEmployeeName = "Kasun Mendis",
            resolution = null,
            appeal = null,
        )

        coEvery { grievanceRepository.appealGrievance(grievanceId, "Inadequate investigation findings") } returns Result.success(appealDetail)

        viewModel.submitGrievanceAppeal()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showGrievanceAppealDialog)
        assertNotNull(viewModel.uiState.value.successBanner)
        assertTrue(viewModel.uiState.value.successBanner!!.contains("Appeal lodged successfully"))
    }

    @Test
    fun `openActionResponse and submitActionResponse flow`() = runTest {
        val actionId = UUID.randomUUID()
        viewModel.openActionResponse(actionId)

        assertTrue(viewModel.uiState.value.showActionResponseDialog)
        assertEquals(actionId, viewModel.uiState.value.respondingActionId)

        viewModel.updateResponseExplanation("I was on approved emergency leave")

        val actionItem = CorrectiveActionItem(
            id = actionId,
            incidentId = UUID.randomUUID(),
            actionType = CorrectiveActionItem.ActionType.SHOW_CAUSE,
            issuedAt = OffsetDateTime.now(),
            status = CorrectiveActionItem.Status.RESPONDED,
            issuedBy = UUID.randomUUID(),
            issuedByName = "HR Director",
            title = "Show cause",
            details = "Details",
            responseDueDate = LocalDate.now(),
            employeeResponse = "I was on approved emergency leave",
            respondedAt = OffsetDateTime.now(),
        )

        coEvery { disciplinaryRepository.respondToCorrectiveAction(actionId, "I was on approved emergency leave") } returns Result.success(actionItem)

        viewModel.submitActionResponse()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showActionResponseDialog)
        assertNotNull(viewModel.uiState.value.successBanner)
        assertTrue(viewModel.uiState.value.successBanner!!.contains("Response to show cause notice submitted"))
    }

    @Test
    fun `openActionAppeal and submitActionAppeal flow`() = runTest {
        val actionId = UUID.randomUUID()
        viewModel.openActionAppeal(actionId)

        assertTrue(viewModel.uiState.value.showActionAppealDialog)
        assertEquals(actionId, viewModel.uiState.value.appealingActionId)

        viewModel.updateActionAppealReason("Disproportionate warning given previous clean record")

        val appealItem = DisciplinaryAppealItem(
            id = UUID.randomUUID(),
            incidentId = UUID.randomUUID(),
            correctiveActionId = actionId,
            reason = "Disproportionate warning given previous clean record",
            appealedAt = OffsetDateTime.now(),
            status = DisciplinaryAppealItem.Status.SUBMITTED,
            outcome = null,
            reviewedAt = null,
        )

        coEvery { disciplinaryRepository.appealCorrectiveAction(actionId, "Disproportionate warning given previous clean record") } returns Result.success(appealItem)

        viewModel.submitActionAppeal()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showActionAppealDialog)
        assertNotNull(viewModel.uiState.value.successBanner)
        assertTrue(viewModel.uiState.value.successBanner!!.contains("Appeal lodged against disciplinary action"))
    }
}
