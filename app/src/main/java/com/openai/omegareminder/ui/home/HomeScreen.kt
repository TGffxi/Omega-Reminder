package com.openai.omegareminder.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openai.omegareminder.data.ActiveOccurrenceEntity
import com.openai.omegareminder.data.ReminderEntity
import com.openai.omegareminder.domain.OccurrenceStatus
import com.openai.omegareminder.domain.RecurrenceType
import com.openai.omegareminder.domain.ScheduleCalculator
import com.openai.omegareminder.ui.MainViewModel
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeItems: List<MainViewModel.HomeItem>,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onToggle: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
    onSettings: () -> Unit,
) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = Instant.now()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Omega Reminder") },
                actions = { TextButton(onClick = onSettings) { Text("Einstellungen") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd) {
                Text("+ Erinnerung")
            }
        },
    ) { padding ->
        if (homeItems.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Noch keine Erinnerung. Lege mit „+ Erinnerung“ deine erste an.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(homeItems, key = { it.reminder.id }) { item ->
                    ReminderCard(item, now, onEdit, onToggle, onDelete)
                }
            }
        }
    }
}

@Composable
private fun ReminderCard(
    item: MainViewModel.HomeItem,
    now: Instant,
    onEdit: (Long) -> Unit,
    onToggle: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val reminder = item.reminder
    Card(Modifier.fillMaxWidth().clickable { onEdit(reminder.id) }) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(reminder.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("${timeText(reminder)} · ${recurrenceText(reminder)}", style = MaterialTheme.typography.bodyMedium)
                }
                Switch(
                    checked = reminder.enabled,
                    onCheckedChange = { onToggle(reminder.id, it) },
                )
            }
            Text(statusText(reminder, item.occurrence, now), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onDelete(reminder.id) }) { Text("Löschen") }
            }
        }
    }
}

private fun timeText(reminder: ReminderEntity): String = "%02d:%02d".format(reminder.hour, reminder.minute)

private fun recurrenceText(reminder: ReminderEntity): String = when (RecurrenceType.valueOf(reminder.recurrenceType)) {
    RecurrenceType.ONE_TIME -> reminder.oneTimeEpochDay?.let {
        LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
    } ?: "Einmalig"
    RecurrenceType.DAILY -> "Täglich"
    RecurrenceType.WEEKDAYS -> {
        val labels = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
        labels.filterIndexed { index, _ -> reminder.weekdayMask and (1 shl index) != 0 }.joinToString(" ")
    }
}

private fun statusText(reminder: ReminderEntity, occurrence: ActiveOccurrenceEntity?, now: Instant): String {
    if (!reminder.enabled) return "Deaktiviert"
    if (occurrence != null) {
        return when (occurrence.occurrenceStatus()) {
            OccurrenceStatus.SNOOZED -> {
                val target = occurrence.snoozedUntil()?.atZone(ZoneId.systemDefault())?.toLocalTime()
                "Verschoben bis ${target?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "–"}"
            }
            OccurrenceStatus.OUTSTANDING -> {
                val mins = max(0, Duration.between(occurrence.scheduledFor(), now).toMinutes())
                if (mins == 0L) "Jetzt fällig" else "Noch offen · seit $mins Min."
            }
        }
    }
    reminder.lastCompletedAt()?.let {
        val local = it.atZone(ZoneId.systemDefault())
        if (local.toLocalDate() == LocalDate.now()) return "Heute erledigt um ${local.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))}"
    }
    val next = ScheduleCalculator().nextOccurrence(reminder.schedule(), now, ZoneId.systemDefault())
    return next?.atZone(ZoneId.systemDefault())?.let {
        val day = when (it.toLocalDate()) {
            LocalDate.now() -> "Heute"
            LocalDate.now().plusDays(1) -> "Morgen"
            else -> it.toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM."))
        }
        "$day ${it.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))}"
    } ?: "Kein weiterer Termin"
}
