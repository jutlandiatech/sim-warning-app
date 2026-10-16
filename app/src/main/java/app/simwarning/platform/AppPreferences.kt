package app.simwarning.platform

import android.content.Context
import app.simwarning.core.*
import java.util.Locale

class AppPreferences(context: Context) : SettingsStore {
    val preferences = context.applicationContext.getSharedPreferences("monitor_settings", Context.MODE_PRIVATE)
    override fun read(): AppSettings {
        val language = AppLanguage.forDevice(preferences.getString("language", Locale.getDefault().toLanguageTag()).orEmpty())
        val interval = preferences.getInt("interval", 2).takeIf { it in listOf(2,4,6,8,10) } ?: 2
        return AppSettings(language, interval, preferences.getBoolean("setup", false), preferences.getBoolean("enabled", false))
    }
    override fun write(settings: AppSettings) {
        check(preferences.edit().putString("language", settings.language.code).putInt("interval", settings.intervalMinutes)
            .putBoolean("setup", settings.setupComplete).putBoolean("enabled", settings.monitoringEnabled).commit()) { "Could not persist app settings" }
    }
}
