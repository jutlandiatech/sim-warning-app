package app.simwarning

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.simwarning.core.*
import app.simwarning.platform.AppPreferences
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppNavigationTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private fun existingSetup() {
        ui.runOnIdle { AppPreferences(ui.activity).write(AppSettings(AppLanguage.ENGLISH, 4, true, false)) }
        ui.onNodeWithTag("main_screen").assertExists()
    }
    @Test fun U11_existingSetupShowsMainAndPreservesOff() {
        existingSetup()
        ui.onNodeWithTag("main_monitoring_switch").assertIsOff()
        ui.activityRule.scenario.recreate()
        ui.onNodeWithTag("main_monitoring_switch").assertIsOff()
    }
    @Test fun S01_S02_sixSettingsDetailsHaveStableIdsAndBackNavigation() {
        existingSetup()
        ui.onNodeWithTag("main_settings_button").performScrollTo().performClick()
        val items = listOf("language", "warning_interval", "preview_sound", "test_warning", "permissions", "help")
        items.forEach { ui.onNodeWithTag("settings_${it}_item").assertExists() }
        for (item in items) {
            ui.onNodeWithTag("settings_${item}_item").performScrollTo().performClick()
            ui.onNodeWithTag("settings_back_button").performScrollTo().performClick()
            ui.onNodeWithTag("settings_screen").assertExists()
        }
    }
    @Test fun S03_languageAppliesImmediatelyAndPersists() {
        existingSetup()
        ui.onNodeWithTag("main_settings_button").performScrollTo().performClick()
        ui.onNodeWithTag("settings_language_item").performScrollTo().performClick()
        ui.onNodeWithTag("settings_language_de").performScrollTo().performClick().assertIsSelected()
        ui.onNodeWithTag("settings_title").assertTextEquals("Sprache")
        assertEquals(AppLanguage.GERMAN, AppPreferences(ui.activity).read().language)
        ui.activityRule.scenario.recreate()
        assertEquals(AppLanguage.GERMAN, AppPreferences(ui.activity).read().language)
    }
    @Test fun S04_intervalChangesPersistWithoutTurningMonitoringOn() {
        existingSetup()
        ui.onNodeWithTag("main_settings_button").performScrollTo().performClick()
        ui.onNodeWithTag("settings_warning_interval_item").performScrollTo().performClick()
        ui.onNodeWithTag("settings_interval_selector").performScrollTo().performClick()
        ui.onNodeWithTag("settings_interval_option_10").performScrollTo().performClick()
        assertEquals(10, AppPreferences(ui.activity).read().intervalMinutes)
        assertFalse(AppPreferences(ui.activity).read().monitoringEnabled)
    }
}
