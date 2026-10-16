package app.simwarning.platform

import android.content.Context
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import app.simwarning.MainActivity
import app.simwarning.R
import app.simwarning.core.*
import app.simwarning.setup.TextCatalog

class NotificationCenter(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private var preview: Ringtone? = null
    private val handler = Handler(Looper.getMainLooper())
    private val stopPreview = Runnable { stopPreview() }
    private fun texts() = TextCatalog.load(context, AppPreferences(context).read().language)
    init { createChannels() }

    fun createChannels() {
        val t = texts()
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        fun channel(id: String, name: String, importance: Int, kind: WarningKind? = null) {
            val channel = NotificationChannel(id, name, importance)
            channel.setSound(if (kind != null) soundUri(kind) else null, attributes)
            channel.enableVibration(kind != null)
            if (kind != null) channel.vibrationPattern = vibration(kind)
            channel.setBypassDnd(false)
            manager.createNotificationChannel(channel) // Existing user choices are retained by Android.
        }
        channel("monitoring_v1", t.text("channel.monitoring"), NotificationManager.IMPORTANCE_LOW)
        channel("warning_visual_v1", t.text("channel.visual"), NotificationManager.IMPORTANCE_DEFAULT)
        for (kind in WarningKind.entries) {
            val title = t.text(if (kind == WarningKind.CONNECTION_LOSS) "channel.loss" else "channel.unknown")
            channel(alertChannel(kind), title, NotificationManager.IMPORTANCE_DEFAULT, kind)
        }
    }
    fun policy(kind: WarningKind): SoundPolicy {
        val mode = when (audio.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> RingerMode.SILENT
            AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
            else -> RingerMode.NORMAL
        }
        val channel = manager.getNotificationChannel(alertChannel(kind))
        // No DND access or bypass is requested. Be quiet when its state is unknown/filtered;
        // Android also enforces the actual notification-channel and interruption rules.
        val dndAllows = manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
        val importanceAllows = channel != null && channel.importance >= NotificationManager.IMPORTANCE_DEFAULT
        return SoundPolicy(mode, dndAllows, dndAllows, importanceAllows && channel.sound != null, importanceAllows && channel.shouldVibrate())
    }
    private fun openApp() = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun serviceNotification(): Notification {
        val t = texts()
        return NotificationCompat.Builder(context, "monitoring_v1").setSmallIcon(R.drawable.ic_stat_sim)
            .setContentTitle(t.text("service.title")).setContentText(t.text("service.body"))
            .setContentIntent(openApp()).setOngoing(true).setSilent(true).setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_SERVICE).build()
    }
    fun postWarning(kind: WarningKind, alert: MonitorEffect.Alert? = null, sinceElapsed: Long? = null, sample: Boolean = false): Boolean {
        if (!AndroidAccess(context).notificationsAllowed()) return false
        val t = texts()
        val channelId = when {
            alert?.sound == true -> alertChannel(kind)
            alert?.vibration == true -> alertChannel(kind) // Android's ringer mode suppresses sound in vibrate mode.
            else -> "warning_visual_v1"
        }
        val prefix = if (kind == WarningKind.CONNECTION_LOSS) "warning.loss" else "warning.unknown"
        val title = if (sample) "${t.text("warning.sample")}: ${t.text("$prefix.title")}" else t.text("$prefix.title")
        val body = t.text(if (sample) "warning.sampleBody" else "$prefix.body")
        val builder = NotificationCompat.Builder(context, channelId).setSmallIcon(R.drawable.ic_stat_sim)
            .setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp()).setAutoCancel(false).setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (alert == null || (!alert.sound && !alert.vibration)) builder.setSilent(true)
        if (!sample) {
            builder.setDeleteIntent(PendingIntent.getBroadcast(context, 1, Intent(context, WarningDismissReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            sinceElapsed?.let { builder.setWhen(System.currentTimeMillis() - (android.os.SystemClock.elapsedRealtime() - it)).setUsesChronometer(true).setShowWhen(true) }
        }
        return try { manager.notify(if (sample) SAMPLE_ID else WARNING_ID, builder.build()); true } catch (_: SecurityException) { false }
    }
    fun sample(kind: WarningKind): Boolean {
        val p = policy(kind)
        val sound = p.ringerMode == RingerMode.NORMAL && p.dndAllowsSound && p.channelAllowsSound
        val vibration = p.ringerMode != RingerMode.SILENT && p.dndAllowsVibration && p.channelAllowsVibration
        return postWarning(kind, MonitorEffect.Alert(kind, sound, vibration), sample = true)
    }
    fun playPreview(kind: WarningKind): Boolean {
        stopPreview()
        val p = policy(kind)
        if (p.ringerMode != RingerMode.NORMAL || !p.dndAllowsSound || !p.channelAllowsSound) return false
        return try {
            preview = RingtoneManager.getRingtone(context, soundUri(kind))?.apply {
                audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                play()
            }
            handler.postDelayed(stopPreview, 3000)
            preview != null
        } catch (_: RuntimeException) { stopPreview(); false }
    }
    fun stopPreview() { handler.removeCallbacks(stopPreview); preview?.stop(); preview = null }
    fun clearWarning() { manager.cancel(WARNING_ID) }
    fun clearLive() { manager.cancel(SERVICE_ID); clearWarning() }
    private fun soundUri(kind: WarningKind): Uri {
        val resource = if (kind == WarningKind.CONNECTION_LOSS) R.raw.connection_loss else R.raw.status_unavailable
        return Uri.parse("android.resource://${context.packageName}/raw/${context.resources.getResourceEntryName(resource)}")
    }
    private fun alertChannel(kind: WarningKind) = if (kind == WarningKind.CONNECTION_LOSS) "loss_alert_v1" else "unknown_alert_v1"
    private fun vibration(kind: WarningKind) = if (kind == WarningKind.CONNECTION_LOSS) longArrayOf(0,180,140,180,140,180) else longArrayOf(0,180,140,180)
    companion object { const val SERVICE_ID = 1001; const val WARNING_ID = 1002; const val SAMPLE_ID = 2001 }
}
