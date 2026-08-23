package com.laurasheehan.royalmiles.core.plan

import com.laurasheehan.royalmiles.core.model.TrainingPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NutritionTargetsCalculatorTest {

    @Test
    fun `base phase targets are the general-activity range`() {
        val targets = NutritionTargetsCalculator.targetsFor(TrainingPhase.BASE, daysToRace = 40)

        assertEquals(4.0, targets.carbGramsPerKg)
        assertEquals(1.3, targets.proteinGramsPerKg)
        assertFalse(targets.isCarbLoadingWindow)
    }

    @Test
    fun `build phase targets step up with training load`() {
        val targets = NutritionTargetsCalculator.targetsFor(TrainingPhase.BUILD, daysToRace = 20)

        assertEquals(6.5, targets.carbGramsPerKg)
        assertEquals(1.4, targets.proteinGramsPerKg)
        assertFalse(targets.isCarbLoadingWindow)
    }

    @Test
    fun `peak phase targets are highest outside of carb-loading`() {
        val targets = NutritionTargetsCalculator.targetsFor(TrainingPhase.PEAK, daysToRace = 8)

        assertEquals(8.0, targets.carbGramsPerKg)
        assertEquals(1.6, targets.proteinGramsPerKg)
        assertFalse(targets.isCarbLoadingWindow)
    }

    @Test
    fun `taper targets are moderate outside the loading window`() {
        val targets = NutritionTargetsCalculator.targetsFor(TrainingPhase.TAPER, daysToRace = 5)

        assertEquals(5.0, targets.carbGramsPerKg)
        assertEquals(1.4, targets.proteinGramsPerKg)
        assertFalse(targets.isCarbLoadingWindow)
    }

    @Test
    fun `carb-loading window kicks in 1 to 2 days before race regardless of phase`() {
        val twoDaysOut = NutritionTargetsCalculator.targetsFor(TrainingPhase.TAPER, daysToRace = 2)
        val oneDayOut = NutritionTargetsCalculator.targetsFor(TrainingPhase.TAPER, daysToRace = 1)

        assertTrue(twoDaysOut.isCarbLoadingWindow)
        assertTrue(oneDayOut.isCarbLoadingWindow)
        assertEquals(10.0, twoDaysOut.carbGramsPerKg)
        assertEquals(10.0, oneDayOut.carbGramsPerKg)
    }

    @Test
    fun `race day itself is not treated as the loading window`() {
        val raceDay = NutritionTargetsCalculator.targetsFor(TrainingPhase.TAPER, daysToRace = 0)

        assertFalse(raceDay.isCarbLoadingWindow)
        assertEquals(5.0, raceDay.carbGramsPerKg)
    }

    @Test
    fun `gram helpers scale by body weight`() {
        val targets = NutritionTargetsCalculator.targetsFor(TrainingPhase.BUILD, daysToRace = 20)

        assertEquals(422.5, targets.carbGramsFor(bodyWeightKg = 65.0), 0.001)
        assertEquals(91.0, targets.proteinGramsFor(bodyWeightKg = 65.0), 0.001)
    }
}
