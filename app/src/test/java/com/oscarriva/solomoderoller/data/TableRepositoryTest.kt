package com.oscarriva.solomoderoller.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TableRepositoryTest {
    private val sampleJson = """
        {
          "sections": [
            {
              "name": "Test Section",
              "tables": [
                {
                  "id": "sample",
                  "title": "Sample Table",
                  "dice": ["d6"],
                  "columns": ["Result"],
                  "rows": [
                    {"ranges": [{"min":1,"max":3}], "values": ["Low"]},
                    {"ranges": [{"min":4,"max":6}], "values": ["High"]}
                  ]
                }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parsesSectionsTablesAndRows() {
        val parsed = TableRepository.parseTables(sampleJson)

        assertEquals(1, parsed.sections.size)
        assertEquals("Test Section", parsed.sections[0].name)

        val table = parsed.sections[0].tables[0]
        assertEquals("sample", table.id)
        assertEquals("Sample Table", table.title)
        assertEquals(listOf("d6"), table.dice)
        assertEquals(listOf("Result"), table.columns)
        assertEquals(2, table.rows.size)
        assertEquals("Low", table.rows[0].values[0])
        assertEquals(1, table.rows[0].ranges[0].min)
        assertEquals(3, table.rows[0].ranges[0].max)
        assertEquals("High", table.rows[1].values[0])
    }
}
