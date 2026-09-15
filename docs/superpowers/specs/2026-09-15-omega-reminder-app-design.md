# Omega Reminder – Android App Design

Date: 2026-09-15
Status: Approved design, ready for implementation planning
Platform: Android
Working title: Omega Reminder

## 1. Purpose

Build a small, reliable Android reminder app whose primary job is to keep a reminder open until the user explicitly completes it. The initial use case is remembering Omega-3 oil, but the app must support multiple independent reminders for any purpose.

The app is intentionally local-first, simple, silent, and persistent. It does not require an account, cloud backend, network access, ads, analytics, or health-data integration.

## 2. Core user requirements

A user can create multiple reminders. Each reminder has:

- a name, e.g. “Omega 3”
- a time of day
- one recurrence mode:
  - one-time
  - daily
  - selected weekdays
- one presentation mode:
  - full-screen reminder with snooze
  - full-screen reminder without snooze
- an enabled/disabled switch

When a reminder becomes due, it remains outstanding until the user explicitly marks it as completed.

If the user does nothing, the app re-alerts every 15 minutes.

In snooze mode the user can choose:

- +10 minutes
- +30 minutes
- +60 minutes
- Done

In no-snooze mode the only completion action is:

- Done

Snoozing moves only the current occurrence. It never shifts the reminder's recurring schedule.

## 3. Important Android platform constraint

The requested experience is implemented using a high-priority notification with a full-screen intent and a dedicated reminder activity.

Android controls whether a true full-screen activity is allowed to appear. On current Android versions, when the device is locked, an allowed full-screen intent can cover the lock screen. When the device is unlocked, Android may instead present an expanded heads-up notification. Therefore the app must never claim it can force a full-screen takeover in every device state.

The required behavior is:

1. Request and verify the system permissions needed for notifications, exact alarms and full-screen intent access.
2. When Android allows it, show the dedicated reminder screen immediately.
3. If Android does not launch the full-screen activity, show a prominent silent high-priority notification as the fallback.
4. Tapping the fallback notification opens the same dedicated reminder screen.
5. The app exposes permission status in Settings and clearly reports when full-screen access is unavailable.

The reminder experience is silent by design: no app-triggered alarm sound and no app-triggered vibration.

## 4. User experience

### 4.1 Home screen

The home screen lists all reminders. Each row shows:

- reminder name
- scheduled time
- recurrence summary
- enabled/disabled state
- current status

Example statuses:

- `Heute 08:00 · erledigt um 08:17`
- `Heute 08:00 · noch offen · seit 47 Min.`
- `Heute 08:00 · verschoben bis 09:00`
- `Morgen 08:00`
- `Deaktiviert`

Primary actions:

- floating `+` button to add a reminder
- tap a row to edit
- enable/disable directly from the row
- delete from an overflow action or equivalent explicit delete control
- open Settings

The list must support multiple reminders independently. One overdue reminder must not prevent another reminder from becoming due.

### 4.2 Add/Edit reminder screen

Fields:

- Name: required text field
- Time: required time picker
- Recurrence:
  - One-time
  - Daily
  - Weekdays
- If One-time: date picker is required
- If Weekdays: Monday–Sunday multi-select is required, at least one day selected
- Presentation mode:
  - Full screen + Snooze
  - Full screen without Snooze
- Enabled switch

Save validates inputs and recalculates the next regular occurrence.

### 4.3 Reminder screen

The reminder screen is intentionally simple and readable from a distance.

It shows:

- reminder name in large type
- scheduled time
- overdue duration when applicable
- if snoozed, the snooze target time

For `Full screen + Snooze` it shows four large actions:

- Done
- +10 min
- +30 min
- +60 min

For `Full screen without Snooze` it shows one large action:

- Done

There is no sound control and no vibration control because the app itself does not create sound or vibration.

The system Back action must not mark the reminder as done. Leaving the activity without choosing an action leaves the occurrence outstanding; its 15-minute retry remains scheduled.

### 4.4 Settings screen

Settings is intentionally small. It shows:

- notification permission status
- exact alarm access status
- full-screen intent access status
- shortcut buttons to the relevant Android system settings when access is missing
- `Test reminder` button

The test reminder opens the same presentation path used by real reminders, but it never changes or completes a real reminder occurrence.

## 5. Reminder state model

Each reminder is persistent data. The currently active occurrence is represented separately from the recurring schedule so that snooze cannot alter future recurrence.

### 5.1 Reminder entity

