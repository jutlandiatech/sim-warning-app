package app.simwarning.platform

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.app.NotificationManager
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat

class AndroidAccess(private val context: Context) {
    fun phoneAllowed() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    fun notificationsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return context.getSystemService(NotificationManager::class.java).getNotificationChannel("warning_visual_v1")?.importance != NotificationManager.IMPORTANCE_NONE
    }
    fun requiredRuntimePermissions(): Array<String> = buildList {
        if (!phoneAllowed()) add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
    fun ready(): Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) && phoneAllowed() && notificationsAllowed()
}
