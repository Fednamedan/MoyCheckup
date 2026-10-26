package ru.moycheckup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.moycheckup.data.AppState
import ru.moycheckup.data.Entry
import java.time.LocalDate

@Composable
fun ScreenTitle(title: String, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        trailing()
    }
}

@Composable
fun DiaryScreen(state: AppState, bottomPad: Dp, onAdd: () -> Unit, onDelete: (Entry) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val groups = state.entries.sortedByDescending { it.ts }.groupBy { it.date }.entries.toList()
    val today = LocalDate.now()
    val side = Modifier.padding(horizontal = 16.dp)

    LazyColumn(
        contentPadding = PaddingValues(top = top + 16.dp, bottom = bottomPad + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenTitle("Дневник") {
                Text(
                    state.entries.size.toString() + " " + plural(state.entries.size, "запись", "записи", "записей"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            }
        }
        if (groups.isEmpty()) {
            item {
                Box(side) {
                    Panel {
                        Label("Записей нет")
                        Text(
                            "Нажмите «+» внизу, выберите место на теле и поставьте силу боли. Все записи появятся здесь по дням.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        for (g in groups) {
            val d = g.key
            item(key = "h$d") {
                val label = (if (d == today) "Сегодня, " + d.fmt("d MMMM") else d.fmt("EEEE, d MMMM")).cap()
                Label(label, Modifier.padding(start = 20.dp, top = 8.dp))
            }
            items(g.value, key = { it.id }) { e ->
                Box(side.enter()) { EntryCard(e, onDelete = { onDelete(e) }) }
            }
        }
    }
}
