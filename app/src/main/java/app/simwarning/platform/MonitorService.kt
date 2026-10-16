package app.simwarning.platform

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.*
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.*
import android.provider.Settings
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.simwarning.core.*
import java.io.FileDescriptor
import java.io.PrintWriter

class MonitorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val engine = MonitorEngine()
    private var connectivity = Connectivity.UNKNOWN
    private lateinit var notifications: NotificationCenter
    private var observer: CellularObserver? = null
    private var receiverRegistered = false
    private var started = false
    private var episodeKind: WarningKind? = null
    private var episodeSince: Long? = null
    private var nextDeadline: Long? = null
    private var visibleWarning: WarningKind? = null
    private var lastWarningPostElapsed: Long? = null
    private var lastLanguage: AppLanguage? = null
    private val due = Runnable { refresh() }
    private val alarm by lazy { getSystemService(AlarmManager::class.java) }
    private val alarmIntent by lazy { PendingIntent.getBroadcast(this, 0, Intent(this, DeadlineReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }
    private val changes = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_AIRPLANE_MODE_CHANGED) {
                observer?.close(); observer = null; connectivity = Connectivity.UNKNOWN
                if (!airplaneMode()) startObserver()
            }
            refresh()
        }
    }
    override fun onCreate() {
        super.onCreate()
        notifications = NotificationCenter(this)
        val settings = AppPreferences(this).read()
        if (!settings.setupComplete || !settings.monitoringEnabled || !AndroidAccess(this).ready()) { stopSelf(); return }
        try {
            ServiceCompat.startForeground(this, NotificationCenter.SERVICE_ID, notifications.serviceNotification(),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
            started = true
            MonitorRuntime.service = this; MonitorRuntime.running = true; MonitorRuntime.starting = false
            val filter = IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED).apply {
                addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
                addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
                addAction("android.app.action.NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED")
            }
            ContextCompat.registerReceiver(this, changes, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
            if (!airplaneMode()) startObserver()
            refresh()
        } catch (_: RuntimeException) { stopSelf() }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (started) refresh() else stopSelf()
        return if (AppPreferences(this).read().monitoringEnabled && AndroidAccess(this).ready()) START_STICKY else START_NOT_STICKY
    }
    fun refresh() {
        if (!started) return
        val settings = AppPreferences(this).read()
        val access = AndroidAccess(this).ready()
        val airplane = airplaneMode()
        val languageChanged = lastLanguage != settings.language
        lastLanguage = settings.language
        if (languageChanged) {
            notifications.createChannels()
            getSystemService(NotificationManager::class.java).notify(NotificationCenter.SERVICE_ID, notifications.serviceNotification())
        }
        val kind = if (connectivity == Connectivity.DISCONNECTED) WarningKind.CONNECTION_LOSS else WarningKind.STATUS_UNAVAILABLE
        val now = SystemClock.elapsedRealtime()
        if (!settings.monitoringEnabled || !access || airplane) { episodeKind = null; episodeSince = null }
        else if (connectivity != Connectivity.CONNECTED && episodeKind != kind) { episodeKind = kind; episodeSince = now }
        val result = engine.update(now, MonitorInput(connectivity, settings.monitoringEnabled, access, airplaneMode = airplane,
            intervalMinutes = settings.intervalMinutes, soundPolicy = notifications.policy(kind)))
        MonitorRuntime.status = result.status
        MonitorRuntime.outageSinceElapsedMs = if (episodeKind == WarningKind.CONNECTION_LOSS && result.status in listOf(MonitorStatus.OUTAGE, MonitorStatus.RECOVERING)) episodeSince else null
        visibleWarning = result.visibleWarning
        nextDeadline = result.nextDeadlineMs
        if (result.status == MonitorStatus.CONNECTED) { episodeKind = null; episodeSince = null }
        val alerts = result.effects.filterIsInstance<MonitorEffect.Alert>()
        result.effects.filterIsInstance<MonitorEffect.ClearWarning>().forEach { notifications.clearWarning() }
        val shown = result.effects.filterIsInstance<MonitorEffect.ShowWarning>()
        if (alerts.isNotEmpty()) alerts.forEach { if (notifications.postWarning(it.kind, it, episodeSince)) lastWarningPostElapsed = now }
        else if (shown.isNotEmpty()) shown.forEach { if (notifications.postWarning(it.kind, sinceElapsed = episodeSince)) lastWarningPostElapsed = now }
        else if (languageChanged) result.visibleWarning?.let { notifications.postWarning(it, sinceElapsed = episodeSince) }
        handler.removeCallbacks(due); alarm.cancel(alarmIntent)
        result.nextDeadlineMs?.let { deadline ->
            handler.postDelayed(due, (deadline - now).coerceAtLeast(1))
            // Standard inexact, idle-allowed wakeup; Android may delay this. No exact-alarm access.
            alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, alarmIntent)
        }
        if (!settings.monitoringEnabled || !access) stopSelf()
    }
    private fun airplaneMode() = Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    private fun startObserver() { observer = CellularObserver(this) { connectivity = it; refresh() }.also { it.start() } }
    fun dismissed() { engine.dismissWarning(); visibleWarning = null }
    override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<out String>?) {
        writer.println("running=$started")
        writer.println("connectivity=$connectivity")
        writer.println("status=${MonitorRuntime.status}")
        writer.println("desiredEnabled=${AppPreferences(this).read().monitoringEnabled}")
        writer.println("nextDeadlineElapsedMs=$nextDeadline")
        writer.println("episodeSinceElapsedMs=$episodeSince")
        writer.println("visibleWarning=$visibleWarning")
        writer.println("lastWarningPostElapsedMs=$lastWarningPostElapsed")
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null); alarm.cancel(alarmIntent)
        observer?.close()
        if (receiverRegistered) unregisterReceiver(changes)
        if (::notifications.isInitialized) { notifications.stopPreview(); notifications.clearLive() }
        MonitorRuntime.service = null; MonitorRuntime.running = false; MonitorRuntime.starting = false
        MonitorRuntime.outageSinceElapsedMs = null
        if (!AppPreferences(this).read().monitoringEnabled) MonitorRuntime.status = MonitorStatus.OFF
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}

class DeadlineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { MonitorRuntime.service?.refresh() }
}
class WarningDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { MonitorRuntime.service?.dismissed() }
}
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        AppController(AppPreferences(context), AndroidMonitorPlatform(context)).resumeIfEnabled()
    }
}
