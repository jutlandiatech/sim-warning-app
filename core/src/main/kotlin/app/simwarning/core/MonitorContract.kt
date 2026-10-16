package app.simwarning.core

/** Platform adapters provide observations; no Android API or wall clock enters this contract. */
enum class Connectivity { CONNECTED, DISCONNECTED, UNKNOWN }
enum class SimService { NORMAL, UNAVAILABLE, EMERGENCY_ONLY, UNKNOWN }
enum class WarningKind { CONNECTION_LOSS, STATUS_UNAVAILABLE }
enum class RingerMode { NORMAL, SILENT, VIBRATE }
enum class MonitorStatus { OFF, NEEDS_PERMISSION, INTENTIONALLY_DISCONNECTED, INITIAL_GRACE, CONNECTED, LOSS_GRACE, OUTAGE, RECOVERING, STATUS_UNAVAILABLE }

data class SoundPolicy(
    val ringerMode: RingerMode = RingerMode.NORMAL,
    val dndAllowsSound: Boolean = true,
    val dndAllowsVibration: Boolean = true,
    val channelAllowsSound: Boolean = true,
    val channelAllowsVibration: Boolean = true,
)

data class MonitorInput(
    val connectivity: Connectivity,
    val monitoringEnabled: Boolean = true,
    val permissionsGranted: Boolean = true,
    val airplaneMode: Boolean = false,
    val allSimsIntentionallyDisabled: Boolean = false,
    val intervalMinutes: Int = 2,
    val soundPolicy: SoundPolicy = SoundPolicy(),
)

sealed interface MonitorEffect {
    data class ShowWarning(val kind: WarningKind) : MonitorEffect
    data class ClearWarning(val kind: WarningKind) : MonitorEffect
    /** One scheduled alert, independent of visual posting. False/false is never emitted. */
    data class Alert(val kind: WarningKind, val sound: Boolean, val vibration: Boolean) : MonitorEffect
}

data class MonitorResult(
    val status: MonitorStatus,
    val visibleWarning: WarningKind?,
    val effects: List<MonitorEffect>,
    /** Earliest required monotonic deadline, not a request for periodic polling. */
    val nextDeadlineMs: Long?,
)

/** An empty list does not prove intentional disabling. */
fun aggregateServices(services: List<SimService>): Connectivity = when {
    SimService.NORMAL in services -> Connectivity.CONNECTED
    services.isEmpty() || SimService.UNKNOWN in services -> Connectivity.UNKNOWN
    else -> Connectivity.DISCONNECTED
}
