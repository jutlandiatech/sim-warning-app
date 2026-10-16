package app.simwarning.platform

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.simwarning.MainActivity
import app.simwarning.core.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Run each method separately with clean emulator app data: actual system grant/deny dialogs. */
class RealPermissionFlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private fun review() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 33 && Build.HARDWARE.contains("ranchu"))
        assertFalse(AndroidAccess(ui.activity).phoneAllowed())
        assertFalse(AndroidAccess(ui.activity).notificationsAllowed())
        ui.onNodeWithTag("setup_language_en").performScrollTo().performClick()
        repeat(3) { ui.onNodeWithTag("setup_continue_button").performScrollTo().performClick() }
        ui.onNodeWithTag("setup_start_monitoring_button").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithTag("setup_review_permissions_button").performScrollTo().performClick()
    }
    private fun answerDialogs(allow: Boolean) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        repeat(2) {
            val id = if (allow) "permission_allow_button" else "permission_deny_button"
            val button = device.wait(Until.findObject(By.res("com.android.permissioncontroller", id)), 7000)
            assertNotNull("Expected actual Android permission dialog ($id)", button)
            button.click()
            device.waitForIdle()
        }
        ui.waitForIdle()
    }
    @Test fun U08_actualDenialLeavesSetupIncomplete() {
        review(); answerDialogs(false)
        ui.onNodeWithTag("setup_start_monitoring_button").performScrollTo().assertIsNotEnabled()
        assertFalse(AppPreferences(ui.activity).read().setupComplete)
        assertFalse(MonitorRuntime.running)
    }
    @Test fun U10_actualGrantsCompleteSetupAndStartService() {
        review(); answerDialogs(true)
        ui.onNodeWithTag("setup_start_monitoring_button").performScrollTo().assertIsEnabled().performClick()
        ui.onNodeWithTag("main_screen").assertExists()
        ui.onNodeWithTag("main_monitoring_switch").assertIsOn()
        ui.waitUntil(8000) { MonitorRuntime.running }
        val folder = File(ui.activity.getExternalFilesDir(null), "app-review").apply { mkdirs() }
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        ui.waitForIdle()
        assertTrue(device.takeScreenshot(File(folder, "main-monitoring.png")))
        ui.onNodeWithTag("main_settings_button").performScrollTo().performClick()
        ui.waitForIdle()
        assertTrue(device.takeScreenshot(File(folder, "settings.png")))
        assertTrue(AppPreferences(ui.activity).read().setupComplete)
        // Leave monitoring enabled so external ADB smoke checks can exercise the real service.
    }
}
