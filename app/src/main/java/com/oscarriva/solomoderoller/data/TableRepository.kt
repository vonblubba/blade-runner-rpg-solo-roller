package com.oscarriva.solomoderoller.data

import android.content.Context
import kotlinx.serialization.json.Json

object TableRepository {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseTables(jsonText: String): TablesFile = json.decodeFromString(jsonText)

    fun loadFromAssets(context: Context): TablesFile {
        val text = context.assets.open("tables.json").bufferedReader().use { it.readText() }
        return parseTables(text)
    }
}
