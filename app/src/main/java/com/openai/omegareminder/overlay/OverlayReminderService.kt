package com.openai.omegareminder.overlay

import android.app.Service
import android.content.ColorStateList
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.openai.omegareminder.OmegaReminderApp
import com.openai.omegareminder.R
import com.openai.omegareminder.data.ActiveOccurrenceEntity
import com.openai.omegareminder.data.ReminderEntity
import com.openai.omegareminder.domain.OccurrenceStatus
import com.openai.omegareminder.notifications.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

class OverlayReminderService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app by lazy { application as OmegaReminderApp }
    private val windowManager by lazy { getSystemService(WindowManager::class.java) }

    private var observationJob: Job? = null
    private var statusTickerJob: Job? = null
    private var overlayView: View? = null

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensure(this)
        startAsForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_TEST -> if (observationJob?.isActive != true) showTestOverlay()
            else -> observeOutstandingReminders()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observationJob?.cancel()
        statusTickerJob?.cancel()
        removeOverlay()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startAsForegroundService() {
        val notification = NotificationCompat.Builder(this, NotificationChannels.OVERLAY_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Omega Reminder")
            .setContentText("Vollbild-Erinnerung aktiv")
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()

        val foregroundType =
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0

        ServiceCompat.startForeground(
            this,
            SERVICE_NOTIFICATION_ID,
            notification,
            foregroundType,
        )
    }

    private fun observeOutstandingReminders() {
        if (observationJob?.isActive == true) return
        observationJob = serviceScope.launch {
            combine(app.repository.reminders, app.repository.occurrences) { reminders, occurrences ->
                selectDisplay(reminders, occurrences)
            }.collect { display ->
                if (display == null) {
                    removeOverlay()
                    stopSelf()
                } else {
                    renderReminder(display.first, display.second)
                }
            }
        }
    }

    private fun selectDisplay(
        reminders: List<ReminderEntity>,
        occurrences: List<ActiveOccurrenceEntity>,
    ): Pair<ReminderEntity, ActiveOccurrenceEntity>? {
        val remindersById = reminders.associateBy { it.id }
        val occurrencesById = occurrences.associateBy { it.reminderId }
        val selectedId = selectOldestOutstanding(
            occurrences.mapNotNull { occurrence ->
                val reminder = remindersById[occurrence.reminderId] ?: return@mapNotNull null
                OverlayCandidate(
                    reminderId = reminder.id,
                    scheduledForEpochMillis = occurrence.scheduledForEpochMillis,
                    enabled = reminder.enabled,
                    outstanding = occurrence.occurrenceStatus() == OccurrenceStatus.OUTSTANDING,
                )
            }
        ) ?: return null

        val reminder = remindersById[selectedId] ?: return null
        val occurrence = occurrencesById[selectedId] ?: return null
        return reminder to occurrence
    }

    private fun renderReminder(
        reminder: ReminderEntity,
        occurrence: ActiveOccurrenceEntity,
    ) {
        renderOverlay(
            name = reminder.name,
            scheduledFor = occurrence.scheduledFor(),
            onDone = {
                serviceScope.launch(Dispatchers.IO) {
                    app.coordinator.complete(reminder.id)
                }
            },
            onSnooze = { minutes ->
                serviceScope.launch(Dispatchers.IO) {
                    app.coordinator.snooze(reminder.id, minutes)
                }
            },
        )
    }

    private fun showTestOverlay() {
        observationJob?.cancel()
        observationJob = null
        renderOverlay(
            name = "Test-Erinnerung",
            scheduledFor = Instant.now(),
            onDone = { stopSelf() },
            onSnooze = { stopSelf() },
        )
    }

    private fun renderOverlay(
        name: String,
        scheduledFor: Instant,
        onDone: () -> Unit,
        onSnooze: (Int) -> Unit,
    ) {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        removeOverlay()
        val panel = buildPanel(name, scheduledFor, onDone, onSnooze)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            windowManager.addView(panel.root, params)
            overlayView = panel.root
            startStatusTicker(panel.status, scheduledFor)
        } catch (_: SecurityException) {
            stopSelf()
        } catch (_: WindowManager.BadTokenException) {
            stopSelf()
        }
    }

    private fun buildPanel(
        name: String,
        scheduledFor: Instant,
        onDone: () -> Unit,
        onSnooze: (Int) -> Unit,
    ): Panel {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
            setBackgroundColor(Color.rgb(16, 17, 20))
            isClickable = true
            isFocusable = true
        }

        val nameView = TextView(this).apply {
            text = name
            setTextColor(Color.WHITE)
            textSize = 46f
            gravity = Gravity.CENTER
            maxLines = 3
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(
            nameView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        )

        val timeView = TextView(this).apply {
            text = scheduledFor.atZone(ZoneId.systemDefault())
                .toLocalTime()
                .format(DateTimeFormatter.ofPattern("HH:mm"))
            setTextColor(Color.WHITE)
            textSize = 30f
            gravity = Gravity.CENTER
        }
        root.addView(
            timeView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(18) }
        )

        val statusView = TextView(this).apply {
            setTextColor(Color.LTGRAY)
            textSize = 18f
            gravity = Gravity.CENTER
        }
        updateStatusText(statusView, scheduledFor)
        root.addView(
            statusView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) }
        )

        val doneButton = makeButton("Erledigt")
        root.addView(
            doneButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(68),
            ).apply { topMargin = dp(42) }
        )

        val snoozeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        listOf(10, 30, 60).forEach { minutes ->
            val button = makeButton("+$minutes Min")
            snoozeRow.addView(
                button,
                LinearLayout.LayoutParams(0, dp(60), 1f).apply {
                    val margin = dp(5)
                    leftMargin = margin
                    rightMargin = margin
                }
            )
            button.setOnClickListener {
                setActionsEnabled(root, false)
                onSnooze(minutes)
            }
        }
        root.addView(
            snoozeRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(18) }
        )

        doneButton.setOnClickListener {
            setActionsEnabled(root, false)
            onDone()
        }

        return Panel(root, statusView)
    }

    private fun makeButton(label: String): Button =
        Button(this).apply {
            text = label
            textSize = 18f
            isAllCaps = false
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(Color.rgb(45, 47, 55))
        }

    private fun setActionsEnabled(view: View, enabled: Boolean) {
        if (view is Button) view.isEnabled = enabled
        if (view is android.view.ViewGroup) {
            for (index in 0 until view.childCount) {
                setActionsEnabled(view.getChildAt(index), enabled)
            }
        }
    }

    private fun startStatusTicker(statusView: TextView, scheduledFor: Instant) {
        statusTickerJob?.cancel()
        statusTickerJob = serviceScope.launch {
            while (isActive) {
                updateStatusText(statusView, scheduledFor)
                delay(30_000)
            }
        }
    }

    private fun updateStatusText(statusView: TextView, scheduledFor: Instant) {
        val overdue = max(0, Duration.between(scheduledFor, Instant.now()).toMinutes())
        statusView.text = if (overdue > 0) "Seit $overdue Min. offen" else "Jetzt fällig"
    }

    private fun removeOverlay() {
        statusTickerJob?.cancel()
        statusTickerJob = null
        overlayView?.let { view ->
            runCatching { windowManager.removeViewImmediate(view) }
        }
        overlayView = null
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private data class Panel(
        val root: LinearLayout,
        val status: TextView,
    )

    companion object {
        const val ACTION_SHOW = "com.openai.omegareminder.overlay.SHOW"
        const val ACTION_TEST = "com.openai.omegareminder.overlay.TEST"
        private const val SERVICE_NOTIFICATION_ID = 43_001
    }
}
