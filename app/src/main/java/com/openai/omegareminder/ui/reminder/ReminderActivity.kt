package com.openai.omegareminder.ui.reminder

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.openai.omegareminder.OmegaReminderApp
import com.openai.omegareminder.ui.theme.OmegaTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

class ReminderActivity : ComponentActivity() {
    private val app by lazy { application as OmegaReminderApp }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }

        val isTest = intent.getBooleanExtra(EXTRA_TEST, false)
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        setContent {
            OmegaTheme {
                var model by remember {
                    mutableStateOf<DisplayModel?>(
                        if (isTest) DisplayModel("Test-Erinnerung", Instant.now()) else null
                    )
                }
                LaunchedEffect(reminderId, isTest) {
                    if (!isTest) {
                        combine(app.repository.reminders, app.repository.occurrences) { reminders, occurrences ->
                            reminders.firstOrNull { it.id == reminderId } to occurrences.firstOrNull { it.reminderId == reminderId }
                        }.collect { (reminder, occurrence) ->
                            if (reminder == null || occurrence == null) {
                                finish()
                            } else {
                                model = DisplayModel(reminder.name, occurrence.scheduledFor())
                            }
                        }
                    }
                }
                model?.let { display ->
                    ReminderDisplay(
                        model = display,
                        onDone = {
                            if (isTest) finish() else lifecycleScope.launch { app.coordinator.complete(reminderId); finish() }
                        },
                        onSnooze = { minutes ->
                            if (isTest) finish() else lifecycleScope.launch { app.coordinator.snooze(reminderId, minutes); finish() }
                        },
                    )
                } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_TEST = "test_mode"
    }
}

data class DisplayModel(val name: String, val scheduledFor: Instant)

@Composable
private fun ReminderDisplay(
    model: DisplayModel,
    onDone: () -> Unit,
    onSnooze: (Int) -> Unit,
) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            now = Instant.now()
        }
    }
    BackHandler { /* Reminder stays visible until Erledigt or Snooze. */ }
    val scheduledLocal = model.scheduledFor.atZone(ZoneId.systemDefault())
    val overdue = max(0, Duration.between(model.scheduledFor, now).toMinutes())

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(model.name, fontSize = 46.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 52.sp)
            Spacer(Modifier.height(18.dp))
            Text(scheduledLocal.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 30.sp)
            Spacer(Modifier.height(10.dp))
            Text(if (overdue > 0) "Seit $overdue Min. offen" else "Jetzt fällig", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(42.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                Text("Erledigt", fontSize = 20.sp)
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(10, 30, 60).forEach { minutes ->
                    OutlinedButton(
                        onClick = { onSnooze(minutes) },
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    ) { Text("+$minutes Min") }
                }
            }
        }
    }
}
