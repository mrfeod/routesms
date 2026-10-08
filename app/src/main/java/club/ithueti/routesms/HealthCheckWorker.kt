package club.ithueti.routesms

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class HealthCheckWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val service = MappingStore.serviceRoute(context)
        if (!service.config.isConfigured) return Result.success()

        if (!HealthReport.hasNetwork(context)) return Result.retry()
        val heartbeat = HealthReport.build(context)
        return try {
            TelegramClient(service.botToken).sendMessage(service.chatId, heartbeat)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

}
