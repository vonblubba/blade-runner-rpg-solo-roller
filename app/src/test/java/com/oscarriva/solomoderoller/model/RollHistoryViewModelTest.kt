package com.oscarriva.solomoderoller.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RollHistoryViewModelTest {
    @Test
    fun addEntryPrependsNewestRollFirst() {
        val viewModel = RollHistoryViewModel()

        viewModel.addEntry("Table A", "D6: 3", listOf("Result: Low"))
        viewModel.addEntry("Table B", "D6: 5", listOf("Result: High"))

        assertEquals(2, viewModel.entries.size)
        assertEquals("Table B", viewModel.entries[0].tableTitle)
        assertEquals("Table A", viewModel.entries[1].tableTitle)
    }

    @Test
    fun clearEmptiesTheHistory() {
        val viewModel = RollHistoryViewModel()

        viewModel.addEntry("Table A", "D6: 3", listOf("Result: Low"))
        viewModel.clear()

        assertTrue(viewModel.entries.isEmpty())
    }
}
