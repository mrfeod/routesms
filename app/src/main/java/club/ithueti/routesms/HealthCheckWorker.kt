package club.ithueti.routesms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class HealthCheckWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext

        val problems = mutableListOf<String>()

        val keys = MappingStore.allKeys(ctx)
        if (keys.isEmpty()) problems += "Нет настроенных маппингов."

        val smsGranted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        if (!smsGranted) problems += "Нет разрешения RECEIVE_SMS."

        val network = hasNetwork(ctx)
        if (!network) problems += "Нет подключения к сети."

        if (network && keys.isNotEmpty()) {
            for (k in keys) {
                val cfg = MappingStore.loadMapping(ctx, k) ?: continue
                val ok = try {
                    TelegramClient(cfg.botToken).sendChatAction(cfg.chatId)
                } catch (_: Exception) { false }
                if (!ok) {
                    problems += "Бот/чат недоступны для «$k»."
                    safeReport(cfg, "⚠️ Проверь телефон с приложением: бот/чат недоступны для «$k».")
                }
            }
        }

        if (problems.isNotEmpty()) {
            val reason = problems.joinToString("\n")
            val now = System.currentTimeMillis()
            val prefs = ctx.getSharedPreferences("health", Context.MODE_PRIVATE)
            val lastReport = prefs.getLong("last_report_ts", 0L)
            val twelveHoursMs = 12L * 60 * 60 * 1000
            if (now - lastReport >= twelveHoursMs) {
                for (k in keys) {
                    val cfg = MappingStore.loadMapping(ctx, k) ?: continue
                    safeReport(cfg, "⚠️ Проверь телефон с приложением:\n$reason")
                }
                prefs.edit().putLong("last_report_ts", now).apply()
            }
        } else {
            val prefs = ctx.getSharedPreferences("health", Context.MODE_PRIVATE)
            prefs.edit().putLong("last_ok_ts", System.currentTimeMillis()).apply()
        }
        return Result.success()
    }

    private fun hasNetwork(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val net = cm?.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun safeReport(cfg: BotConfig, text: String) {
        try { TelegramClient(cfg.botToken).sendMessage(cfg.chatId, text) } catch (_: Exception) {}
    }
}
