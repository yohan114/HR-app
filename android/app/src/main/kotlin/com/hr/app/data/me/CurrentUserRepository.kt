package com.hr.app.data.me

import com.hr.client.api.MeApi
import com.hr.client.model.MeResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Who is signed in, and what they may do.
 *
 * `GET /v1/me` returns identity, effective permissions and enabled modules together, deliberately:
 * the navigation shell needs all three before it can draw a single tab, and assembling that from
 * three calls would mean three chances to render a half-built app.
 *
 * Held here rather than in a ViewModel because several screens need it and none of them owns it —
 * the shell decides its tabs from `permissions`, the profile screen needs `employeeId`, and a
 * sign-out has to clear it.
 */
@Singleton
class CurrentUserRepository
    @Inject
    constructor(
        private val meApi: MeApi,
    ) {
        private val _user = MutableStateFlow<MeResponse?>(null)
        val user: StateFlow<MeResponse?> = _user.asStateFlow()

        /**
         * Loads the current user.
         *
         * Returns the failure rather than throwing, because the caller's decision is always the
         * same shape: a shell that cannot load this cannot decide what to draw, so it shows a
         * retry rather than guessing at a tab set.
         */
        suspend fun refresh(): Result<MeResponse> =
            runCatching {
                val response = meApi.getMe()
                val body =
                    response.body().takeIf { response.isSuccessful }
                        ?: error("GET /v1/me failed with ${response.code()}")
                _user.value = body
                body
            }

        fun clear() {
            _user.value = null
        }

        /**
         * Whether the caller approves anything.
         *
         * Drives the Approvals tab. Checked against a *list* of permissions rather than one,
         * because approval authority is granted per module — a manager who approves leave but not
         * expenses still needs the tab, and by Phase 3 there will be five of these.
         */
        val canApprove: Boolean
            get() = _user.value?.permissions.orEmpty().any { it in APPROVAL_PERMISSIONS }

        private companion object {
            /**
             * Anything that puts work in somebody's queue.
             *
             * Only the first exists today. The rest are listed now so the tab appears the moment
             * the permission is granted, rather than needing a client change to notice a
             * capability the server already reports.
             */
            val APPROVAL_PERMISSIONS =
                setOf(
                    "employee.manage",
                    "leave.approve",
                    "attendance.approve",
                    "expense.approve",
                    "timesheet.approve",
                )
        }
    }
