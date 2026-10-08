package club.ithueti.routesms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages: Array<SmsMessage> = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (e: Exception) {
            return
        }

        if (messages.isEmpty()) return

        val sb = StringBuilder()
        var sender: String? = null
        for (m in messages) {
            if (sender == null) sender = m.displayOriginatingAddress
            sb.append(m.messageBody)
        }
        val text = sb.toString()
        val from = sender ?: "unknown"

        val subId = extractSubscriptionId(intent.extras)

        val mappingKeysToTry = listOfNotNull(
            subId?.let { "mapping_sub_$it" },
            subId?.let { "mapping_slot_$it" },
            "mapping_default"
        )

        CoroutineScope(Dispatchers.IO).launch {
            val prefs = context.getSharedPreferences("health", Context.MODE_PRIVATE)
            for (k in mappingKeysToTry) {
                val cfg = MappingStore.loadMapping(context, k)
                if (cfg != null) {
                    try {
                        val client = TelegramClient(cfg.botToken)
                        val message = "От: $from\n$text"
                        client.sendMessage(cfg.chatId, message)
                        prefs.edit().putLong("last_ok_ts", System.currentTimeMillis()).apply()
                    } catch (_: Exception) {
                        // ignore
                    }
                    return@launch
                }
            }
        }
    }

    private fun extractSubscriptionId(extras: Bundle?): Int? {
        if (extras == null) return null

        // 1️⃣ Сначала пробуем официальные поля из SubscriptionManager (если есть)
        try {
            val cls = android.telephony.SubscriptionManager::class.java
            val EXTRA_SUBSCRIPTION_ID = try { cls.getField("EXTRA_SUBSCRIPTION_ID").get(null) as? String } catch (_: Throwable) { null }
            val EXTRA_SUBSCRIPTION_INDEX = try { cls.getField("EXTRA_SUBSCRIPTION_INDEX").get(null) as? String } catch (_: Throwable) { null }

            if (EXTRA_SUBSCRIPTION_ID != null) {
                val v = extras.getInt(EXTRA_SUBSCRIPTION_ID, -1)
                if (v >= 0) return v
            }
            if (EXTRA_SUBSCRIPTION_INDEX != null) {
                val v = extras.getInt(EXTRA_SUBSCRIPTION_INDEX, -1)
                if (v >= 0) return v
            }
        } catch (_: Throwable) {}

        // 2️⃣ OEM и старые поля, встречающиеся на разных устройствах
        val candidates = listOf(
            "android.telephony.extra.SUBSCRIPTION_ID",
            "subscription",
            "subscription_id",
            "sub_id",
            "slot",
            "slot_id",
            "simId"
        )

        for (key in candidates) {
            try {
                val any = extras.get(key)
                when (any) {
                    is Int -> if (any >= 0) return any
                    is String -> any.toIntOrNull()?.let { if (it >= 0) return it }
                }
            } catch (_: Throwable) {}
        }

        return null
    }
}
