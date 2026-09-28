package com.oscarriva.solomoderoller.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TablesJsonCoverageTest {
    @Test
    fun everyDieValueComboMatchesExactlyOneRow() {
        val jsonText = File("src/main/assets/tables.json").readText()
        val tablesFile = TableRepository.parseTables(jsonText)

        assertTrue("Expected at least one section", tablesFile.sections.isNotEmpty())

        for (section in tablesFile.sections) {
            for (table in section.tables) {
                for (row in table.rows) {
                    assertEquals(
                        "Table '${table.title}' has a row with ranges.size != dice.size",
                        table.dice.size,
                        row.ranges.size
                    )
                    assertEquals(
                        "Table '${table.title}' has a row with values.size != columns.size",
                        table.columns.size,
                        row.values.size
                    )
                }

                val sidesPerDie = table.dice.map { it.removePrefix("d").toInt() }
                for (combo in cartesianProduct(sidesPerDie)) {
                    val matches = table.rows.count { row ->
                        row.ranges.indices.all { i -> combo[i] in row.ranges[i].min..row.ranges[i].max }
                    }
                    assertEquals(
                        "Table '${table.title}' die-value combo $combo should match exactly one row, matched $matches",
                        1,
                        matches
                    )
                }
            }
        }
    }

    private fun cartesianProduct(sidesPerDie: List<Int>): List<List<Int>> {
        return sidesPerDie.fold(listOf(listOf())) { acc, sides ->
            acc.flatMap { prefix -> (1..sides).map { value -> prefix + value } }
        }
    }
}
