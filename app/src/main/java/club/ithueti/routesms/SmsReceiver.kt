package club.ithueti.routesms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsMessage
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages: Array<SmsMessage> = runCatching {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        }.getOrElse { return }
        if (messages.isEmpty()) return

        val sender = messages.firstNotNullOfOrNull { it.displayOriginatingAddress } ?: "unknown"
        val text = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        val subId = extractNumber(intent.extras, subscriptionKeys())?.toInt()
        val slot = extractNumber(intent.extras, listOf("slot", "slot_id", "simSlot", "phone"))?.toInt()
        val data = Data.Builder()
            .putString(SmsForwardWorker.KEY_SENDER, sender)
            .putString(SmsForwardWorker.KEY_TEXT, text)
            .putInt(SmsForwardWorker.KEY_SUB_ID, subId ?: Int.MIN_VALUE)
            .putInt(SmsForwardWorker.KEY_SLOT, slot ?: Int.MIN_VALUE)
            .build()
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<SmsForwardWorker>().setInputData(data).build()
        )
    }

    private fun subscriptionKeys(): List<String> = listOf(
        "subscription",
        "android.telephony.extra.SUBSCRIPTION_ID",
        "subscription_id",
        "sub_id",
        "simId"
    )

    @Suppress("DEPRECATION")
    private fun extractNumber(extras: Bundle?, keys: List<String>): Long? {
        if (extras == null) return null
        for (key in keys) {
            val value = runCatching { extras.get(key) }.getOrNull()
            val number = when (value) {
                is Long -> value
                is Int -> value.toLong()
                is Short -> value.toLong()
                is String -> value.toLongOrNull()
                else -> null
            }
            if (number != null && number >= 0) return number
        }
        return null
    }
}
