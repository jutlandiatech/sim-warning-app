package app.simwarning.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.simwarning.core.MonitorStatus

/** Actual process lifecycle, separate from the persisted enabled preference. Main-thread owned. */
object MonitorRuntime {
    var running by mutableStateOf(false)
        internal set
    var starting by mutableStateOf(false)
        internal set
    var status by mutableStateOf(MonitorStatus.OFF)
        internal set
    var outageSinceElapsedMs by mutableStateOf<Long?>(null)
        internal set
    internal var service: MonitorService? = null
}