Suggested fields:

- `id: Long`
- `name: String`
- `enabled: Boolean`
- `localTime: LocalTime`
- `recurrenceType: ONE_TIME | DAILY | WEEKDAYS`
- `oneTimeDate: LocalDate?`
- `weekdayMask: Int`
- `presentationMode: FULLSCREEN_SNOOZE | FULLSCREEN_NO_SNOOZE`
- `createdAt: Instant`
- `updatedAt: Instant`
- `lastCompletedAt: Instant?`

### 5.2 Active occurrence entity

Suggested fields:

- `reminderId: Long`
- `scheduledFor: Instant`
- `status: OUTSTANDING | SNOOZED`
- `snoozedUntil: Instant?`
- `lastPresentedAt: Instant?`
- `nextRetryAt: Instant?`

There is at most one active occurrence per reminder.

This is deliberate: if a daily Omega-3 reminder from yesterday is still outstanding, the app does not create a second open Omega-3 dose today. The existing occurrence stays overdue until completed. After completion, the next future regular occurrence is calculated from the reminder's schedule.

## 6. State transitions

### 6.1 Regular occurrence becomes due

1. Alarm fires.
2. Repository checks that the reminder still exists and is enabled.
3. If no active occurrence exists, create one as `OUTSTANDING`.
4. Present the reminder.
5. Schedule a one-shot retry for 15 minutes later.

### 6.2 User does nothing

At each retry:

1. Confirm the occurrence is still outstanding.
2. Present it again.
3. Schedule another one-shot retry for 15 minutes later.

Retries continue until Done, Snooze, Disable or Delete changes the state.

### 6.3 Snooze

Available only in snooze mode.

1. Set active occurrence to `SNOOZED`.
2. Set `snoozedUntil` to now + 10, 30 or 60 minutes.
3. Cancel the pending 15-minute retry.
4. Schedule one exact one-shot snooze alarm.
5. At snooze expiry, switch back to `OUTSTANDING`, present the reminder and restart the normal 15-minute retry cycle.

The reminder's recurring schedule is unchanged.

### 6.4 Done

1. Cancel all alarms associated with the active occurrence.
2. Remove the active occurrence.
3. Store `lastCompletedAt`.
4. For a recurring reminder, calculate and schedule the next regular occurrence strictly in the future.
5. For a one-time reminder, disable it after completion.
6. Cancel the active notification and close the reminder activity if it is visible.

### 6.5 Disable

Disabling a reminder:

- cancels its regular alarm
- cancels its retry or snooze alarm
- clears its active occurrence
- cancels its notification

Re-enabling calculates the next future regular occurrence from the current schedule. It does not resurrect an old overdue occurrence.

### 6.6 Delete

Deleting a reminder cancels every PendingIntent/notification associated with that reminder and removes both the reminder and active occurrence from storage.

## 7. Editing behavior

Editing does not silently count an active occurrence as completed.

- Name changes apply immediately to the active occurrence display.
- Presentation mode changes apply to the next presentation of the active occurrence.
- Schedule changes affect future regular occurrences.
- If an occurrence is currently outstanding or snoozed, it remains outstanding until Done unless the reminder is explicitly disabled or deleted.
- Saving any edit rebuilds the reminder's next regular alarm to avoid stale scheduled PendingIntents.

This avoids both accidental completion and duplicate occurrences.

## 8. Scheduling architecture

### 8.1 Technology

- Kotlin
- Jetpack Compose + Material 3
- Room for local persistence
- Repository layer for reminders and active occurrences
- AlarmManager for user-visible timed events
- BroadcastReceivers for alarm delivery, boot recovery and exact-alarm permission state changes
- NotificationCompat for high-priority notifications/full-screen intents
- java.time APIs for schedule calculations

No continuously running foreground service is required for the baseline design.

### 8.2 Exact alarm strategy

Use `SCHEDULE_EXACT_ALARM` and check `AlarmManager.canScheduleExactAlarms()` before scheduling exact alarms on versions where required.

The app uses exact one-shot alarms rather than repeating alarms:

- one exact alarm for the next regular occurrence
- one exact alarm for the current 15-minute retry when an occurrence is outstanding
- or one exact alarm for the current snooze target

Only the next required alarm is scheduled per logical path. This avoids Android's inexact repeating-alarm behavior and avoids building up a queue of stale retries.

Preferred call when exact access is granted:

- `setExactAndAllowWhileIdle(RTC_WAKEUP, ...)`

If exact access is unavailable:

