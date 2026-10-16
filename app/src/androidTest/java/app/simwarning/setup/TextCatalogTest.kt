package app.simwarning.setup

import androidx.test.platform.app.InstrumentationRegistry
import app.simwarning.core.AppLanguage
import org.junit.Assert.*
import org.junit.Test

class TextCatalogTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun R31_allThreeCatalogsHaveIdenticalNonemptyKeysAndTranslatedNames() {
        val names = mapOf(AppLanguage.ENGLISH to "Sim Warning", AppLanguage.DANISH to "SIM-advarsel", AppLanguage.GERMAN to "SIM-Warnung")
        val referenceKeys = TextCatalog.load(context, AppLanguage.ENGLISH).keys
        assertTrue(referenceKeys.size >= 25)
        names.forEach { (language, name) ->
            val catalog = TextCatalog.load(context, language)
            assertEquals(referenceKeys, catalog.keys)
            assertEquals(name, catalog.text("app.name"))
            catalog.keys.filter { it != "setup.progress" && it != "setup.interval.value" }.forEach {
                assertTrue("Blank $language/$it", catalog.text(it).isNotBlank())
            }
        }
    }
    @Test fun R32_placeholdersAreStrictAndDoNotLeakIntoVisibleText() {
        val catalog = TextCatalog.load(context, AppLanguage.ENGLISH)
        assertEquals("Step 2 of 4", catalog.text("setup.progress", "current" to 2, "total" to 4))
        assertEquals("Every 10 minutes", catalog.text("setup.interval.value", "minutes" to 10))
        assertThrows(IllegalArgumentException::class.java) { catalog.text("setup.progress") }
        assertThrows(IllegalArgumentException::class.java) { catalog.text("missing.key") }
    }
}
