package club.ithueti.routesms

import android.content.Context

object HealthSettings {
    const val DEFAULT_INTERVAL_HOURS = 12
    const val MIN_INTERVAL_HOURS = 0
    const val MAX_INTERVAL_HOURS = 9999

    private const val PREFS = "health_settings"
    private const val KEY_INTERVAL_HOURS = "heartbeat_interval_hours"

    fun intervalHours(context: Context): Int = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getInt(KEY_INTERVAL_HOURS, DEFAULT_INTERVAL_HOURS)
        .coerceIn(MIN_INTERVAL_HOURS, MAX_INTERVAL_HOURS)

    fun setIntervalHours(context: Context, hours: Int): Int {
        val normalized = hours.coerceIn(MIN_INTERVAL_HOURS, MAX_INTERVAL_HOURS)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_INTERVAL_HOURS, normalized)
            .apply()
        return normalized
    }
}
