package com.jherkenhoff.qalculate.data

import android.content.Context
import org.json.JSONObject

class CurrencyAutocompleteProvider(context: Context) {
    private val codeToName: Map<String, String>
    private val nameToCode: Map<String, String>

    init {
        val json = context.assets.open("currencies.json").bufferedReader().use { it.readText() }
        val obj = JSONObject(json)
        codeToName = obj.keys().asSequence().associateWith { obj.getString(it) }
        nameToCode = codeToName.entries.associate { (k, v) -> v.lowercase() to k }
    }

    fun suggest(query: String): List<Pair<String, String>> {
        val q = query.lowercase()
        return codeToName.entries.filter {
            it.key.contains(q) || it.value.lowercase().contains(q)
        }.map { it.toPair() }
    }

    fun getCodeForName(name: String): String? = nameToCode[name.lowercase()]
    fun getNameForCode(code: String): String? = codeToName[code.lowercase()]
}
