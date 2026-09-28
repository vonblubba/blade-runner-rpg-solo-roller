package com.oscarriva.solomoderoller.model

import androidx.compose.runtime.mutableStateListOf

class RollHistoryViewModel {
    private val _entries = mutableStateListOf<RollEntry>()
    val entries: List<RollEntry> get() = _entries

    private var nextId = 0L

    fun addEntry(tableTitle: String, diceDescription: String, resultLines: List<String>) {
        _entries.add(0, RollEntry(nextId++, tableTitle, diceDescription, resultLines, System.currentTimeMillis()))
    }

    fun clear() {
        _entries.clear()
    }
}
