package com.laurasheehan.royalmiles.data.coach

import android.content.Context
import android.content.SharedPreferences

interface CoachSuggestionDecisions {
    fun isDismissed(date: String): Boolean
    fun isAccepted(date: String): Boolean
    fun markDismissed(date: String)
    fun markAccepted(date: String)
}

class CoachSuggestionDecisionStore internal constructor(
    private val prefs: PreferenceAccess,
) : CoachSuggestionDecisions {

    constructor(context: Context) : this(
        SharedPreferenceAccess(
            context.applicationContext.getSharedPreferences("coach_suggestions", Context.MODE_PRIVATE),
        ),
    )

    override fun isDismissed(date: String): Boolean = date in set(KEY_DISMISSED)

    override fun isAccepted(date: String): Boolean = date in set(KEY_ACCEPTED)

    override fun markDismissed(date: String) {
        putSet(KEY_DISMISSED, set(KEY_DISMISSED) + date)
    }

    override fun markAccepted(date: String) {
        putSet(KEY_ACCEPTED, set(KEY_ACCEPTED) + date)
    }

    private fun set(key: String): Set<String> = prefs.getStringSet(key).orEmpty()

    private fun putSet(key: String, value: Set<String>) {
        prefs.putStringSet(key, value)
    }

    internal interface PreferenceAccess {
        fun getStringSet(key: String): Set<String>?
        fun putStringSet(key: String, value: Set<String>)
    }

    private class SharedPreferenceAccess(
        private val prefs: SharedPreferences,
    ) : PreferenceAccess {
        override fun getStringSet(key: String): Set<String>? = prefs.getStringSet(key, emptySet())

        override fun putStringSet(key: String, value: Set<String>) {
            prefs.edit().putStringSet(key, value).apply()
        }
    }

    private companion object {
        const val KEY_DISMISSED = "dismissed_dates"
        const val KEY_ACCEPTED = "accepted_dates"
    }
}
