package com.oscarriva.solomoderoller.logic

import com.oscarriva.solomoderoller.data.RollTable
import com.oscarriva.solomoderoller.data.TableRow
import kotlin.random.Random

data class RollResult(val dieValues: List<Int>, val row: TableRow)

object Roller {
    fun rollDice(table: RollTable, random: Random = Random.Default): List<Int> {
        return table.dice.map { dieSpec ->
            val sides = dieSpec.removePrefix("d").toInt()
            random.nextInt(1, sides + 1)
        }
    }

    fun resolveRow(table: RollTable, dieValues: List<Int>): TableRow {
        return table.rows.first { row ->
            row.ranges.indices.all { i -> dieValues[i] in row.ranges[i].min..row.ranges[i].max }
        }
    }

    fun roll(table: RollTable, random: Random = Random.Default): RollResult {
        val dieValues = rollDice(table, random)
        return RollResult(dieValues, resolveRow(table, dieValues))
    }
}
