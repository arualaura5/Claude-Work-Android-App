package com.laura.royaltasks.data

/** How much a task is worth. Accent colour on the card follows the same order. */
enum class Priority(val xp: Int, val label: String) {
    LOW(5, "Low"),
    NORMAL(10, "Normal"),
    HIGH(20, "High");

    fun next(): Priority = when (this) {
        LOW -> NORMAL
        NORMAL -> HIGH
        HIGH -> LOW
    }
}

/** Tasks per day that earn the daily crown. */
const val DAILY_GOAL = 3
const val CROWN_BONUS_XP = 25

private val TITLES = listOf(
    "Page", "Squire", "Knight", "Baron", "Viscount",
    "Earl", "Duke", "Prince", "Monarch", "Legend"
)

fun titleFor(level: Int): String = TITLES[(level - 1).coerceIn(0, TITLES.lastIndex)]

/** Total XP needed to reach [level]. Each level costs 100 XP more than the last. */
fun xpToReach(level: Int): Int = 50 * level * (level - 1)

fun levelFor(xp: Int): Int {
    var level = 1
    while (xpToReach(level + 1) <= xp) level++
    return level
}

data class Progress(
    val totalXp: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastDoneEpochDay: Long? = null,
    val crowns: Int = 0,
    val lastCrownEpochDay: Long? = null
) {
    val level: Int get() = levelFor(totalXp)
    val title: String get() = titleFor(level)
    val xpIntoLevel: Int get() = totalXp - xpToReach(level)
    val xpForThisLevel: Int get() = xpToReach(level + 1) - xpToReach(level)

    /** A streak only counts while it's still alive — today or yesterday. */
    fun streakAsOf(today: Long): Int {
        val last = lastDoneEpochDay ?: return 0
        return if (last >= today - 1) currentStreak else 0
    }

    fun crownEarnedOn(today: Long): Boolean = lastCrownEpochDay == today

    /** [doneToday] includes the task being completed now. */
    fun afterCompleting(xp: Int, today: Long, doneToday: Int): Progress {
        val streak = when (lastDoneEpochDay) {
            today -> currentStreak.coerceAtLeast(1)
            today - 1 -> currentStreak + 1
            else -> 1
        }
        val earnsCrown = doneToday >= DAILY_GOAL && lastCrownEpochDay != today
        return copy(
            totalXp = totalXp + xp + if (earnsCrown) CROWN_BONUS_XP else 0,
            currentStreak = streak,
            longestStreak = maxOf(longestStreak, streak),
            lastDoneEpochDay = today,
            crowns = crowns + if (earnsCrown) 1 else 0,
            lastCrownEpochDay = if (earnsCrown) today else lastCrownEpochDay
        )
    }

    /** Un-ticking a task hands back its XP. Streaks and crowns stay earned. */
    fun afterUndoing(xp: Int): Progress = copy(totalXp = (totalXp - xp).coerceAtLeast(0))
}
