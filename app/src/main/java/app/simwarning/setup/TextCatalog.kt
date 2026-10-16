package app.simwarning.setup

import android.content.Context
import app.simwarning.core.AppLanguage
import org.json.JSONObject

class TextCatalog private constructor(private val entries: Map<String, String>) {
    fun text(id: String, vararg arguments: Pair<String, Any>): String {
        val template = requireNotNull(entries[id]) { "Missing text catalog key: $id" }
        val values = arguments.toMap()
        val placeholders = PLACEHOLDER.findAll(template).map { it.groupValues[1] }.toSet()
        require(values.keys == placeholders) { "Arguments for $id must be $placeholders" }
        return PLACEHOLDER.replace(template) { values.getValue(it.groupValues[1]).toString() }
    }
    val keys: Set<String> get() = entries.keys
    companion object {
        private val PLACEHOLDER = Regex("\\{([a-zA-Z][a-zA-Z0-9]*)\\}")

        fun load(context: Context, language: AppLanguage): TextCatalog {
            val source = context.assets.open("texts/${language.catalogFile}").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(source)
            val entries = json.keys().asSequence().associateWith { key ->
                val value = json.get(key)
                require(value is String && value.isNotBlank()) { "Invalid text at $key" }
                value
            }
            return TextCatalog(entries)
        }
    }
}
