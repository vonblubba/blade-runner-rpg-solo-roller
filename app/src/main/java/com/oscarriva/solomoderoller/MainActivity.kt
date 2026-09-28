package com.oscarriva.solomoderoller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.oscarriva.solomoderoller.data.RollTable
import com.oscarriva.solomoderoller.data.TableRepository
import com.oscarriva.solomoderoller.logic.Roller
import com.oscarriva.solomoderoller.model.RollHistoryViewModel
import com.oscarriva.solomoderoller.ui.SectionListScreen
import com.oscarriva.solomoderoller.ui.TableRollScreen
import com.oscarriva.solomoderoller.ui.theme.SoloModeRollerTheme

private sealed class Screen {
    data object SectionList : Screen()
    data class TableRoll(val table: RollTable) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tablesFile = TableRepository.loadFromAssets(this)

        setContent {
            SoloModeRollerTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.SectionList) }
                val historyViewModel = remember { RollHistoryViewModel() }

                when (val current = screen) {
                    is Screen.SectionList -> SectionListScreen(
                        sections = tablesFile.sections,
                        onTableClick = { table -> screen = Screen.TableRoll(table) }
                    )
                    is Screen.TableRoll -> TableRollScreen(
                        table = current.table,
                        history = historyViewModel.entries,
                        onRoll = {
                            val result = Roller.roll(current.table)
                            val diceDescription = current.table.dice.zip(result.dieValues)
                                .joinToString(" · ") { (die, value) -> "${die.uppercase()}: $value" }
                            val resultLines = current.table.columns.zip(result.row.values)
                                .map { (column, value) -> "$column: $value" }
                            historyViewModel.addEntry(current.table.title, diceDescription, resultLines)
                        },
                        onClear = { historyViewModel.clear() },
                        onBack = { screen = Screen.SectionList }
                    )
                }
            }
        }
    }
}