- do not silently pretend precision is guaranteed
- show a clear setup warning
- use the best available fallback alarm only as a degraded mode
- keep the database state authoritative so alarms can be rebuilt after access is granted

### 8.3 Time-of-day recurrence

Regular reminders are wall-clock events. Store their intended local time and recurrence rule rather than storing a fixed 24-hour interval.

For each next occurrence:

1. Read the device's current `ZoneId`.
2. Find the next matching local calendar date.
3. Resolve the configured local time in that zone.
4. Convert to an Instant and schedule it.

This preserves e.g. 08:00 across daylight-saving changes rather than drifting by one hour.

DST edge behavior:

- If a configured local time does not exist because of the spring-forward gap, schedule at the first valid instant after the gap.
- If a local time occurs twice during the autumn overlap, use the earlier occurrence.

### 8.4 15-minute retry

The 15-minute retry is relative to the last trigger and uses a one-shot exact alarm. It is not a repeating alarm.

When a retry fires late because the device was unavailable, the app treats it as an overdue outstanding occurrence, presents it as soon as possible and schedules the next retry from the actual presentation time.

## 9. Reboot and recovery

Android alarms do not survive a device reboot. Register a receiver for `BOOT_COMPLETED`.

On boot:

1. Load all enabled reminders and active occurrences from Room.
2. If an active occurrence is already overdue, present it as soon as the system permits and schedule the next 15-minute retry.
3. If an active occurrence is snoozed and its snooze time is still in the future, restore that snooze alarm.
4. If the snooze time has already passed, treat it as outstanding and overdue.
5. For reminders with no active occurrence, calculate and schedule the next regular occurrence.

The same rebuild routine is reused after exact-alarm permission is newly granted.

## 10. Notification and full-screen behavior

### 10.1 Notification channel

Create a dedicated reminder channel configured for high visual importance but silent behavior:

- high importance required for intrusive visual presentation/heads-up behavior
- no app-supplied sound
- no app-supplied vibration pattern
- public lock-screen content unless the user changes the channel setting

System/OEM/user notification settings remain authoritative.

### 10.2 Full-screen access

Manifest declares `USE_FULL_SCREEN_INTENT`.

On supported Android versions:

- check whether full-screen intents can be used
- if missing, show a settings action that opens the relevant special access page
- full-screen access denial must never cause the reminder to disappear

The notification always carries a content intent to the dedicated reminder activity. When allowed, it also carries a full-screen intent to that activity.

The reminder activity is configured to show over the lock screen and request that the screen turn on when Android allows it.

### 10.3 No sound or vibration

The app does not deliberately play audio or start vibration when presenting a reminder. This is a visual reminder app, not an audible alarm clock.

## 11. Permissions and special access

Depending on Android version, the app needs:

- `POST_NOTIFICATIONS`
- `SCHEDULE_EXACT_ALARM`
- `USE_FULL_SCREEN_INTENT`
- `RECEIVE_BOOT_COMPLETED`

The app must request permissions only when relevant and explain why they are needed.

Permission checks must be visible in Settings. Missing permission states are degraded-mode states, not crashes.

## 12. Components and responsibilities

Suggested boundaries:

### `data/`

- Room database
- `ReminderEntity`
- `ActiveOccurrenceEntity`
- DAOs
- repository implementation

### `domain/`

- reminder models
- recurrence enum
- presentation mode enum
- schedule calculator
- state transition/use-case classes

### `alarms/`

- `AlarmScheduler`
- unique PendingIntent/request-code generation
- `ReminderAlarmReceiver`
- `BootReceiver`
- `ExactAlarmPermissionReceiver`
- rebuild/reschedule coordinator

### `notifications/`

- notification channel setup
- notification/full-screen intent builder
- notification cancellation

### `ui/`

- Home screen
- Add/Edit screen
- Reminder screen
- Settings screen
- ViewModels

Each subsystem has one clear responsibility; scheduling logic must not be embedded in Compose UI code.

## 13. PendingIntent identity and duplicate prevention

Every scheduled operation must have a deterministic identity derived from reminder ID plus alarm kind. At minimum distinguish:

- regular occurrence
- retry
- snooze

Rescheduling the same logical alarm replaces the old one. Completing, disabling or deleting a reminder cancels all three possible identities.

Room is the source of truth. A receiver must re-check database state before acting, so a stale PendingIntent cannot resurrect a deleted or completed occurrence.

## 14. Multiple simultaneous reminders

