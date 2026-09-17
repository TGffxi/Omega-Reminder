package com.openai.omegareminder.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import com.openai.omegareminder.OmegaReminderApp
import com.openai.omegareminder.ui.edit.EditReminderScreen
import com.openai.omegareminder.ui.home.HomeScreen
import com.openai.omegareminder.ui.reminder.ReminderActivity
import com.openai.omegareminder.ui.settings.SettingsScreen
import com.openai.omegareminder.ui.theme.OmegaTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) lifecycleScope.launch { app.coordinator.rebuildAll() }
        }

    private val overlaySettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (app.overlayController.canDrawOverlays()) {
                app.overlayController.showIfAllowed()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        lifecycleScope.launch { app.coordinator.rebuildAll() }

        setContent {
            OmegaTheme {
                val items by viewModel.items.collectAsState()
                var screen by remember { mutableStateOf<Screen>(Screen.Home) }
                var showOverlayPrompt by remember {
                    mutableStateOf(!app.overlayController.canDrawOverlays())
                }

                when (val current = screen) {
                    Screen.Home -> HomeScreen(
                        homeItems = items,
                        onAdd = { screen = Screen.Edit(null) },
                        onEdit = { screen = Screen.Edit(it) },
                        onToggle = viewModel::setEnabled,
                        onDelete = viewModel::delete,
                        onSettings = { screen = Screen.Settings },
                    )
                    is Screen.Edit -> {
                        val existing = current.id?.let { id -> items.firstOrNull { it.reminder.id == id }?.reminder }
                        EditReminderScreen(
                            existing = existing,
                            onBack = { screen = Screen.Home },
                            onSave = { draft -> viewModel.save(draft) { screen = Screen.Home } },
                        )
                    }
                    Screen.Settings -> SettingsScreen(
                        notificationsEnabled = { app.notifier.notificationsEnabled() },
                        exactAlarmEnabled = { app.alarmScheduler.canScheduleExact() },
                        fullScreenEnabled = { app.notifier.canUseFullScreenIntent() },
                        overlayEnabled = { app.overlayController.canDrawOverlays() },
                        onRequestNotifications = {
                            if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else openAppNotificationSettings()
                        },
                        onOpenExactAlarmSettings = { openExactAlarmSettings() },
                        onOpenFullScreenSettings = { openFullScreenSettings() },
                        onOpenOverlaySettings = { openOverlaySettings() },
                        onTestReminder = {
                            if (!app.overlayController.showTestIfAllowed()) {
                                startActivity(
                                    Intent(this, ReminderActivity::class.java)
                                        .putExtra(ReminderActivity.EXTRA_TEST, true)
                                )
                            }
                        },
                        onBack = { screen = Screen.Home },
                    )
                }

                if (showOverlayPrompt) {
                    AlertDialog(
                        onDismissRequest = { showOverlayPrompt = false },
                        title = { Text("Vollbild über anderen Apps") },
                        text = {
                            Text(
                                "Damit Omega Reminder bei entsperrtem Handy immer über der gerade geöffneten App erscheint, muss „Über anderen Apps anzeigen“ erlaubt werden."
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showOverlayPrompt = false
                                    openOverlaySettings()
                                }
                            ) { Text("Freigeben") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showOverlayPrompt = false }) { Text("Später") }
                        },
                    )
                }
            }
        }
    }

    private val app get() = application as OmegaReminderApp

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        }
    }

    private fun openFullScreenSettings() {
        if (Build.VERSION.SDK_INT >= 34) {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
        }
    }

    private fun openOverlaySettings() {
        overlaySettingsLauncher.launch(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun openAppNotificationSettings() {
        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }

    private sealed interface Screen {
        data object Home : Screen
        data class Edit(val id: Long?) : Screen
        data object Settings : Screen
    }
}
