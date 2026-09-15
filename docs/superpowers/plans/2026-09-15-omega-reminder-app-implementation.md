# Omega Reminder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Android app that manages multiple persistent visual reminders with once/daily/weekday recurrence, 10/30/60-minute snooze, 15-minute no-action retries, and a full-screen reminder UI with a notification fallback.

**Architecture:** Single-module Kotlin Android app using Jetpack Compose/Material 3 for UI, Room as the authoritative persistent state, domain-level schedule/state services, and AlarmManager one-shot alarms for regular, retry, and snooze triggers. Broadcast receivers always re-read Room before acting; stale alarm events never create authoritative state.

**Tech Stack:** Kotlin, Android Gradle Plugin, Jetpack Compose, Material 3, Navigation Compose, Room, Kotlin coroutines/Flow, AlarmManager, NotificationCompat, java.time, JUnit, AndroidX test.

**Spec:** `docs/superpowers/specs/2026-09-15-omega-reminder-app-design.md`

## Global Constraints

- Android-only, local-first, no login, no analytics, no ads, no cloud, no network permission.
- Reminder presentation is silent: no app-triggered sound and no app-triggered vibration.
- Recurrence modes: one-time, daily, selected weekdays.
- Presentation modes: full-screen with snooze, full-screen without snooze.
- Snooze durations are exactly 10, 30, and 60 minutes.
- Unhandled active occurrences retry every 15 minutes.
- There is at most one active occurrence per reminder; overdue occurrences never multiply into duplicate doses.
- Room is the source of truth; system alarms are only triggers to re-read state.
- Wall-clock schedules retain local time across timezone/DST changes.
- Missing exact-alarm/full-screen/notification access must degrade visibly and safely rather than crash or silently lose reminders.

---

## File Map

- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`: Gradle/Android project configuration.
- `app/build.gradle.kts`: app dependencies and Android configuration.
- `app/src/main/AndroidManifest.xml`: activities, permissions, receivers.
- `app/src/main/java/com/openai/omegareminder/data/*`: Room entities, DAOs, database, repository.
- `app/src/main/java/com/openai/omegareminder/domain/*`: enums, schedule calculator, state transitions.
- `app/src/main/java/com/openai/omegareminder/alarms/*`: alarm identity/scheduling, receivers, rebuild coordinator.
- `app/src/main/java/com/openai/omegareminder/notifications/*`: notification channel, full-screen notification builder/cancellation.
- `app/src/main/java/com/openai/omegareminder/ui/*`: Compose screens and ViewModels.
- `app/src/test/java/com/openai/omegareminder/*`: schedule/state unit tests.

### Task 1: Project scaffold and pure scheduling domain

**Files:** create Gradle project files, manifest, theme/app entry point, `domain/Models.kt`, `domain/ScheduleCalculator.kt`; test `ScheduleCalculatorTest.kt`.

**Interfaces:**
- `enum class RecurrenceType { ONE_TIME, DAILY, WEEKDAYS }`
- `enum class PresentationMode { FULLSCREEN_SNOOZE, FULLSCREEN_NO_SNOOZE }`
- `data class ReminderSchedule(...)`
- `class ScheduleCalculator { fun nextOccurrence(schedule: ReminderSchedule, now: Instant, zone: ZoneId): Instant? }`

- [ ] Write JUnit tests for one-time/daily/weekdays and future-only behavior.
- [ ] Run tests and confirm failures before implementation.
- [ ] Implement `ScheduleCalculator` with java.time wall-clock logic and DST-safe resolution.
- [ ] Run tests until passing.
- [ ] Commit scaffold/domain.

### Task 2: Room persistence and repository state transitions

**Files:** create entities/DAOs/database/repository plus `ReminderStateService.kt`; test repository/state transitions with an in-memory fake repository for pure state logic.

**Interfaces:**
- `ReminderEntity`, `ActiveOccurrenceEntity`
- `ReminderRepository.observeReminders()`, `getReminder(id)`, `getOccurrence(id)`, `saveReminder`, `setOccurrence`, `clearOccurrence`, `deleteReminder`
- `ReminderStateService.onDue`, `onRetry`, `snooze`, `complete`, `disable`

- [ ] Write failing state-transition tests for due/retry/snooze/done/disable and one-active-occurrence invariant.
- [ ] Implement persistence model and state service.
- [ ] Run tests until passing.
- [ ] Commit persistence/state.

### Task 3: Alarm scheduling, receivers, recovery, and notification delivery

**Files:** create `AlarmKind.kt`, `AlarmScheduler.kt`, `ReminderAlarmReceiver.kt`, `BootReceiver.kt`, `ExactAlarmPermissionReceiver.kt`, `RebuildCoordinator.kt`, `ReminderNotifier.kt`, `NotificationChannels.kt`.

**Interfaces:**
- alarm kinds `REGULAR`, `RETRY`, `SNOOZE`
- deterministic PendingIntent identity from reminder ID + kind
- `AlarmScheduler.scheduleRegular`, `scheduleRetry`, `scheduleSnooze`, `cancelAll`
- receivers call repository/state service and reschedule based on current database state
- notifier opens `ReminderActivity` via content/full-screen intent when allowed

- [ ] Add deterministic identity and retry-time unit tests.
- [ ] Implement one-shot exact scheduling with degraded inexact fallback when exact access is unavailable.
- [ ] Implement idempotent alarm receiver and rebuild coordinator for boot/permission restoration.
- [ ] Implement high-importance silent notification channel and visible fallback.
- [ ] Run unit tests and compile.
- [ ] Commit scheduling/delivery.

### Task 4: Compose home, edit, settings, and reminder screens

**Files:** create `MainActivity.kt`, navigation, Home/Edit/Settings composables, `ReminderActivity.kt`, view models, reusable UI components.

**Interfaces:**
- Home shows reminder rows, statuses, enable/disable, add/edit/delete.
- Edit validates required name/time/date/weekdays and presentation mode.
- Settings shows permission/special-access status and test reminder action.
- ReminderActivity renders large reminder name/time/overdue state and exactly the permitted action buttons.

- [ ] Implement ViewModels around repository/state services.
- [ ] Implement home and add/edit flows.
- [ ] Implement settings and permission navigation.
- [ ] Implement full-screen reminder UI with snooze/no-snooze variants; Back never completes.
- [ ] Compile and run available tests.
- [ ] Commit UI.

### Task 5: Integration hardening and APK build

**Files:** manifest/build config/tests/README as needed.

- [ ] Verify permissions/receivers/export flags and lock-screen/screen-on behavior.
- [ ] Add tests for snooze not shifting recurrence, simultaneous reminders, stale alarm no-op, and disabled/deleted reminder safety where feasible.
- [ ] Run full Gradle test suite and lint/assembleDebug.
- [ ] Inspect generated APK and capture SHA-256.
- [ ] Write concise install/test notes including Android special-access steps.
- [ ] Commit release candidate.
