package com.bydmate.app.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bydmate.app.data.repository.SettingsRepository
import com.bydmate.app.data.repository.TripRepository
import com.bydmate.app.data.trips.TripAutoResetMode
import com.bydmate.app.data.trips.TripCounterMath
import com.bydmate.app.data.trips.TripCounterResets
import com.bydmate.app.data.trips.TripCounterUi
import com.bydmate.app.service.TrackingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Kom-BYDMate: TRIP 1 / TRIP 2 for the Reports screen and the dashboard's built-in «TRIP» card,
 * the same counters the original dashboard showed (DashboardViewModel.collectTripCounter).
 */
@HiltViewModel
class TripCountersViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val settingsRepository: SettingsRepository,
    private val tripCounterResets: TripCounterResets,
) : ViewModel() {

    data class State(
        val trip1: TripCounterUi? = null,
        val trip2: TripCounterUi? = null,
        val mode1: TripAutoResetMode = TripAutoResetMode.OFF,
        val mode2: TripAutoResetMode = TripAutoResetMode.OFF,
        val currencySymbol: String = "֏",
        /** 0 none, 1 or 2: whose details are open. */
        val expanded: Int = 0,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(currencySymbol = settingsRepository.getCurrencySymbol()) }
        }
        viewModelScope.launch {
            tripCounterResets.load()
            val tariff = settingsRepository.getTripCostTariff()
            launch { collect(1, tariff) }
            launch { collect(2, tariff) }
        }
        viewModelScope.launch {
            settingsRepository.observeTripAutoResetMode(1).collect { m -> _state.update { it.copy(mode1 = m) } }
        }
        viewModelScope.launch {
            settingsRepository.observeTripAutoResetMode(2).collect { m -> _state.update { it.copy(mode2 = m) } }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun collect(n: Int, tariff: Double) {
        tripCounterResets.state(n).filterNotNull()
            .combine(TrackingService.sessionStartedAt) { reset, sessionStart -> reset to sessionStart }
            .flatMapLatest { (reset, sessionStart) ->
                combine(
                    tripRepository.observeCounterStats(reset.resetTs, sessionStart ?: Long.MAX_VALUE),
                    TrackingService.tripDistanceKm,
                    TrackingService.tripKwhConsumed,
                    TrackingService.sessionStartedAt,
                    TrackingService.liveWholeSession,
                ) { stats, liveKm, liveKwh, sessionStartInner, liveWholeSession ->
                    TripCounterMath.compute(stats, reset, liveKm, liveKwh, sessionStartInner,
                        liveWholeSession, System.currentTimeMillis(), tariff)
                }
            }.collect { ui -> _state.update { if (n == 1) it.copy(trip1 = ui) else it.copy(trip2 = ui) } }
    }

    fun open(n: Int) = _state.update { it.copy(expanded = n) }

    fun close() = _state.update { it.copy(expanded = 0) }

    /** Long press: the anchor logic lives in [TripCounterResets.reset]. */
    fun reset(n: Int) {
        viewModelScope.launch { tripCounterResets.reset(n) }
    }

    fun setMode(n: Int, mode: TripAutoResetMode) {
        viewModelScope.launch { settingsRepository.setTripAutoResetMode(n, mode) }
    }
}
