package club.ithueti.routesms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class HealthCheckWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val service = MappingStore.serviceRoute(context)
        if (!service.config.isConfigured) return Result.success()

        val routes = MappingStore.allRoutes(context)
        val activeSims = routes.filter { it.kind == RouteKind.SIM && it.active }
        val problems = buildList {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
                add("нет разрешения RECEIVE_SMS")
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                add("нет разрешения READ_PHONE_STATE")
            }
            if (!hasNetwork(context)) add("нет подключения к интернету")
            if (activeSims.isEmpty()) add("не обнаружено активных SIM")
            activeSims.filterNot { it.config.isConfigured }.forEach { add("не настроен маршрут «${it.displayName()}»") }
        }

        if (!hasNetwork(context)) return Result.retry()
        val lastSuccess = context.getSharedPreferences("health", Context.MODE_PRIVATE).getLong("last_ok_ts", 0L)
        val status = if (problems.isEmpty()) "✅ Route SMS работает" else "⚠️ Route SMS: ${problems.joinToString("; ")}"
        val heartbeat = buildString {
            append(status)
            append("\nАктивные SIM: ")
            append(activeSims.joinToString { it.displayName() }.ifBlank { "нет" })
            append("\nПоследняя успешная пересылка: ")
            append(if (lastSuccess == 0L) "ещё не было" else java.text.DateFormat.getDateTimeInstance().format(lastSuccess))
        }
        return try {
            TelegramClient(service.botToken).sendMessage(service.chatId, heartbeat)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun hasNetwork(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
}
