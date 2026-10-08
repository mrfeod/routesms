package club.ithueti.routesms

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SmsForwardWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val sender = inputData.getString(KEY_SENDER) ?: return Result.failure()
        val text = inputData.getString(KEY_TEXT) ?: return Result.failure()
        val rawSubId = inputData.getInt(KEY_SUB_ID, Int.MIN_VALUE)
        val rawSlot = inputData.getInt(KEY_SLOT, Int.MIN_VALUE)
        val subId = rawSubId.takeUnless { it == Int.MIN_VALUE }
        val slot = rawSlot.takeUnless { it == Int.MIN_VALUE }

        val simRoute = MappingStore.routeForSubscription(applicationContext, subId)
        val route = simRoute?.takeIf { it.config.isConfigured }
            ?: MappingStore.defaultRoute(applicationContext).takeIf { it.config.isConfigured }

        if (route == null) {
            reportService("Ошибка маршрутизации SMS от $sender: subscriptionId=${subId ?: "не определён"}, слот=${slot?.plus(1) ?: "не определён"}; Default не настроен.")
            return Result.failure()
        }

        return try {
            TelegramClient(route.botToken).sendMessage(route.chatId, "От: $sender\n$text")
            applicationContext.getSharedPreferences("health", Context.MODE_PRIVATE)
                .edit().putLong("last_ok_ts", System.currentTimeMillis()).apply()
            if (route.kind == RouteKind.DEFAULT) {
                reportService("Использован Default для SMS от $sender: subscriptionId=${subId ?: "не определён"}, слот=${slot?.plus(1) ?: "не определён"}.")
            }
            Result.success()
        } catch (error: Exception) {
            reportService("Не удалось переслать SMS через «${route.displayName()}»: ${error.message ?: "неизвестная ошибка"}")
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

    private suspend fun reportService(message: String) {
        val service = MappingStore.serviceRoute(applicationContext)
        if (!service.config.isConfigured) return
        runCatching { TelegramClient(service.botToken).sendMessage(service.chatId, "⚠️ $message") }
    }

    companion object {
        const val KEY_SENDER = "sender"
        const val KEY_TEXT = "text"
        const val KEY_SUB_ID = "subscription_id"
        const val KEY_SLOT = "slot"
    }
}
