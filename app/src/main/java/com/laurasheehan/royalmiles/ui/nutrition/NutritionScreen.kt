package com.laurasheehan.royalmiles.ui.nutrition

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import com.laurasheehan.royalmiles.core.plan.NutritionTargets
import com.laurasheehan.royalmiles.data.health.NutritionSummary
import com.laurasheehan.royalmiles.ui.theme.RoyalPurple
import com.laurasheehan.royalmiles.ui.theme.ShimmerSilverDim

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionScreen(viewModel: NutritionViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = remember { viewModel.permissionContract() },
        onResult = { viewModel.onPermissionGranted() },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nutrition") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> {}
            !state.available -> {
                Column(modifier = Modifier.padding(padding).padding(24.dp)) {
                    Text(
                        "Health Connect isn't available on this device. It ships with Android 14+ and " +
                            "can be installed from the Play Store on most phones back to Android 9.",
                        color = ShimmerSilverDim,
                    )
                }
            }
            !state.hasPermission -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        "Connect Health Connect to see the food you log in Cronometer here, alongside " +
                            "supportive, phase-aware guidance for this stage of training.",
                        color = ShimmerSilverDim,
                    )
                    Button(onClick = { permissionLauncher.launch(viewModel.permissionsToRequest) }) {
                        Text("Connect Health Connect")
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item { TodayCard(nutrition = state.today) }
                    item {
                        BodyWeightCard(
                            bodyWeightKg = state.bodyWeightKg,
                            onSave = { viewModel.setBodyWeightKg(it) },
                        )
                    }
                    if (state.targets != null) {
                        item {
                            PhaseGuidanceCard(
                                phase = state.phase,
                                targets = state.targets!!,
                                bodyWeightKg = state.bodyWeightKg,
                                today = state.today,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayCard(nutrition: NutritionSummary?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Today's food", style = MaterialTheme.typography.titleMedium)
            Text("via Cronometer", style = MaterialTheme.typography.bodySmall, color = ShimmerSilverDim)
            if (nutrition == null) {
                Text(
                    "Nothing logged yet today, or it hasn't synced from Cronometer to Health Connect yet. " +
                        "Tap refresh once you've logged something.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ShimmerSilverDim,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    NutritionStat(label = "kcal", value = nutrition.kcal.roundToDisplay())
                    NutritionStat(label = "protein g", value = nutrition.proteinG.roundToDisplay())
                    NutritionStat(label = "carbs g", value = nutrition.carbsG.roundToDisplay())
                    NutritionStat(label = "fat g", value = nutrition.fatG.roundToDisplay())
                }
            }
        }
    }
}

@Composable
private fun NutritionStat(label: String, value: String) {
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall, color = ShimmerSilverDim)
    }
}

@Composable
private fun BodyWeightCard(bodyWeightKg: Double?, onSave: (Double?) -> Unit, modifier: Modifier = Modifier) {
    var editing by remember(bodyWeightKg) { mutableStateOf(bodyWeightKg == null) }
    var input by remember(bodyWeightKg) { mutableStateOf(bodyWeightKg?.toString() ?: "") }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Body weight", style = MaterialTheme.typography.titleMedium)
            Text(
                "Only used to scale the g/kg guidance below to you. Optional — nothing else in the app uses it.",
                style = MaterialTheme.typography.bodySmall,
                color = ShimmerSilverDim,
            )
            if (editing) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text("kg") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        val parsed = input.toDoubleOrNull()
                        onSave(parsed)
                        editing = false
                    }) { Text("Save") }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text("${bodyWeightKg?.let { formatKg(it) }} kg", style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { editing = true }) { Text("Edit") }
                }
            }
        }
    }
}

@Composable
private fun PhaseGuidanceCard(
    phase: TrainingPhase?,
    targets: NutritionTargets,
    bodyWeightKg: Double?,
    today: NutritionSummary?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("This week's guidance", style = MaterialTheme.typography.titleMedium, color = RoyalPurple)
            Text(phaseIntro(phase, targets), style = MaterialTheme.typography.bodyMedium)

            if (bodyWeightKg != null) {
                val carbTarget = targets.carbGramsFor(bodyWeightKg).roundToDisplay()
                val proteinTarget = targets.proteinGramsFor(bodyWeightKg).roundToDisplay()
                Text(
                    "Carbs: this phase works out to about ${carbTarget}g a day for you" +
                        (today?.let { " — today so far: ${it.carbsG.roundToDisplay()}g" } ?: "") + ".",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Protein: roughly ${proteinTarget}g a day, spread across meals — a post-session " +
                        "snack with ~20g of protein is the single most consistent recovery habit.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    "Add your body weight above for these turned into an actual gram target — for now, " +
                        "roughly ${formatKg(targets.carbGramsPerKg)}g of carbs and ${formatKg(targets.proteinGramsPerKg)}g of " +
                        "protein per kg of body weight, per day.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Text(
                "These are ranges to inform you, not a scoreboard — no pressure to land on an exact number.",
                style = MaterialTheme.typography.bodySmall,
                color = ShimmerSilverDim,
            )
        }
    }
}

private fun phaseIntro(phase: TrainingPhase?, targets: NutritionTargets): String = when {
    targets.isCarbLoadingWindow ->
        "You're in the carb-loading window before race day — a moderate boost for a day or two, not a multi-day mega-load."
    phase == TrainingPhase.BASE ->
        "You're in Base phase — easy, foundational running. No need to carb-load or fuel mid-run at this stage."
    phase == TrainingPhase.BUILD ->
        "You're in Build phase — long runs are pushing past an hour, so carbs earn a bit more focus."
    phase == TrainingPhase.PEAK ->
        "Peak week — genuine endurance-nutrition territory. Worth treating your long run as a rehearsal for race-day fuelling."
    phase == TrainingPhase.TAPER ->
        "Taper week — training's easing off. This is about eating well and letting your legs recover, not restricting."
    else -> "Here's this phase's guidance."
}

private fun Double.roundToDisplay(): String = kotlin.math.round(this).toInt().toString()

private fun formatKg(value: Double): String = if (value == value.toInt().toDouble()) value.toInt().toString() else value.toString()
