package club.ithueti.routesms

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class BotConfig(
    @SerializedName("bot_token") val botToken: String,
    @SerializedName("chat_id") val chatId: String
)

object MappingStore {
    private const val PREFS = "sms2tg_mappings"
    private val gson = Gson()

    fun saveMapping(context: Context, key: String, config: BotConfig) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(key, gson.toJson(config)).apply()
    }

    fun removeMapping(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().remove(key).apply()
    }

    fun loadMapping(context: Context, key: String): BotConfig? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val s = prefs.getString(key, null) ?: return null
        return try { gson.fromJson(s, BotConfig::class.java) } catch (e: Exception) { null }
    }

    fun allKeys(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.all.keys
    }
}
