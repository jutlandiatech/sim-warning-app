package app.simwarning.setup

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import app.simwarning.core.AppLanguage
import app.simwarning.core.SetupState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SetupFlowTest {
    @get:Rule val ui = createAndroidComposeRule<SetupTestActivity>()
    private var readiness = SetupReadiness.ACCESS_REQUIRED
    private var reviews = 0
    private var starts = mutableListOf<SetupState>()
    private var acceptStart = true

    private fun launch(language: String = "en") {
        ui.setContent {
            SetupFlow(language, { readiness }, { reviews++ }, {
                starts.add(it)
                acceptStart
            })
        }
    }
    private fun next() = ui.onNodeWithTag("setup_continue_button").performScrollTo().performClick()
    private fun back() = ui.onNodeWithTag("setup_back_button").performScrollTo().performClick()
    private fun toPermissions() { repeat(3) { next() } }

    @Test fun U01_deviceLanguageAndFallback() {
        launch("da-DK")
        ui.onNodeWithTag("setup_language_da").assertIsSelected()
        ui.onNodeWithTag("setup_language_en").assertIsNotSelected()
        ui.onNodeWithTag("setup_language_de").assertIsNotSelected()
        ui.onNodeWithTag("setup_app_name").assertTextEquals("SIM-advarsel")
        ui.onNodeWithTag("setup_step_progress").assertTextEquals("Trin 1 af 4")
        ui.onNodeWithTag("setup_back_button").assertDoesNotExist()
    }
    @Test fun U02_immediateTranslationWithStableSelectedRows() {
        launch()
        for ((code, title) in listOf("de" to "SIM-Warnung", "da" to "SIM-advarsel", "en" to "Sim Warning")) {
            ui.onNodeWithTag("setup_language_$code").performScrollTo().performClick().assertIsSelected()
            ui.onNodeWithTag("setup_app_name").assertTextEquals(title)
        }
    }
    @Test fun U03_onlyCurrentStepIsExposedAndNoEarlyPermissionRequest() {
        launch()
        val steps = listOf("language", "explanation", "interval", "permissions")
        steps.forEachIndexed { index, current ->
            ui.onNodeWithTag("setup_${current}_screen").assertExists()
            steps.filter { it != current }.forEach { ui.onNodeWithTag("setup_${it}_screen").assertDoesNotExist() }
            ui.onNodeWithTag("setup_step_progress").assertTextEquals("Step ${index + 1} of 4")
            assertEquals(0, reviews)
            assertTrue(starts.isEmpty())
            if (index < 3) next()
        }
        ui.onNodeWithTag("setup_continue_button").assertDoesNotExist()
    }
    @Test fun U05_exactIntervalOptionsAndSelection() {
        launch()
        next(); next()
        ui.onNodeWithTag("setup_interval_selector").assertTextContains("Every 2 minutes")
        for (minutes in listOf(2, 4, 6, 8, 10)) {
            ui.onNodeWithTag("setup_interval_selector").performScrollTo().performClick()
            listOf(2, 4, 6, 8, 10).forEach { ui.onNodeWithTag("setup_interval_option_$it").assertExists() }
            ui.onAllNodes(hasTestTag("setup_interval_option_3")).assertCountEquals(0)
            ui.onNodeWithTag("setup_interval_option_$minutes").performScrollTo().performClick()
            ui.onNodeWithTag("setup_interval_selector").assertTextContains("Every $minutes minutes")
        }
    }
    @Test fun U04_U06_backAndRecreationRetainStepLanguageAndInterval() {
        launch()
        ui.onNodeWithTag("setup_language_de").performScrollTo().performClick()
        next(); next()
        ui.onNodeWithTag("setup_interval_selector").performScrollTo().performClick()
        ui.onNodeWithTag("setup_interval_option_8").performScrollTo().performClick()
        next(); back()
        ui.onNodeWithTag("setup_interval_selector").assertTextContains("Alle 8 Minuten")
        back(); back()
        ui.onNodeWithTag("setup_language_de").assertIsSelected()
    }
    @Test fun U06_saveableStateRestoresCurrentStepAndPreferences() {
        val restoration = StateRestorationTester(ui)
        restoration.setContent { SetupFlow("en", { readiness }, { reviews++ }, { starts.add(it); true }) }
        ui.onNodeWithTag("setup_language_da").performScrollTo().performClick()
        next(); next()
        ui.onNodeWithTag("setup_interval_selector").performScrollTo().performClick()
        ui.onNodeWithTag("setup_interval_option_6").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        ui.onNodeWithTag("setup_interval_screen").assertExists()
        ui.onNodeWithTag("setup_app_name").assertTextEquals("SIM-advarsel")
        ui.onNodeWithTag("setup_interval_selector").assertTextContains("Hvert 6. minut")
    }
    @Test fun U12_compactWidthAndDoubleFontKeepGermanActionsReachable() {
        ui.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                Box(Modifier.width(320.dp)) {
                    SetupFlow("de", { readiness }, { reviews++ }, { false })
                }
            }
        }
        toPermissions()
        ui.onNodeWithTag("setup_start_monitoring_button").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        back()
        ui.onNodeWithTag("setup_interval_selector").performScrollTo().assertIsDisplayed()
    }
    @Test fun U07_missingAccessAndReviewNeverPretendsGrant() {
        launch(); toPermissions()
        ui.onNodeWithTag("setup_start_monitoring_button").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithTag("setup_review_permissions_button").performScrollTo().performClick()
        assertEquals(1, reviews)
        ui.onNodeWithTag("setup_start_monitoring_button").performScrollTo().assertIsNotEnabled()
        assertTrue(starts.isEmpty())
    }
    @Test fun U09_accessRevokedBeforeTapIsRechecked() {
        readiness = SetupReadiness.READY
        launch(); toPermissions()
        ui.onNodeWithTag("setup_start_monitoring_button").performScrollTo().assertIsEnabled()
        ui.runOnIdle { readiness = SetupReadiness.ACCESS_REQUIRED }
        ui.onNodeWithTag("setup_start_monitoring_button").performClick().assertIsNotEnabled()
        assertTrue(starts.isEmpty())
    }
    @Test fun U10_singleCompletionContainsChosenPreferences() {
        readiness = SetupReadiness.READY
        launch("de-DE"); toPermissions()
        ui.onNodeWithTag("setup_start_monitoring_button").performScrollTo().performClick().performClick()
        assertEquals(listOf(SetupState(AppLanguage.GERMAN, 2, app.simwarning.core.SetupStep.PERMISSIONS)), starts)
    }
    @Test fun U10_failedStartDoesNotClaimSuccessAndCanRetry() {
        readiness = SetupReadiness.READY
        acceptStart = false
        launch(); toPermissions()
        ui.onNodeWithTag("setup_start_monitoring_button").performScrollTo().performClick()
        ui.onNodeWithTag("setup_permission_status").assertTextEquals("Monitoring could not start. Please try again.")
        ui.onNodeWithTag("setup_start_monitoring_button").performScrollTo().assertIsEnabled()
        ui.runOnIdle { acceptStart = true }
        ui.onNodeWithTag("setup_start_monitoring_button").performClick()
        assertEquals(2, starts.size)
    }
    @Test fun U14_uiAutomatorFindsLanguageRowByResourceId() {
        launch()
        ui.waitForIdle()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assertNotNull(device.findObject(By.res("setup_language_en")))
    }
}
