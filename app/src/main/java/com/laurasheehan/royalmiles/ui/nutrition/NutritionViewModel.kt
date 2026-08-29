package com.laurasheehan.royalmiles.ui.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import com.laurasheehan.royalmiles.core.plan.NutritionTargets
import com.laurasheehan.royalmiles.core.plan.NutritionTargetsCalculator
import com.laurasheehan.royalmiles.data.AthleteProfileRepository
import com.laurasheehan.royalmiles.data.PlanRepository
import com.laurasheehan.royalmiles.data.health.HealthConnectRepository
import com.laurasheehan.royalmiles.data.health.NutritionSummary
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NutritionUiState(
    val loading: Boolean = true,
    val available: Boolean = false,
    val hasPermission: Boolean = false,
    val today: NutritionSummary? = null,
    val phase: TrainingPhase? = null,
    val daysToRace: Long = 0,
    val targets: NutritionTargets? = null,
    val bodyWeightKg: Double? = null,
    /** e.g. "Withings" when the weight came from the scale rather than being typed in. */
    val weightSource: String? = null,
)

private data class HealthState(
    val loading: Boolean = true,
    val available: Boolean = false,
    val hasPermission: Boolean = false,
    val today: NutritionSummary? = null,
    val weightSource: String? = null,
)

class NutritionViewModel(
    private val repository: PlanRepository,
    private val healthConnect: HealthConnectRepository,
    private val athleteProfileRepository: AthleteProfileRepository,
    private val raceDate: LocalDate,
) : ViewModel() {

    private val _health = MutableStateFlow(HealthState())

    val uiState: StateFlow<NutritionUiState> = combine(
        repository.observeWeeks(),
        athleteProfileRepository.observeBodyWeightKg(),
        _health,
    ) { weeks, bodyWeightKg, health ->
        val today = LocalDate.now()
        // Before the plan's Monday start, or after its last week, fall back to the nearest week
        // instead of leaving the phase (and this whole card) blank.
        val currentWeek = weeks.firstOrNull { !it.startDate.isAfter(today) && it.startDate.plusDays(6) >= today }
            ?: weeks.filter { it.startDate.isAfter(today) }.minByOrNull { it.startDate }
            ?: weeks.maxByOrNull { it.startDate }
        val phase = currentWeek?.phase
        val daysToRace = ChronoUnit.DAYS.between(today, raceDate)
        NutritionUiState(
            loading = health.loading,
            available = health.available,
            hasPermission = health.hasPermission,
            today = health.today,
            phase = phase,
            daysToRace = daysToRace,
            targets = phase?.let { NutritionTargetsCalculator.targetsFor(it, daysToRace) },
            bodyWeightKg = bodyWeightKg,
            weightSource = health.weightSource,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NutritionUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _health.update { it.copy(loading = true) }
            val available = healthConnect.isAvailable()
            val hasPermission = available && healthConnect.hasPermissions()
            val today = if (hasPermission) healthConnect.nutritionSummaryForToday() else null

            // Persist the scale reading rather than only displaying it, so the g/kg targets still
            // resolve if Health Connect is unreachable later.
            val weight = if (hasPermission) healthConnect.latestWeight() else null
            weight?.let { athleteProfileRepository.setBodyWeightKg(it.kg) }

            _health.value = HealthState(
                loading = false,
                available = available,
                hasPermission = hasPermission,
                today = today,
                weightSource = weight?.sourceLabel,
            )
        }
    }

    fun onPermissionGranted() = refresh()

    fun permissionContract() = healthConnect.requestPermissionsContract()

    val permissionsToRequest: Set<String> get() = healthConnect.permissions

    fun setBodyWeightKg(kg: Double?) {
        viewModelScope.launch { athleteProfileRepository.setBodyWeightKg(kg) }
    }
}
