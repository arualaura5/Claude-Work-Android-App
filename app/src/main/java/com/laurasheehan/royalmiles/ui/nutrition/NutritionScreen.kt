package com.laurasheehan.royalmiles.ui.nutrition

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
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
    var showInfo by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text("This week's guidance", style = MaterialTheme.typography.titleMedium, color = RoyalPurple)
                IconButton(onClick = { showInfo = true }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Info, contentDescription = "About these ratios", tint = ShimmerSilverDim)
                }
            }
            Text(phaseIntro(phase, targets), style = MaterialTheme.typography.bodyMedium)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (bodyWeightKg != null) {
                    val carbTarget = targets.carbGramsFor(bodyWeightKg).roundToDisplay()
                    val proteinTarget = targets.proteinGramsFor(bodyWeightKg).roundToDisplay()
                    MacroBullet(label = "Carbs", value = "~${carbTarget}g/day")
                    today?.let {
                        Text(
                            "today so far: ${it.carbsG.roundToDisplay()}g",
                            style = MaterialTheme.typography.bodySmall,
                            color = ShimmerSilverDim,
                            modifier = Modifier.padding(start = 20.dp),
                        )
                    }
                    MacroBullet(label = "Protein", value = "~${proteinTarget}g/day")
                } else {
                    MacroBullet(label = "Carbs", value = "${formatKg(targets.carbGramsPerKg)} g/kg/day")
                    MacroBullet(label = "Protein", value = "${formatKg(targets.proteinGramsPerKg)} g/kg/day")
                    Text(
                        "Add body weight above for a gram target.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ShimmerSilverDim,
                        modifier = Modifier.padding(start = 20.dp, top = 2.dp),
                    )
                }
            }

            Text(
                "Ranges to inform you, not a scoreboard.",
                style = MaterialTheme.typography.bodySmall,
                color = ShimmerSilverDim,
            )
        }
    }

    if (showInfo) {
        NutritionRatioInfoDialog(onDismiss = { showInfo = false })
    }
}

@Composable
private fun MacroBullet(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("•  $label", style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NutritionRatioInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ratios, per kg body weight") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RatioBlock(phase = "Base", carbs = "4.0", protein = "1.3")
                RatioBlock(phase = "Build", carbs = "6.5", protein = "1.4")
                RatioBlock(phase = "Peak", carbs = "8.0", protein = "1.6")
                RatioBlock(phase = "Taper", carbs = "5.0", protein = "1.4")
                RatioBlock(phase = "Carb-loading (1–2 days pre-race)", carbs = "10.0", protein = "1.4")
                Text(
                    "From ACSM/IOC/ISSN sport-nutrition consensus guidelines. A post-session snack with " +
                        "~20g protein is the single most consistent recovery habit across all phases.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ShimmerSilverDim,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        },
    )
}

@Composable
private fun RatioBlock(phase: String, carbs: String, protein: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(phase, style = MaterialTheme.typography.labelLarge, color = RoyalPurple)
        Text("•  Carbs — $carbs g/kg/day", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
        Text("•  Protein — $protein g/kg/day", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
    }
}

private fun phaseIntro(phase: TrainingPhase?, targets: NutritionTargets): String = when {
    targets.isCarbLoadingWindow -> "Carb-loading window — a short boost before race day."
    phase == TrainingPhase.BASE -> "Base phase — easy running, no fuelling focus needed yet."
    phase == TrainingPhase.BUILD -> "Build phase — long runs are earning more carbs."
    phase == TrainingPhase.PEAK -> "Peak week — treat this as race-day fuelling practice."
    phase == TrainingPhase.TAPER -> "Taper — ease off, eat well, let your legs recover."
    else -> "This phase's guidance."
}

private fun Double.roundToDisplay(): String = kotlin.math.round(this).toInt().toString()

private fun formatKg(value: Double): String = if (value == value.toInt().toDouble()) value.toInt().toString() else value.toString()
