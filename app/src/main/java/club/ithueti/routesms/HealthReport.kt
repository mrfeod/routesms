package club.ithueti.routesms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import java.text.DateFormat
import java.util.Date

object HealthReport {
    fun build(context: Context): String {
        val sims = MappingStore.allRoutes(context).filter { it.kind == RouteKind.SIM }
        val activeSims = sims.filter { it.active }
        val problems = buildList {
            if (!hasPermission(context, Manifest.permission.RECEIVE_SMS)) add("нет разрешения RECEIVE_SMS")
            if (!hasPermission(context, Manifest.permission.READ_PHONE_STATE)) add("нет разрешения READ_PHONE_STATE")
            if (!hasNetwork(context)) add("нет подключения к интернету")
            if (activeSims.isEmpty()) add("не обнаружено активных SIM")
            activeSims.filterNot { it.config.isConfigured }
                .forEach { add("не настроен маршрут «${it.displayName()}»") }
        }
        val lastSuccess = context.getSharedPreferences("health", Context.MODE_PRIVATE)
            .getLong("last_ok_ts", 0L)
        return buildString {
            append(if (problems.isEmpty()) "✅ Route SMS работает" else "⚠️ Route SMS: ${problems.joinToString("; ")}")
            append("\nАктивные SIM: ")
            append(activeSims.joinToString { it.displayName() }.ifBlank { "нет" })
            append("\nПоследняя успешная пересылка: ")
            append(formatTime(lastSuccess))
            append("\n\nПоследние полученные SMS:")
            if (sims.isEmpty()) append("\n• SIM ещё не обнаружены")
            else sims.forEach { append("\n• ${it.displayName()}: ${formatTime(it.lastSmsAt)}") }
        }
    }

    fun hasNetwork(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    fun simInfo(route: RouteRecord): String = buildString {
        append("📱 Route SMS — ${route.displayName()}")
        append("\nНомер: ${route.effectivePhoneNumber.ifBlank { "не определён" }}")
        append("\nСлот: ${route.slotIndex?.plus(1) ?: "—"}")
        append("\nОператор: ${route.carrierName.ifBlank { "—" }}")
        append("\nСтатус: ${if (route.active) "активна" else "неактивна"}")
        append("\nSubscription ID: ${route.subscriptionId ?: "—"}")
        append("\nПоследняя полученная SMS: ${formatTime(route.lastSmsAt)}")
    }

    private fun hasPermission(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun formatTime(timestamp: Long): String = if (timestamp == 0L) {
        "ещё не было"
    } else {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
    }
}
