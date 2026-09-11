package com.hr.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hr.app.data.sync.Outbox
import com.hr.client.api.MeApi
import com.hr.client.api.MobileApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalTime
import javax.inject.Inject

/**
 * ViewModel for the mobile home dashboard.
 *
 * Implements docs/home-composite.md:
 * - Aggregates composite dashboard cards in a single network round trip (P1-BE-25)
 * - Observes offline outbox pending/in-flight entities to dynamically subtract offline
 *   approvals from the pending count (§3)
 * - Gracefully handles offline states and open-string card extensions (§4)
 */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val mobileApi: MobileApi,
        private val meApi: MeApi,
        private val outbox: Outbox,
    ) : ViewModel() {

        private val _state = MutableStateFlow(HomeState())
        val state: StateFlow<HomeState> = _state.asStateFlow()

        init {
            observeOutbox()
            loadHome()
        }

        private fun observeOutbox() {
            viewModelScope.launch {
                outbox.pendingAggregateKeys.collect { keys ->
                    val keySet = keys.map { it.aggregateType to it.aggregateId }.toSet()
                    _state.update { it.copy(pendingOutboxEntities = keySet) }
                }
            }
        }

        fun refresh() = loadHome(isRefresh = true)

        fun retry() = loadHome(isRefresh = false)

        fun loadHome(isRefresh: Boolean = false) {
            _state.update {
                it.copy(
                    loading = !isRefresh && it.cards.isEmpty(),
                    refreshing = isRefresh,
                    error = null,
                )
            }

            viewModelScope.launch {
                // Determine personalized greeting
                val hour = LocalTime.now().hour
                val timeGreeting = when (hour) {
                    in 4..11 -> "Good morning"
                    in 12..16 -> "Good afternoon"
                    else -> "Good evening"
                }

                // Fetch user info for greeting
                runCatching { meApi.getMe() }.onSuccess { meResponse ->
                    val me = meResponse.body()
                    if (me != null) {
                        val name = me.displayName ?: me.username
                        val firstName = name.split(" ").firstOrNull() ?: name
                        _state.update {
                            it.copy(
                                greeting = "$timeGreeting, $firstName",
                                userName = name,
                                tenantName = me.tenant.name,
                            )
                        }
                    }
                }

                // Fetch home composite
                runCatching {
                    mobileApi.getMobileHome()
                }.onSuccess { response ->
                    val home = response.body()
                    if (response.isSuccessful && home != null) {
                        _state.update {
                            it.copy(
                                loading = false,
                                refreshing = false,
                                isOffline = false,
                                error = null,
                                syncCursor = home.syncCursor,
                                cards = home.cards.sortedBy { card -> card.priority },
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                loading = false,
                                refreshing = false,
                                error = "Could not load your dashboard.",
                            )
                        }
                    }
                }.onFailure { cause ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            isOffline = cause is IOException,
                            error = if (cause is IOException) {
                                "No connection. Changes are saved on this device."
                            } else {
                                "Could not load your dashboard."
                            },
                        )
                    }
                }
            }
        }
    }
