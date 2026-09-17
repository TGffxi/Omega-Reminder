package com.openai.omegareminder.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    notificationsEnabled: () -> Boolean,
    exactAlarmEnabled: () -> Boolean,
    fullScreenEnabled: () -> Boolean,
    overlayEnabled: () -> Boolean,
    onRequestNotifications: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenFullScreenSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onTestReminder: () -> Unit,
    onBack: () -> Unit,
) {
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    @Suppress("UNUSED_VARIABLE") val refreshKey = refresh

    val overlayGranted = overlayEnabled()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Zurück") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PermissionRow("Benachrichtigungen", notificationsEnabled(), onRequestNotifications)
            PermissionRow("Exakte Alarme", exactAlarmEnabled(), onOpenExactAlarmSettings)
            if (Build.VERSION.SDK_INT >= 34) {
                PermissionRow("Vollbild-Erinnerungen", fullScreenEnabled(), onOpenFullScreenSettings)
            } else {
                PermissionRow("Vollbild-Erinnerungen", true, null)
            }
            PermissionRow("Über anderen Apps anzeigen", overlayGranted, onOpenOverlaySettings)
            HorizontalDivider()
            if (!overlayGranted) {
                Text(
                    "Für die Vollbild-Anzeige über anderen Apps muss „Über anderen Apps anzeigen“ freigegeben sein.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                "Fehlt eine Freigabe, bleiben deine Erinnerungen gespeichert. Die App verwendet dann den bestmöglichen sichtbaren Fallback.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onTestReminder, modifier = Modifier.fillMaxWidth()) { Text("Test-Erinnerung anzeigen") }
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, action: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(if (granted) "Freigegeben" else "Freigabe fehlt", color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }
        if (!granted && action != null) OutlinedButton(onClick = action) { Text("Öffnen") }
    }
}
