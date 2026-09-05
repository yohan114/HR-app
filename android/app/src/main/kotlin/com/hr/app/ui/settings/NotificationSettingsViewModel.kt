package com.hr.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hr.client.api.MeApi
import com.hr.client.model.NotificationChannel
import com.hr.client.model.QuietHoursSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class NotificationSettingsUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    /** Dense: every event × channel the screen draws, defaults filled in. */
    val switches: Map<Pair<String, NotificationChannel>, Boolean> = emptyMap(),
    val quietHoursEnabled: Boolean = false,
    val quietStart: LocalTime = LocalTime.of(22, 0),
    val quietEnd: LocalTime = LocalTime.of(7, 0),
    val timezone: String = ZoneId.systemDefault().id,
    /** Set once a change has been made and not yet saved. */
    val dirty: Boolean = false,
)

@HiltViewModel
class NotificationSettingsViewModel
    @Inject
    constructor(
        private val meApi: MeApi,
    ) : ViewModel() {
        private val _state = MutableStateFlow(NotificationSettingsUiState())
        val state: StateFlow<NotificationSettingsUiState> = _state.asStateFlow()

        init {
            load()
        }

        fun load() {
            _state.update { it.copy(loading = true, error = null) }

            viewModelScope.launch {
                runCatching {
                    val response = meApi.getNotificationSettings()
                    if (!response.isSuccessful) error("settings ${response.code()}")
                    response.body()
                }.onSuccess { settings ->
                    val quiet = settings?.quietHours
                    _state.update {
                        it.copy(
                            loading = false,
                            error = null,
                            switches = expand(settings),
                            quietHoursEnabled = quiet?.enabled ?: false,
                            quietStart = parseTime(quiet?.startAt) ?: it.quietStart,
                            quietEnd = parseTime(quiet?.endAt) ?: it.quietEnd,
                            // The stored zone wins over the device's. A user who set quiet hours
                            // at home and is now travelling asked to be quiet at home's 10pm; a
                            // silent switch to the local zone would move the window under them.
                            timezone = quiet?.timezone ?: ZoneId.systemDefault().id,
                            dirty = false,
                        )
                    }
                }.onFailure { cause ->
                    _state.update {
                        it.copy(loading = false, error = cause.message ?: "Could not load settings")
                    }
                }
            }
        }

        fun toggle(
            eventKey: String,
            channel: NotificationChannel,
            enabled: Boolean,
        ) {
            _state.update {
                it.copy(switches = it.switches + ((eventKey to channel) to enabled), dirty = true)
            }
        }

        fun setQuietHoursEnabled(enabled: Boolean) {
            _state.update { it.copy(quietHoursEnabled = enabled, dirty = true) }
        }

        fun setQuietWindow(
            start: LocalTime,
            end: LocalTime,
        ) {
            _state.update { it.copy(quietStart = start, quietEnd = end, dirty = true) }
        }

        fun save() {
            val current = _state.value
            // The server rejects a zero-length window, and it would be a poor way to find out.
            if (current.quietHoursEnabled && current.quietStart == current.quietEnd) {
                _state.update {
                    it.copy(error = "Quiet hours must start and end at different times")
                }
                return
            }

            _state.update { it.copy(saving = true, error = null) }

            viewModelScope.launch {
                runCatching {
                    val payload =
                        collapse(
                            matrix = current.switches,
                            quietHours =
                                if (current.quietHoursEnabled) {
                                    QuietHoursSettings(
                                        startAt = current.quietStart.toString(),
                                        endAt = current.quietEnd.toString(),
                                        timezone = current.timezone,
                                        enabled = true,
                                    )
                                } else {
                                    null
                                },
                        )
                    val response = meApi.replaceNotificationSettings(payload)
                    if (!response.isSuccessful) error("save ${response.code()}")
                    response.body()
                }.onSuccess { saved ->
                    _state.update {
                        it.copy(saving = false, error = null, switches = expand(saved), dirty = false)
                    }
                }.onFailure { cause ->
                    // The switches stay as the user left them. Reverting to the server's copy
                    // would discard their edits as the response to a failure they did not cause,
                    // and leave them nothing to retry.
                    _state.update {
                        it.copy(saving = false, error = cause.message ?: "Could not save settings")
                    }
                }
            }
        }
    }

/** `22:00:00` and `22:00` both parse; anything else reads as absent rather than crashing the screen. */
internal fun parseTime(value: String?): LocalTime? =
    value?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
