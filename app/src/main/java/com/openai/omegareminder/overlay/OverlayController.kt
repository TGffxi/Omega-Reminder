package com.openai.omegareminder.overlay

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat

class OverlayController(private val context: Context) {
    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    fun showIfAllowed(): Boolean = start(OverlayReminderService.ACTION_SHOW)

    fun showTestIfAllowed(): Boolean = start(OverlayReminderService.ACTION_TEST)

    private fun start(action: String): Boolean {
        if (!canDrawOverlays()) return false
        return try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, OverlayReminderService::class.java).setAction(action),
            )
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalStateException) {
            false
        }
    }
}
