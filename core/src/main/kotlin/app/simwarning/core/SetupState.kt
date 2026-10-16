package app.simwarning.core

enum class AppLanguage(val code: String, val catalogFile: String) {
    DANISH("da", "TextCatalog_DEN.json"),
    ENGLISH("en", "TextCatalog_ENG.json"),
    GERMAN("de", "TextCatalog_GER.json");

    companion object {
        fun forDevice(languageTag: String): AppLanguage {
            val language = languageTag.substringBefore('-').substringBefore('_').lowercase(java.util.Locale.ROOT)
            return entries.firstOrNull { it.code == language } ?: ENGLISH
        }
    }
}

enum class SetupStep { LANGUAGE, EXPLANATION, INTERVAL, PERMISSIONS }

data class SetupState(
    val language: AppLanguage = AppLanguage.ENGLISH,
    val intervalMinutes: Int = 2,
    val step: SetupStep = SetupStep.LANGUAGE,
) {
    init { require(intervalMinutes in listOf(2, 4, 6, 8, 10)) }
    fun next(): SetupState = copy(step = SetupStep.entries[(step.ordinal + 1).coerceAtMost(3)])
    fun back(): SetupState = copy(step = SetupStep.entries[(step.ordinal - 1).coerceAtLeast(0)])
}
