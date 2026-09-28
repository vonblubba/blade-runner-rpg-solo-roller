package com.oscarriva.solomoderoller.logic

import com.oscarriva.solomoderoller.data.DieRange
import com.oscarriva.solomoderoller.data.RollTable
import com.oscarriva.solomoderoller.data.TableRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RollerTest {
    private val table = RollTable(
        id = "sample",
        title = "Sample",
        dice = listOf("d6", "d12"),
        columns = listOf("Result"),
        rows = listOf(
            TableRow(ranges = listOf(DieRange(1, 2), DieRange(1, 12)), values = listOf("Bucket A")),
            TableRow(ranges = listOf(DieRange(3, 4), DieRange(1, 12)), values = listOf("Bucket B")),
            TableRow(ranges = listOf(DieRange(5, 6), DieRange(1, 12)), values = listOf("Bucket C"))
        )
    )

    @Test
    fun resolveRowFindsMatchingRowForGivenDieValues() {
        assertEquals("Bucket A", Roller.resolveRow(table, listOf(1, 7)).values[0])
        assertEquals("Bucket B", Roller.resolveRow(table, listOf(4, 1)).values[0])
        assertEquals("Bucket C", Roller.resolveRow(table, listOf(6, 12)).values[0])
    }

    @Test
    fun rollDiceProducesValuesWithinEachDiesRange() {
        repeat(200) {
            val values = Roller.rollDice(table)
            assertTrue(values[0] in 1..6)
            assertTrue(values[1] in 1..12)
        }
    }

    @Test
    fun rollCombinesRollDiceAndResolveRow() {
        val result = Roller.roll(table)
        assertEquals(result.row, Roller.resolveRow(table, result.dieValues))
    }
}
