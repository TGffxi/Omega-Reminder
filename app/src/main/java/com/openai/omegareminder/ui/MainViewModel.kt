package com.openai.omegareminder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openai.omegareminder.OmegaReminderApp
import com.openai.omegareminder.data.ActiveOccurrenceEntity
import com.openai.omegareminder.data.ReminderEntity
import com.openai.omegareminder.domain.ReminderDraft
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as OmegaReminderApp

    data class HomeItem(val reminder: ReminderEntity, val occurrence: ActiveOccurrenceEntity?)

    val items: StateFlow<List<HomeItem>> = combine(
        app.repository.reminders,
        app.repository.occurrences,
    ) { reminders, occurrences ->
        val active = occurrences.associateBy { it.reminderId }
        reminders.map { HomeItem(it, active[it.id]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(draft: ReminderDraft, onDone: (Long) -> Unit) {
        viewModelScope.launch { onDone(app.coordinator.save(draft)) }
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { app.coordinator.setEnabled(id, enabled) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { app.coordinator.delete(id) }
    }
}
