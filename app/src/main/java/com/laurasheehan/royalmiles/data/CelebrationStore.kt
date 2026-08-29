package com.laurasheehan.royalmiles.data

import android.content.Context

/**
 * Remembers which badges and which level have already been celebrated, so an unlock fires its
 * moment exactly once and then stops.
 *
 * Deliberately SharedPreferences rather than Room: this is per-install UI state about what has been
 * *shown*, not training data. Losing it on a reinstall costs one duplicate celebration, which is a
 * far better failure than putting a schema migration in the way of it.
 */
class CelebrationStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("celebrations", Context.MODE_PRIVATE)

    fun seenBadges(): Set<String> = prefs.getStringSet(KEY_BADGES, emptySet()).orEmpty()

    fun markBadgesSeen(names: Set<String>) {
        prefs.edit().putStringSet(KEY_BADGES, seenBadges() + names).apply()
    }

    fun seenLevel(): Int = prefs.getInt(KEY_LEVEL, 0)

    fun markLevelSeen(level: Int) {
        prefs.edit().putInt(KEY_LEVEL, maxOf(level, seenLevel())).apply()
    }

    /**
     * True the first time this is called for a given week. Used so the Sunday wrap card appears
     * once per week rather than every time the Dashboard is opened.
     */
    fun weekWrapDismissed(weekCommencing: String): Boolean =
        prefs.getString(KEY_WRAP_DISMISSED, null) == weekCommencing

    fun dismissWeekWrap(weekCommencing: String) {
        prefs.edit().putString(KEY_WRAP_DISMISSED, weekCommencing).apply()
    }

    /**
     * Seeds the store from the current state without celebrating any of it — used on first run
     * after an update so an existing history doesn't fire a dozen backdated unlocks at once.
     */
    fun primeIfUnset(badges: Set<String>, level: Int) {
        if (prefs.contains(KEY_PRIMED)) return
        prefs.edit()
            .putBoolean(KEY_PRIMED, true)
            .putStringSet(KEY_BADGES, badges)
            .putInt(KEY_LEVEL, level)
            .apply()
    }

    private companion object {
        const val KEY_BADGES = "seen_badges"
        const val KEY_LEVEL = "seen_level"
        const val KEY_WRAP_DISMISSED = "wrap_dismissed_week"
        const val KEY_PRIMED = "primed"
    }
}
