package app.simwarning.core

import org.junit.Assert.*
import org.junit.Test

class SetupStateTest {
    @Test fun `U01 supported device locales preselect their language`() {
        mapOf("da-DK" to AppLanguage.DANISH, "en-GB" to AppLanguage.ENGLISH,
            "de-AT" to AppLanguage.GERMAN, "DE_de" to AppLanguage.GERMAN).forEach { (tag, expected) ->
            assertEquals(expected, AppLanguage.forDevice(tag))
        }
    }
    @Test fun `U01 unsupported or empty device language falls back to English`() {
        for (tag in listOf("fr-FR", "", "sv-SE", "deutsch")) {
            assertEquals(AppLanguage.ENGLISH, AppLanguage.forDevice(tag))
        }
    }
    @Test fun `U03 traverse four steps without completing setup implicitly`() {
        var state = SetupState()
        for (step in SetupStep.entries) {
            assertEquals(step, state.step)
            state = state.next()
        }
        assertEquals(SetupStep.PERMISSIONS, state.step)
    }
    @Test fun `U04 back stops at language and retains chosen values`() {
        var state = SetupState(AppLanguage.GERMAN, 8, SetupStep.PERMISSIONS)
        for (step in SetupStep.entries.reversed()) {
            assertEquals(step, state.step)
            assertEquals(AppLanguage.GERMAN, state.language)
            assertEquals(8, state.intervalMinutes)
            state = state.back()
        }
        assertEquals(SetupStep.LANGUAGE, state.step)
    }
    @Test fun `U05 only the five approved intervals can be represented`() {
        for (interval in listOf(2, 4, 6, 8, 10)) assertEquals(interval, SetupState(intervalMinutes = interval).intervalMinutes)
        for (interval in listOf(-2, 0, 1, 3, 5, 7, 9, 11, 12)) {
            assertThrows(IllegalArgumentException::class.java) { SetupState(intervalMinutes = interval) }
        }
    }
    @Test fun `U06 forward and backward navigation preserve selections`() {
        val state = SetupState(AppLanguage.DANISH, 10, SetupStep.EXPLANATION)
        assertEquals(state, state.next().back())
    }
}
