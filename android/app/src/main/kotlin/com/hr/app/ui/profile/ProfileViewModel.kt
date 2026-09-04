package com.hr.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hr.client.api.EmployeesApi
import com.hr.client.model.EmployeeProfile
import com.hr.client.model.FormSchema
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * One employee profile, rendered from the server's form schema.
 *
 * Both requests are made together and awaited as a pair. The schema decides *which* fields exist
 * for this caller and the profile supplies their values, so rendering with only one of them would
 * either show a form with no data or data with no idea what may be shown.
 */
@HiltViewModel
class ProfileViewModel
    @Inject
    constructor(
        private val employeesApi: EmployeesApi,
    ) : ViewModel() {
        private val _state = MutableStateFlow(ProfileState())
        val state: StateFlow<ProfileState> = _state.asStateFlow()

        private var loadedId: UUID? = null

        /** @param employeeId null loads the caller's own profile, via `/v1/employees/me`. */
        fun load(employeeId: UUID?) {
            loadedId = employeeId
            _state.update { it.copy(loading = true, error = null) }

            viewModelScope.launch {
                runCatching {
                    // In parallel: they are independent, and serialising them would double the
                    // time the user spends looking at a spinner for no reason.
                    val profile = async { if (employeeId == null) employeesApi.getOwnEmployeeProfile() else employeesApi.getEmployeeProfile(employeeId) }
                    val schema = async { employeeId?.let { employeesApi.getEmployeeEditForm(it) } }
                    listOf(profile, schema).awaitAll()

                    val profileResponse = profile.await()
                    val body =
                        profileResponse.body().takeIf { profileResponse.isSuccessful }
                            ?: error("profile ${profileResponse.code()}")

                    body to schema.await()?.let { if (it.isSuccessful) it.body() else null }
                }.onSuccess { (profile, schema) ->
                    _state.update {
                        it.copy(loading = false, profile = profile, schema = schema, error = null)
                    }
                }.onFailure { cause ->
                    _state.update {
                        it.copy(
                            loading = false,
                            // A 404 here is deliberate on the server's part: a record the caller
                            // may not open answers 404 rather than 403, so the endpoint cannot be
                            // walked to enumerate employee ids. To the user both mean the same
                            // thing, so they get one message.
                            error =
                                if (cause is java.io.IOException) {
                                    "No connection. Check your network and try again."
                                } else {
                                    "That profile is not available."
                                },
                        )
                    }
                }
            }
        }

        fun retry() = load(loadedId)
    }

data class ProfileState(
    val profile: EmployeeProfile? = null,
    /**
     * Null for the caller's own profile, which has no edit form yet.
     *
     * When absent the screen falls back to a fixed set of fields it knows are safe — everything on
     * `/v1/employees/me` belongs to the caller, so there is nothing there they may not see.
     */
    val schema: FormSchema? = null,
    val loading: Boolean = false,
    val error: String? = null,
)
