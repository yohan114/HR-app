package com.hr.app.ui.home

import com.hr.app.data.local.PendingAggregateKey
import com.hr.app.data.sync.Outbox
import com.hr.client.api.MeApi
import com.hr.client.api.MobileApi
import com.hr.client.model.HomeCard
import com.hr.client.model.MeResponse
import com.hr.client.model.MobileHomeResponse
import com.hr.client.model.TenantSummary
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val mobileApi = mockk<MobileApi>()
    private val meApi = mockk<MeApi>()
    private val outbox = mockk<Outbox>()

    private val pendingKeysFlow = MutableSharedFlow<List<PendingAggregateKey>>(replay = 1)
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { outbox.pendingAggregateKeys } returns pendingKeysFlow
        pendingKeysFlow.tryEmit(emptyList())

        coEvery { meApi.getMe() } returns Response.success(
            MeResponse(
                userId = UUID.randomUUID(),
                username = "nimali",
                displayName = "Nimali Wickramasinghe",
                employeeId = UUID.randomUUID(),
                tenant = TenantSummary(id = UUID.randomUUID(), code = "demo", name = "Demo Company"),
                permissions = listOf("employee.manage"),
                roles = listOf("ADMIN"),
                enabledModules = listOf("identity", "employee"),
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sampleCard(id: String, type: String, priority: Int) = HomeCard(
        id = id,
        type = type,
        title = "Card $id",
        priority = priority,
        payload = mapOf("dummy" to JsonPrimitive("test")),
    )

    @Test
    fun `loads cards and sorts them by server priority`() = runTest(dispatcher) {
        val cards = listOf(
            sampleCard(id = "c3", type = "ANNOUNCEMENTS", priority = 30),
            sampleCard(id = "c1", type = "PENDING_APPROVALS", priority = 10),
            sampleCard(id = "c2", type = "EXPIRING_DOCUMENTS", priority = 20),
        )

        coEvery { mobileApi.getMobileHome() } returns Response.success(
            MobileHomeResponse(syncCursor = "cursor-1", cards = cards)
        )

        val vm = HomeViewModel(mobileApi, meApi, outbox)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.loading)
        assertNull(state.error)
        assertEquals("cursor-1", state.syncCursor)
        assertEquals(3, state.cards.size)
        // Ordered by priority: 10, 20, 30
        assertEquals("c1", state.cards[0].id)
        assertEquals("c2", state.cards[1].id)
        assertEquals("c3", state.cards[2].id)
        assertEquals("Demo Company", state.tenantName)
        assertTrue(state.greeting.contains("Nimali"))
    }

    @Test
    fun `observes outbox pending aggregate keys`() = runTest(dispatcher) {
        coEvery { mobileApi.getMobileHome() } returns Response.success(
            MobileHomeResponse(syncCursor = null, cards = emptyList())
        )

        val vm = HomeViewModel(mobileApi, meApi, outbox)
        advanceUntilIdle()

        pendingKeysFlow.emit(
            listOf(
                PendingAggregateKey("leaveApplication", "leave-1"),
                PendingAggregateKey("leaveApplication", "leave-2"),
            )
        )
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(
            setOf("leaveApplication" to "leave-1", "leaveApplication" to "leave-2"),
            state.pendingOutboxEntities,
        )
    }

    @Test
    fun `handles network IOException by flagging offline mode`() = runTest(dispatcher) {
        coEvery { mobileApi.getMobileHome() } throws IOException("Socket closed")

        val vm = HomeViewModel(mobileApi, meApi, outbox)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.loading)
        assertTrue(state.isOffline)
        assertTrue(state.error?.contains("No connection") == true)
    }

    @Test
    fun `pull to refresh updates cards without blanking current state on load`() = runTest(dispatcher) {
        val initialCards = listOf(sampleCard(id = "init", type = "QUICK_ACTIONS", priority = 5))
        coEvery { mobileApi.getMobileHome() } returns Response.success(
            MobileHomeResponse(syncCursor = "cur-1", cards = initialCards)
        )

        val vm = HomeViewModel(mobileApi, meApi, outbox)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.cards.size)

        val refreshedCards = listOf(
            sampleCard(id = "refreshed-1", type = "MILESTONES", priority = 1),
            sampleCard(id = "refreshed-2", type = "ANNOUNCEMENTS", priority = 2),
        )
        coEvery { mobileApi.getMobileHome() } returns Response.success(
            MobileHomeResponse(syncCursor = "cur-2", cards = refreshedCards)
        )

        vm.refresh()
        // During refresh, loading is false because cards are already present
        assertFalse(vm.state.value.loading)
        advanceUntilIdle()

        val finalState = vm.state.value
        assertFalse(finalState.refreshing)
        assertEquals("cur-2", finalState.syncCursor)
        assertEquals(2, finalState.cards.size)
        assertEquals("refreshed-1", finalState.cards[0].id)
    }
}
