package app.simwarning.core

import org.junit.Assert.*
import org.junit.Test

class AppControllerTest {
    private class Store(var value: AppSettings = AppSettings()) : SettingsStore {
        override fun read() = value
        override fun write(settings: AppSettings) { value = settings }
    }
    private class Platform : MonitoringPlatform {
        var allowed = true; var active = false; var accepted = true
        var starts = 0; var stops = 0; var changes = 0
        override fun ready() = allowed
        override fun running() = active
        override fun start(): Boolean { starts++; active = accepted; return accepted }
        override fun stop() { stops++; active = false }
        override fun preferencesChanged() { changes++ }
    }
    private val store = Store()
    private val platform = Platform()
    private val controller = AppController(store, platform)

    @Test fun `U09 missing access cannot complete setup`() {
        platform.allowed = false
        assertFalse(controller.finishSetup(SetupState()))
        assertFalse(store.value.setupComplete); assertEquals(0, platform.starts)
    }
    @Test fun `U10 successful setup persists choices and starts once`() {
        assertTrue(controller.finishSetup(SetupState(AppLanguage.DANISH, 8)))
        assertTrue(controller.finishSetup(SetupState(AppLanguage.DANISH, 8)))
        assertEquals(AppSettings(AppLanguage.DANISH, 8, true, true), store.value)
        assertEquals(1, platform.starts)
    }
    @Test fun `U10 start failure restores previous settings and allows retry`() {
        platform.accepted = false
        assertFalse(controller.finishSetup(SetupState(AppLanguage.GERMAN, 6)))
        assertEquals(AppSettings(), store.value)
        platform.accepted = true
        assertTrue(controller.finishSetup(SetupState(AppLanguage.GERMAN, 6)))
    }
    @Test fun `R18 off persists and boot never re-enables it`() {
        controller.finishSetup(SetupState())
        assertTrue(controller.setMonitoring(false))
        assertFalse(store.value.monitoringEnabled); assertEquals(1, platform.stops)
        assertFalse(controller.resumeIfEnabled()); assertEquals(1, platform.starts)
    }
    @Test fun `R10 enable and boot require actual access`() {
        store.value = AppSettings(setupComplete = true)
        platform.allowed = false
        assertFalse(controller.setMonitoring(true)); assertFalse(store.value.monitoringEnabled)
        store.value = store.value.copy(monitoringEnabled = true)
        assertFalse(controller.resumeIfEnabled()); assertTrue(store.value.monitoringEnabled)
        assertEquals(0, platform.starts)
    }
    @Test fun `R15 interval and language updates do not restart a live episode`() {
        controller.finishSetup(SetupState())
        controller.setInterval(10); controller.setLanguage(AppLanguage.GERMAN)
        assertEquals(10, store.value.intervalMinutes); assertEquals(AppLanguage.GERMAN, store.value.language)
        assertEquals(1, platform.starts); assertEquals(2, platform.changes)
        assertThrows(IllegalArgumentException::class.java) { controller.setInterval(3) }
    }
    @Test fun `U11 relaunch retries stopped service only when setup and preference permit`() {
        assertFalse(controller.resumeIfEnabled())
        store.value = AppSettings(setupComplete = true, monitoringEnabled = true)
        assertTrue(controller.resumeIfEnabled()); assertTrue(controller.resumeIfEnabled())
        assertEquals(1, platform.starts)
    }
}
