package com.laurasheehan.royalmiles.core.progress

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** One "how did that feel", 1 (rough) to 5 (strong), against the day it was felt. */
data class EffortReading(val date: LocalDate, val rating: Int)

/**
 * What the recent run of effort ratings suggests.
 *
 * This is the one place the app looks for trouble rather than progress. Rebuilding from a long
 * layoff, the thing most likely to end a training block isn't losing motivation — it's picking up
 * an injury, and the earliest warning is usually a run of sessions that felt harder than they
 * should have.
 */
enum class EffortSignal {
    /** Nothing to say — too little data, or things are going fine. */
    NONE,

    /** Trending harder. Worth naming, not worth acting on yet. */
    WATCH,

    /** A sustained run of rough sessions. Worth suggesting a lighter week. */
    DIAL_BACK,
}

object EffortTrends {

    /** Older ratings say nothing about how this week is going. */
    private const val WINDOW_DAYS = 21L

    /** Below this the trend is noise, and a false alarm costs more than a missed one. */
    private const val MINIMUM_READINGS = 3

    /**
     * Assesses the recent ratings, newest last.
     *
     * Deliberately conservative. An app that cries wolf about fatigue gets ignored precisely when
     * it matters, so [DIAL_BACK] needs three consecutive rough sessions rather than an average that
     * happens to dip.
     */
    fun assess(readings: List<EffortReading>, asOf: LocalDate = LocalDate.now()): EffortSignal {
        val recent = readings
            .filter { ChronoUnit.DAYS.between(it.date, asOf) in 0..WINDOW_DAYS }
            .sortedBy { it.date }
        if (recent.size < MINIMUM_READINGS) return EffortSignal.NONE

        val last3 = recent.takeLast(3).map { it.rating }
        if (last3.all { it <= 2 }) return EffortSignal.DIAL_BACK

        val last3Average = last3.average()
        if (last3Average <= 2.5) return EffortSignal.WATCH

        // A clear drop against the three before it, even if the absolute level is still moderate.
        val previous3 = recent.dropLast(3).takeLast(3).map { it.rating }
        if (previous3.size == 3 && previous3.average() - last3Average >= 1.0) return EffortSignal.WATCH

        return EffortSignal.NONE
    }
}
