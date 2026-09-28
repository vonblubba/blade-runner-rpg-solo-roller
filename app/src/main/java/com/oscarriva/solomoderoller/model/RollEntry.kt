package com.oscarriva.solomoderoller.model

data class RollEntry(
    val id: Long,
    val tableTitle: String,
    val diceDescription: String,
    val resultLines: List<String>,
    val timestamp: Long
)
