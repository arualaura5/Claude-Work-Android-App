package com.laurasheehan.royalmiles.core.plan

import com.laurasheehan.royalmiles.core.model.TrainingPhase

/**
 * Phase-scaled daily nutrition guidance, in grams per kilogram of body weight. Sourced from the
 * ACSM/IOC/ISSN consensus ranges summarised in NUTRITION_GUIDELINES.md — one representative value
 * per phase rather than a full range, so the app can show a single supportive number instead of
 * asking the user to interpret a range themselves.
 *
 * Deliberately carb/protein only: no calorie or fat gram target, so nothing here reads as a
 * "calories remaining" scoreboard.
 */
data class NutritionTargets(
    val phase: TrainingPhase,
    val carbGramsPerKg: Double,
    val proteinGramsPerKg: Double,
    /** True on the 24-48h carb-loading window before race day. */
    val isCarbLoadingWindow: Boolean,
) {
    fun carbGramsFor(bodyWeightKg: Double): Double = carbGramsPerKg * bodyWeightKg

    fun proteinGramsFor(bodyWeightKg: Double): Double = proteinGramsPerKg * bodyWeightKg
}

object NutritionTargetsCalculator {

    fun targetsFor(phase: TrainingPhase, daysToRace: Long): NutritionTargets {
        if (daysToRace in 1..2) {
            return NutritionTargets(phase, carbGramsPerKg = 10.0, proteinGramsPerKg = 1.4, isCarbLoadingWindow = true)
        }
        val (carbGramsPerKg, proteinGramsPerKg) = when (phase) {
            TrainingPhase.BASE -> 4.0 to 1.3
            TrainingPhase.BUILD -> 6.5 to 1.4
            TrainingPhase.PEAK -> 8.0 to 1.6
            TrainingPhase.TAPER -> 5.0 to 1.4
        }
        return NutritionTargets(phase, carbGramsPerKg, proteinGramsPerKg, isCarbLoadingWindow = false)
    }
}
