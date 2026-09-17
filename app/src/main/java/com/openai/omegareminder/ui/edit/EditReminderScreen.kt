package com.openai.omegareminder.ui.edit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openai.omegareminder.data.ReminderEntity
import com.openai.omegareminder.domain.*
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditReminderScreen(
    existing: ReminderEntity?,
    onBack: () -> Unit,
    onSave: (ReminderDraft) -> Unit,
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var hour by remember(existing?.id) { mutableIntStateOf(existing?.hour ?: 8) }
    var minute by remember(existing?.id) { mutableIntStateOf(existing?.minute ?: 0) }
    var recurrence by remember(existing?.id) {
        mutableStateOf(existing?.recurrenceType?.let(RecurrenceType::valueOf) ?: RecurrenceType.DAILY)
    }
    var date by remember(existing?.id) {
        mutableStateOf(existing?.oneTimeEpochDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now())
    }
    var weekdayMask by remember(existing?.id) { mutableIntStateOf(existing?.weekdayMask ?: 0) }
    var enabled by remember(existing?.id) { mutableStateOf(existing?.enabled ?: true) }
    var timeDialog by remember { mutableStateOf(false) }
    var dateDialog by remember { mutableStateOf(false) }

    val draft = ReminderDraft(
        id = existing?.id ?: 0,
        name = name,
        enabled = enabled,
        localTime = LocalTime.of(hour, minute),
        recurrenceType = recurrence,
        oneTimeDate = if (recurrence == RecurrenceType.ONE_TIME) date else null,
        weekdayMask = if (recurrence == RecurrenceType.WEEKDAYS) weekdayMask else 0,
        presentationMode = PresentationMode.FULLSCREEN_SNOOZE,
    )
    val validation = validate(draft)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Erinnerung anlegen" else "Erinnerung bearbeiten") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Zurück") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name") },
                singleLine = true,
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Uhrzeit", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { timeDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("%02d:%02d".format(hour, minute))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Wiederholung", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecurrenceType.entries.forEach { type ->
                        FilterChip(
                            selected = recurrence == type,
                            onClick = { recurrence = type },
                            label = { Text(recurrenceLabel(type)) },
                        )
                    }
                }
            }

            if (recurrence == RecurrenceType.ONE_TIME) {
                OutlinedButton(onClick = { dateDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Datum: ${date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}")
                }
            }

            if (recurrence == RecurrenceType.WEEKDAYS) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Wochentage", style = MaterialTheme.typography.titleMedium)
                    val labels = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        labels.forEachIndexed { index, label ->
                            val bit = 1 shl index
                            FilterChip(
                                selected = weekdayMask and bit != 0,
                                onClick = { weekdayMask = weekdayMask xor bit },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }

            Text(
                "Anzeige: immer Vollbild mit Erledigt sowie +10 / +30 / +60 Min.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Aktiv", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            if (validation != null) Text(validation, color = MaterialTheme.colorScheme.error)
            Button(
                onClick = { onSave(draft) },
                enabled = validation == null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Speichern") }
        }
    }

    if (timeDialog) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { timeDialog = false },
            confirmButton = {
                TextButton(onClick = { hour = state.hour; minute = state.minute; timeDialog = false }) { Text("Übernehmen") }
            },
            dismissButton = { TextButton(onClick = { timeDialog = false }) { Text("Abbrechen") } },
            text = { TimePicker(state = state) },
        )
    }

    if (dateDialog) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { dateDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    dateDialog = false
                }) { Text("Übernehmen") }
            },
            dismissButton = { TextButton(onClick = { dateDialog = false }) { Text("Abbrechen") } },
        ) { DatePicker(state = state) }
    }
}

private fun recurrenceLabel(type: RecurrenceType): String = when (type) {
    RecurrenceType.ONE_TIME -> "Einmalig"
    RecurrenceType.DAILY -> "Täglich"
    RecurrenceType.WEEKDAYS -> "Wochentage"
}

private fun validate(draft: ReminderDraft): String? {
    if (draft.name.isBlank()) return "Bitte einen Namen eingeben."
    if (draft.recurrenceType == RecurrenceType.WEEKDAYS && draft.weekdayMask == 0) return "Bitte mindestens einen Wochentag wählen."
    if (draft.recurrenceType == RecurrenceType.ONE_TIME) {
        val next = ScheduleCalculator().nextOccurrence(
            ReminderSchedule(draft.localTime, RecurrenceType.ONE_TIME, draft.oneTimeDate),
            Instant.now(),
            ZoneId.systemDefault(),
        )
        if (next == null) return "Der einmalige Termin muss in der Zukunft liegen."
    }
    return null
}
