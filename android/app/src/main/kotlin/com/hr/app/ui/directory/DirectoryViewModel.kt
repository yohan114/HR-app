package com.hr.app.ui.directory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hr.client.api.DirectoryApi
import com.hr.client.model.DirectoryEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The employee directory.
 *
 * Available to every authenticated employee — finding a colleague's extension is not a privileged
 * operation, and gating it behind a permission would lock most of the workforce out of the feature
 * they open most often. What makes that safe is what the endpoint does not select: no salary, no
 * bank details, no identity documents, no date of birth.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class DirectoryViewModel
    @Inject
    constructor(
        private val directoryApi: DirectoryApi,
    ) : ViewModel() {
        private val _state = MutableStateFlow(DirectoryState())
        val state: StateFlow<DirectoryState> = _state.asStateFlow()

        private val queries = MutableStateFlow("")

        init {
            viewModelScope.launch {
                queries
                    // Typing a name should not fire a request per keystroke. 250ms collapses a
                    // burst of typing without the results feeling detached from the input.
                    .debounce(DEBOUNCE_MILLIS)
                    .distinctUntilChanged()
                    .collect { search(it) }
            }
        }

        fun onQueryChanged(value: String) {
            _state.update { it.copy(query = value) }
            queries.value = value
        }

        fun retry() = search(_state.value.query)

        private fun search(query: String) {
            _state.update { it.copy(loading = true, error = null) }

            viewModelScope.launch {
                runCatching {
                    directoryApi.searchDirectory(
                        query = query.takeIf { it.isNotBlank() },
                        departmentId = null,
                        locationId = null,
                        cursor = null,
                        limit = PAGE_SIZE,
                    )
                }.onSuccess { response ->
                    val page = response.body()
                    if (response.isSuccessful && page != null) {
                        _state.update {
                            it.copy(loading = false, entries = page.items.orEmpty(), error = null)
                        }
                    } else {
                        _state.update { it.copy(loading = false, error = "Could not load the directory.") }
                    }
                }.onFailure { cause ->
                    _state.update {
                        it.copy(
                            loading = false,
                            // Offline is the expected case on a phone, not an exception. Saying
                            // "no connection" is actionable; "something went wrong" is not.
                            error =
                                if (cause is java.io.IOException) {
                                    "No connection. Check your network and try again."
                                } else {
                                    "Could not load the directory."
                                },
                        )
                    }
                }
            }
        }

        private companion object {
            const val DEBOUNCE_MILLIS = 250L
            const val PAGE_SIZE = 50
        }
    }

data class DirectoryState(
    val query: String = "",
    val entries: List<DirectoryEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)
