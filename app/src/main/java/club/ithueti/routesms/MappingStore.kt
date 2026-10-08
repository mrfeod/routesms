package club.ithueti.routesms

import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class BotConfig(
    @SerializedName("bot_token") val botToken: String = "",
    @SerializedName("chat_id") val chatId: String = ""
) {
    val isConfigured: Boolean get() = botToken.isNotBlank() && chatId.isNotBlank()
}

enum class RouteKind { SIM, DEFAULT, SERVICE }

data class RouteRecord(
    val id: String,
    val kind: RouteKind,
    val subscriptionId: Int? = null,
    val phoneNumber: String = "",
    val manualPhoneNumber: String? = null,
    val slotIndex: Int? = null,
    val carrierName: String = "",
    val alias: String = "",
    val active: Boolean = false,
    val lastSeenAt: Long = 0L,
    val lastSmsAt: Long = 0L,
    val botToken: String = "",
    val chatId: String = ""
) {
    val config: BotConfig get() = BotConfig(botToken, chatId)
    val effectivePhoneNumber: String get() = manualPhoneNumber.orEmpty().ifBlank { phoneNumber }

    fun displayName(): String = when (kind) {
        RouteKind.SERVICE -> "Служебный чат"
        RouteKind.DEFAULT -> "Default"
        RouteKind.SIM -> alias.ifBlank {
            effectivePhoneNumber.ifBlank {
                carrierName.ifBlank { "SIM ${slotIndex?.plus(1) ?: "?"}" }
            }
        }
    }
}

object MappingStore {
    const val ACTION_SMS_ACTIVITY_CHANGED = "club.ithueti.routesms.SMS_ACTIVITY_CHANGED"
    private const val PREFS_V2 = "routes_v2"
    private const val LEGACY_PREFS = "sms2tg_mappings"
    const val SERVICE_ID = "service"
    const val DEFAULT_ID = "default"
    private val gson = Gson()

    fun allRoutes(context: Context): List<RouteRecord> {
        val sims = prefs(context).all.values.mapNotNull { raw ->
            (raw as? String)?.let { decode(it) }
        }.filter { it.kind == RouteKind.SIM }
            .sortedWith(compareByDescending<RouteRecord> { it.active }.thenBy { it.slotIndex ?: Int.MAX_VALUE }.thenBy { it.displayName() })
        return sims + loadRoute(context, DEFAULT_ID) + loadRoute(context, SERVICE_ID)
    }

    fun loadRoute(context: Context, id: String): RouteRecord {
        prefs(context).getString(id, null)?.let { decode(it)?.let { route -> return route } }
        return when (id) {
            SERVICE_ID -> RouteRecord(SERVICE_ID, RouteKind.SERVICE)
            DEFAULT_ID -> {
                val legacy = legacyConfig(context, "mapping_default")
                RouteRecord(DEFAULT_ID, RouteKind.DEFAULT, botToken = legacy?.botToken.orEmpty(), chatId = legacy?.chatId.orEmpty())
            }
            else -> RouteRecord(id, RouteKind.SIM)
        }
    }

    fun saveRoute(context: Context, route: RouteRecord) {
        prefs(context).edit().putString(route.id, gson.toJson(route)).apply()
    }

    fun routeForSubscription(context: Context, subscriptionId: Int?): RouteRecord? {
        if (subscriptionId == null) return null
        return allRoutes(context).firstOrNull { it.kind == RouteKind.SIM && it.subscriptionId == subscriptionId }
    }

    fun defaultRoute(context: Context): RouteRecord = loadRoute(context, DEFAULT_ID)
    fun serviceRoute(context: Context): RouteRecord = loadRoute(context, SERVICE_ID)

    fun recordSmsReceived(context: Context, subscriptionId: Int?, slotIndex: Int?, receivedAt: Long) {
        val route = allRoutes(context).firstOrNull {
            it.kind == RouteKind.SIM && (
                (subscriptionId != null && it.subscriptionId == subscriptionId) ||
                    (subscriptionId == null && slotIndex != null && it.slotIndex == slotIndex)
                )
        } ?: return
        saveRoute(context, route.copy(lastSmsAt = receivedAt))
        context.sendBroadcast(Intent(ACTION_SMS_ACTIVITY_CHANGED).setPackage(context.packageName))
    }

    fun mergeDiscoveredSim(context: Context, discovered: RouteRecord) {
        val existing = loadRoute(context, discovered.id)
        val legacy = legacyConfig(context, "mapping_sub_${discovered.subscriptionId}")
        saveRoute(
            context,
            discovered.copy(
                alias = existing.alias,
                manualPhoneNumber = existing.manualPhoneNumber,
                lastSmsAt = existing.lastSmsAt,
                botToken = existing.botToken.ifBlank { legacy?.botToken.orEmpty() },
                chatId = existing.chatId.ifBlank { legacy?.chatId.orEmpty() }
            )
        )
    }

    fun markAllSimsInactive(context: Context) {
        allRoutes(context).filter { it.kind == RouteKind.SIM && it.active }.forEach {
            saveRoute(context, it.copy(active = false, slotIndex = null))
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_V2, Context.MODE_PRIVATE)

    private fun decode(raw: String): RouteRecord? = try {
        gson.fromJson(raw, RouteRecord::class.java)
    } catch (_: Exception) {
        null
    }

    private fun legacyConfig(context: Context, key: String): BotConfig? {
        val raw = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE).getString(key, null) ?: return null
        return try { gson.fromJson(raw, BotConfig::class.java) } catch (_: Exception) { null }
    }
}
