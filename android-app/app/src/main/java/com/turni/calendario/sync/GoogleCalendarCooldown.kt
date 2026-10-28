package com.turni.calendario.sync

import android.content.Context
import java.util.Locale

object CalendarCooldownPolicy {
    const val DURATION_MILLIS = 180_000L
    fun remaining(deadline: Long, now: Long): Long =
        if (deadline <= now || now < 0) 0 else (deadline - now).coerceAtMost(DURATION_MILLIS)
    fun deadline(now: Long) = now + DURATION_MILLIS
    fun label(remainingMillis: Long): String {
        val seconds = ((remainingMillis.coerceAtLeast(0) + 999) / 1000)
        return "%02d:%02d".format(Locale.ROOT, seconds / 60, seconds % 60)
    }
}

/** Shared by synchronization and removal, across screens/restarts, separately for each account. */
object GoogleCalendarCooldown {
    private fun preferences(context: Context) = context.getSharedPreferences("calendar_operations_v1", Context.MODE_PRIVATE)
    private fun key(email: String) = "until:" + email.trim().lowercase(Locale.ROOT)
    @Synchronized fun remaining(context: Context, email: String?): Long =
        if (email.isNullOrBlank()) 0 else CalendarCooldownPolicy.remaining(
            preferences(context).getLong(key(email), 0), System.currentTimeMillis())

    @Synchronized fun tryStart(context: Context, email: String): Boolean {
        if (email.isBlank() || remaining(context, email) > 0) return false
        return preferences(context).edit().putLong(key(email), CalendarCooldownPolicy.deadline(System.currentTimeMillis())).commit()
    }
    @Synchronized fun finished(context: Context, email: String) {
        preferences(context).edit().putLong(key(email), CalendarCooldownPolicy.deadline(System.currentTimeMillis())).commit()
    }
}
