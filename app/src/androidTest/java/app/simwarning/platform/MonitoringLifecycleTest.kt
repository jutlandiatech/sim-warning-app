package app.simwarning.platform

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.simwarning.MainActivity
import app.simwarning.core.*
import org.junit.*
import org.junit.Assert.*

/** Emulator-only platform verification; no fake observations/timing switches in the app. */
class MonitoringLifecycleTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val context get() = ui.activity
    private fun prepare(): AppController {
        Assume.assumeTrue(Build.HARDWARE.contains("ranchu") || Build.FINGERPRINT.contains("generic"))
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(context.packageName, Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= 33) automation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val controller = AppController(AppPreferences(context), AndroidMonitorPlatform(context))
        ui.runOnIdle {
            AppPreferences(context).write(AppSettings(AppLanguage.ENGLISH, 2, true, false))
            assertTrue(AndroidAccess(context).ready())
        }
        return controller
    }
    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        }
    }
    @After fun cleanup() {
        if (!(Build.HARDWARE.contains("ranchu") || Build.FINGERPRINT.contains("generic"))) return
        shell("cmd connectivity airplane-mode disable")
        ui.runOnIdle { AppController(AppPreferences(context), AndroidMonitorPlatform(context)).setMonitoring(false) }
        ui.waitUntil(5000) { !MonitorRuntime.running && !MonitorRuntime.starting }
        context.getSystemService(NotificationManager::class.java).cancel(NotificationCenter.SAMPLE_ID)
    }
    @Test fun F04_serviceStartsAsForegroundAndStopsWithSwitch() {
        val controller = prepare()
        ui.runOnIdle { assertTrue(controller.setMonitoring(true)) }
        ui.waitUntil(8000) { MonitorRuntime.running }
        val manager = context.getSystemService(NotificationManager::class.java)
        ui.waitUntil(5000) { manager.activeNotifications.any { it.id == NotificationCenter.SERVICE_ID } }
        val notification = manager.activeNotifications.single { it.id == NotificationCenter.SERVICE_ID }.notification
        assertTrue(notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0)
        assertNull(manager.getNotificationChannel(notification.channelId).sound)
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressHome()
        assertTrue(MonitorRuntime.running)
        ui.runOnIdle { assertTrue(controller.setMonitoring(false)) }
        ui.waitUntil(5000) { !MonitorRuntime.running }
        assertFalse(manager.activeNotifications.any { it.id == NotificationCenter.SERVICE_ID || it.id == NotificationCenter.WARNING_ID })
    }
    @Test fun A09_airplaneModeSuppressesAndReturnKeepsMonitoringEnabled() {
        val controller = prepare()
        ui.runOnIdle { assertTrue(controller.setMonitoring(true)) }
        ui.waitUntil(8000) { MonitorRuntime.running }
        shell("cmd connectivity airplane-mode enable")
        ui.waitUntil(5000) { MonitorRuntime.status == MonitorStatus.INTENTIONALLY_DISCONNECTED }
        assertFalse(context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == NotificationCenter.WARNING_ID })
        shell("cmd connectivity airplane-mode disable")
        ui.waitUntil(5000) { MonitorRuntime.status != MonitorStatus.INTENTIONALLY_DISCONNECTED }
        assertTrue(AppPreferences(context).read().monitoringEnabled)
    }
    @Test fun S07_sampleDoesNotOverwriteForegroundOrLiveState() {
        val controller = prepare()
        ui.runOnIdle { assertTrue(controller.setMonitoring(true)) }
        ui.waitUntil(8000) { MonitorRuntime.running }
        ui.runOnIdle {
            val status = MonitorRuntime.status
            assertTrue(NotificationCenter(context).sample(WarningKind.STATUS_UNAVAILABLE))
            assertEquals(status, MonitorRuntime.status)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        ui.waitUntil(5000) { manager.activeNotifications.any { it.id == NotificationCenter.SERVICE_ID } && manager.activeNotifications.any { it.id == NotificationCenter.SAMPLE_ID } }
        val notifications = manager.activeNotifications
        assertTrue(notifications.any { it.id == NotificationCenter.SERVICE_ID })
        val sample = notifications.single { it.id == NotificationCenter.SAMPLE_ID }
        assertTrue(sample.notification.extras.getString(Notification.EXTRA_TITLE).orEmpty().startsWith("Test warning:"))
        assertFalse(notifications.any { it.id == NotificationCenter.WARNING_ID })
    }
    @Test fun R18_bootReceiverNeverEnablesSavedOffPreference() {
        prepare()
        ui.runOnIdle { BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED)) }
        assertFalse(MonitorRuntime.running || MonitorRuntime.starting)
        assertFalse(AppPreferences(context).read().monitoringEnabled)
    }
}