Multiple reminders can be due independently.

Each reminder uses:

- its own notification ID/tag
- its own alarm identities
- its own active occurrence

If two reminders become due at nearly the same time, both remain outstanding. Opening or completing one must not modify the other.

## 15. Process death and app closure

The app must not rely on an Activity, ViewModel, in-memory timer or process remaining alive.

All durable state lives in Room. AlarmManager and system receivers re-enter the application when needed. Any UI opened from a notification reconstructs its state from the repository using the reminder ID.

## 16. Error handling

The app must fail visibly and safely.

Examples:

- Notifications denied: Settings shows missing access and reminders remain stored.
- Exact alarm access denied/revoked: Settings warns; exact alarms are rebuilt when access returns.
- Full-screen access denied: use the prominent notification fallback.
- Reminder referenced by a stale alarm no longer exists: receiver exits without user-visible error.
- Invalid edit input: Save remains blocked with an inline explanation.
- Database error: surface a generic retryable error rather than silently deleting reminders.

## 17. Privacy and network

Baseline app:

- no login
- no account
- no analytics
- no ads
- no cloud sync
- no network permission needed
- all reminder data stored locally on the device

## 18. Testing strategy

### Unit tests

- next daily occurrence
- weekday recurrence across all weekday combinations
- one-time occurrence
- next occurrence is always in the future after Done
- DST spring gap
- DST autumn overlap
- snooze does not alter recurrence
- 15-minute retry calculation
- overdue occurrence logic
- duplicate-prevention identifiers

### Repository/state tests

- Due -> Outstanding
- Outstanding -> Retry
- Outstanding -> Snoozed
- Snoozed -> Outstanding
- Outstanding -> Done
- Disable clears occurrence
- Delete cancels/removes state
- Editing preserves an open occurrence unless disabled/deleted

### Instrumentation/integration tests

- create/edit/delete reminder
- daily reminder
- one-time reminder
- selected weekdays
- +10/+30/+60 snooze
- no action -> retry after 15 minutes
- no-snooze mode has no snooze actions
- multiple simultaneous reminders
- notification tap opens correct reminder
- full-screen-intent fallback path
- reboot rebuild logic
- exact-alarm permission revoked/restored
- notification permission denied/restored
- device time/timezone change behavior

### Manual acceptance tests on a real device

1. Full-screen + Snooze reminder while phone is locked.
2. Full-screen + Snooze reminder while phone is unlocked.
3. Full-screen without Snooze while locked/unlocked.
4. Ignore reminder for at least two 15-minute retries.
5. Test all three snooze buttons.
6. Reboot before a scheduled reminder.
7. Power device off across a due time, then boot.
8. Keep a daily reminder overdue across the next day's nominal time and confirm no duplicate occurrence is created.
9. Complete overdue occurrence and confirm the next schedule is the next future regular time.
10. Remove full-screen access and verify notification fallback.
11. Remove exact alarm access and verify visible degraded state.
12. Verify no app-generated sound or vibration.

## 19. Acceptance criteria

The first release is accepted when:

- multiple reminders can be created and independently managed
- once, daily and selected-weekday recurrence all work
- both presentation modes work
- snooze offers exactly 10/30/60 minutes
- no-action reminders repeat every 15 minutes until resolved
- Done reliably ends the active occurrence
- snooze never shifts the recurring schedule
- overdue reminders do not multiply into duplicate doses
- state survives process death and device reboot
- missed reminders are recovered after boot
- full-screen denial has a reliable visible fallback
- the app itself produces no reminder sound or vibration
- the UI clearly shows upcoming, overdue, snoozed, completed and disabled states

## 20. Explicitly out of scope for v1

- cloud synchronization
- account/login
- smartwatch/Wear OS app
- medication or dosage tracking
- health advice
- history/analytics dashboard beyond the latest completion status
- custom snooze durations
- custom automatic retry interval
- audible alarms
- vibration alarms
- location-based reminders
- shared/family reminders

These can be added later without changing the core reminder state model.

## 21. Implementation recommendation

Proceed with a single-module Android app initially, using package boundaries described above. Keep the scheduling/state logic isolated from Compose UI and test it heavily before wiring real alarms. Build the reminder delivery path around Room as the authority, exact one-shot AlarmManager events, and idempotent BroadcastReceivers.

The most important implementation rule is: **an alarm event is only a trigger to re-read authoritative state; it is never authoritative state itself.** This prevents stale system events from creating duplicate or resurrected reminders.

