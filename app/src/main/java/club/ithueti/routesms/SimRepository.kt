package club.ithueti.routesms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

object SimRepository {
    fun refresh(context: Context): Result<List<RouteRecord>> = runCatching {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            "Нет разрешения на чтение состояния телефона"
        }
        val manager = context.getSystemService(SubscriptionManager::class.java)
            ?: error("Служба SIM недоступна")
        val subscriptions = manager.activeSubscriptionInfoList.orEmpty()
        MappingStore.markAllSimsInactive(context)
        val now = System.currentTimeMillis()

        subscriptions.map { info ->
            val subId = info.subscriptionId
            val number = try {
                if (Build.VERSION.SDK_INT >= 33) manager.getPhoneNumber(subId)
                else @Suppress("DEPRECATION") info.number.orEmpty()
            } catch (_: SecurityException) {
                @Suppress("DEPRECATION") info.number.orEmpty()
            }
            RouteRecord(
                id = "sim_$subId",
                kind = RouteKind.SIM,
                subscriptionId = subId,
                phoneNumber = number.trim(),
                slotIndex = info.simSlotIndex.takeIf { it >= 0 },
                carrierName = info.carrierName?.toString().orEmpty(),
                active = true,
                lastSeenAt = now
            ).also { MappingStore.mergeDiscoveredSim(context, it) }
        }
        MappingStore.allRoutes(context)
    }
}
