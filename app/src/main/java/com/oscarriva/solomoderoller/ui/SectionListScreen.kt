package com.oscarriva.solomoderoller.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oscarriva.solomoderoller.data.RollTable
import com.oscarriva.solomoderoller.data.TableSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionListScreen(
    sections: List<TableSection>,
    onTableClick: (RollTable) -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Solo Mode Roller") }) }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(sections) { section ->
                SectionCard(section = section, onTableClick = onTableClick)
            }
        }
    }
}

@Composable
private fun SectionCard(section: TableSection, onTableClick: (RollTable) -> Unit) {
    var expanded by remember { mutableStateOf(true) }
    Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Column {
            ListItem(
                headlineContent = { Text(section.name) },
                modifier = Modifier.clickable { expanded = !expanded }
            )
            if (expanded) {
                section.tables.forEach { table ->
                    ListItem(
                        headlineContent = { Text(table.title) },
                        supportingContent = { Text(table.dice.joinToString(" + ") { it.uppercase() }) },
                        modifier = Modifier.clickable { onTableClick(table) }
                    )
                }
            }
        }
    }
}
