package com.oscarriva.solomoderoller.data

import kotlinx.serialization.Serializable

@Serializable
data class DieRange(val min: Int, val max: Int)

@Serializable
data class TableRow(val ranges: List<DieRange>, val values: List<String>)

@Serializable
data class RollTable(
    val id: String,
    val title: String,
    val dice: List<String>,
    val columns: List<String>,
    val rows: List<TableRow>
)

@Serializable
data class TableSection(val name: String, val tables: List<RollTable>)

@Serializable
data class TablesFile(val sections: List<TableSection>)
