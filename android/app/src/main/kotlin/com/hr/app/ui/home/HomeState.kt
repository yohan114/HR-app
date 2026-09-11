package com.hr.app.ui.home

import com.hr.client.model.HomeCard

/**
 * State for the mobile home dashboard.
 *
 * Cards are ordered by server-assigned priority. In-flight outbox mutations
 * ([pendingOutboxEntities]) are tracked so that pending approval counts can immediately
 * reflect offline actions without waiting for a server round trip (docs/home-composite.md §3).
 */
data class HomeState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val isOffline: Boolean = false,
    val greeting: String = "Welcome",
    val userName: String = "",
    val tenantName: String = "",
    val syncCursor: String? = null,
    val cards: List<HomeCard> = emptyList(),
    val pendingOutboxEntities: Set<Pair<String, String>> = emptySet(),
)
