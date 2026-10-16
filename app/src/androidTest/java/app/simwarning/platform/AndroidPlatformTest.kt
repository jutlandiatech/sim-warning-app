package app.simwarning.platform

import android.app.NotificationManager
import androidx.test.platform.app.InstrumentationRegistry
import app.simwarning.core.*
import org.junit.Assert.*
import org.junit.Test

class AndroidPlatformTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun U11_preferencesSurviveNewRepositoryAndRetainOff() {
        val store = AppPreferences(context)
        val previous = store.read()
        try {
            val expected = AppSettings(AppLanguage.GERMAN, 10, true, false)
            store.write(expected)
            assertEquals(expected, AppPreferences(context).read())
        } finally { store.write(previous) }
    }
    @Test fun F07_missingPhoneAccessIsNotReady() {
        assertFalse(AndroidAccess(context).ready())
    }
    @Test fun F06_visualChannelIsSilentAndKindsHaveDistinctSoundUris() {
        NotificationCenter(context).createChannels()
        val manager = context.getSystemService(NotificationManager::class.java)
        val visual = manager.getNotificationChannel("warning_visual_v1")
        assertNull(visual.sound); assertFalse(visual.shouldVibrate())
        val loss = manager.getNotificationChannel("loss_alert_v1")
        val unknown = manager.getNotificationChannel("unknown_alert_v1")
        assertNotNull(loss.sound); assertNotEquals(loss.sound, unknown.sound)
        assertFalse(loss.canBypassDnd()); assertFalse(unknown.canBypassDnd())
    }
    @Test fun S07_deniedNotificationAccessNeverReportsSuccessfulSample() {
        assertFalse(NotificationCenter(context).sample(WarningKind.CONNECTION_LOSS))
    }
}
