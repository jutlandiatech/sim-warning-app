package app.simwarning.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class SetupAccessibilityTest {
    @get:Rule val ui = createAndroidComposeRule<SetupTestActivity>()
    private fun next() = ui.onNodeWithTag(ElementIds.SETUP_CONTINUE_BUTTON).performScrollTo().performClick()

    @Test fun U04_androidBackReturnsOneStepAndKeepsLanguage() {
        ui.setContent { SetupFlow("de", { SetupReadiness.ACCESS_REQUIRED }, {}, { false }) }
        next(); next()
        ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.onNodeWithTag(ElementIds.SETUP_EXPLANATION_SCREEN).assertExists()
        ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.onNodeWithTag(ElementIds.SETUP_LANGUAGE_DE).assertIsSelected()
    }

    @Test fun U07_settingsReturnRefreshesReadinessWithoutAssumingGrant() {
        var readiness = SetupReadiness.ACCESS_REQUIRED
        ui.setContent { SetupFlow("en", { readiness }, {}, { false }) }
        repeat(3) { next() }
        ui.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        ui.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        ui.onNodeWithTag(ElementIds.SETUP_START_MONITORING_BUTTON).performScrollTo().assertIsNotEnabled()
        ui.runOnIdle { readiness = SetupReadiness.READY }
        ui.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        ui.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        ui.onNodeWithTag(ElementIds.SETUP_START_MONITORING_BUTTON).performScrollTo().assertIsEnabled()
    }

    @Test fun U14_popupOptionsExposeResourceIds() {
        ui.setContent { SetupFlow("de", { SetupReadiness.ACCESS_REQUIRED }, {}, { false }) }
        next(); next()
        ui.onNodeWithTag(ElementIds.SETUP_INTERVAL_SELECTOR).performScrollTo().performClick()
        ui.waitForIdle()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assertNotNull(device.findObject(By.res(ElementIds.SETUP_INTERVAL_OPTION_8)))
    }

    @Test fun U12_U13_allLocalesAtCompactWidthAndLargeFontHaveUnclippedText() {
        var language by mutableStateOf("en")
        var scale by mutableFloatStateOf(1f)
        ui.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                key(language, scale) {
                    Box(Modifier.width(320.dp)) {
                        SetupFlow(language, { SetupReadiness.ACCESS_REQUIRED }, {}, { false })
                    }
                }
            }
        }
        for (locale in listOf("en", "da", "de")) for (fontScale in listOf(1f, 2f)) {
            ui.runOnIdle { language = locale; scale = fontScale }
            for (step in 0..3) {
                val textNodes = ui.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
                val nodes = textNodes.fetchSemanticsNodes()
                nodes.indices.forEach { index ->
                    val bounds = textNodes[index].getUnclippedBoundsInRoot()
                    assertTrue("Text extends beyond compact screen: $bounds", bounds.left.value >= -0.5f && bounds.right.value <= 320.5f)
                }
                ui.runOnIdle {
                    val layouts = mutableListOf<TextLayoutResult>()
                    nodes.forEach { it.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts) }
                    assertTrue("Text layout results missing", layouts.isNotEmpty())
                    layouts.forEach { layout ->
                        val description = "$locale / $fontScale / $step: ${layout.layoutInput.text}"
                        assertFalse("Vertical text overflow at $description", layout.didOverflowHeight)
                        for (line in 0 until layout.lineCount) {
                            assertFalse("Ellipsized text at $description", layout.isLineEllipsized(line))
                            // Paragraph layout width can exceed the constrained text node even
                            // when every glyph fits. Test rendered line extents, allowing 1px rounding.
                            assertTrue("Text starts outside bounds at $description", layout.getLineLeft(line) >= -1f)
                            assertTrue("Text ends outside bounds at $description", layout.getLineRight(line) <= layout.size.width + 1f)
                        }
                    }
                }
                if (step < 3) next() else {
                    ui.onNodeWithTag(ElementIds.SETUP_START_MONITORING_BUTTON).performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                }
            }
        }
    }

    @Test fun U12_captureNativeSetupForReview() {
        ui.setContent { SetupFlow("en", { SetupReadiness.ACCESS_REQUIRED }, {}, { false }) }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.getExternalFilesDir(null), "setup-review").apply { mkdirs() }
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        for ((index, name) in listOf("language", "explanation", "interval", "permissions").withIndex()) {
            ui.onNodeWithTag("setup_${name}_screen").assertExists()
            ui.waitForIdle()
            assertTrue(device.takeScreenshot(File(folder, "setup-$name.png")))
            if (index < 3) next()
        }
        ui.onNodeWithTag(ElementIds.SETUP_START_MONITORING_BUTTON).performScrollTo().assertIsNotEnabled()
        ui.waitForIdle()
        assertTrue(device.takeScreenshot(File(folder, "setup-permissions-actions.png")))
    }
}
