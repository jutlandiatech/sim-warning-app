package app.simwarning.platform

import android.content.Context
import app.simwarning.core.MonitoringPlatform
import android.content.Intent
import androidx.core.content.ContextCompat

class AndroidMonitorPlatform(context: Context) : MonitoringPlatform {
    private val context = context.applicationContext
    override fun ready() = AndroidAccess(context).ready()
    override fun running() = MonitorRuntime.running || MonitorRuntime.starting
    override fun start(): Boolean {
        if (!ready()) return false
        if (running()) return true
        return try {
            MonitorRuntime.starting = true
            ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
            true
        } catch (_: RuntimeException) { MonitorRuntime.starting = false; false }
    }
    override fun stop() {
        MonitorRuntime.starting = false
        context.stopService(Intent(context, MonitorService::class.java))
        NotificationCenter(context).clearLive()
    }
    override fun preferencesChanged() { MonitorRuntime.service?.refresh() }
}
