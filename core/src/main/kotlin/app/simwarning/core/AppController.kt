package app.simwarning.core

data class AppSettings(
    val language: AppLanguage = AppLanguage.ENGLISH,
    val intervalMinutes: Int = 2,
    val setupComplete: Boolean = false,
    val monitoringEnabled: Boolean = false,
) { init { require(intervalMinutes in listOf(2, 4, 6, 8, 10)) } }

interface SettingsStore { fun read(): AppSettings; fun write(settings: AppSettings) }
interface MonitoringPlatform {
    fun ready(): Boolean
    fun running(): Boolean
    fun start(): Boolean
    fun stop()
    fun preferencesChanged()
}

class AppController(private val store: SettingsStore, private val platform: MonitoringPlatform) {
    fun finishSetup(state: SetupState): Boolean {
        val previous = store.read()
        if (previous.setupComplete) return previous.monitoringEnabled && resumeIfEnabled()
        if (!platform.ready()) return false
        store.write(AppSettings(state.language, state.intervalMinutes, true, true))
        if (platform.running() || platform.start()) return true
        store.write(previous)
        return false
    }
    fun setMonitoring(enabled: Boolean): Boolean {
        val previous = store.read()
        if (!enabled) {
            store.write(previous.copy(monitoringEnabled = false))
            platform.stop()
            return true
        }
        if (!previous.setupComplete || !platform.ready()) return false
        store.write(previous.copy(monitoringEnabled = true))
        if (platform.running() || platform.start()) return true
        store.write(previous)
        return false
    }
    fun setLanguage(language: AppLanguage) {
        store.write(store.read().copy(language = language))
        platform.preferencesChanged()
    }
    fun setInterval(minutes: Int) {
        store.write(store.read().copy(intervalMinutes = minutes))
        platform.preferencesChanged()
    }
    fun resumeIfEnabled(): Boolean {
        val settings = store.read()
        return settings.setupComplete && settings.monitoringEnabled && platform.ready() && (platform.running() || platform.start())
    }
}

/** Public cellular-registration evidence, independent of WLAN/internet connectivity. */
data class CellularEvidence(
    val cellularRegistrationAvailable: Boolean = false,
    val cellularRegisteredForVoiceOrData: Boolean = false,
    val emergencyOnly: Boolean = false,
    val legacyVoiceInService: Boolean = false,
    val legacyVoiceNetworkIsCellular: Boolean = false,
    val definitelyNoSim: Boolean = false,
)

fun classifyCellular(evidence: CellularEvidence): SimService = when {
    evidence.cellularRegisteredForVoiceOrData -> SimService.NORMAL
    evidence.definitelyNoSim -> SimService.UNAVAILABLE
    evidence.emergencyOnly -> SimService.EMERGENCY_ONLY
    evidence.cellularRegistrationAvailable -> SimService.UNAVAILABLE
    evidence.legacyVoiceInService && evidence.legacyVoiceNetworkIsCellular -> SimService.NORMAL
    else -> SimService.UNKNOWN
}
