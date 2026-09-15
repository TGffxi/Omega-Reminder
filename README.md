# Omega Reminder

Native Android reminder app for persistent visual reminders that stay open until explicitly completed.

## Implemented v1 behavior

- Multiple independent reminders
- Recurrence: one-time, daily, selected weekdays
- Two presentation modes:
  - large/full-screen reminder with **Done / +10 / +30 / +60 min**
  - large/full-screen reminder with **Done only**
- If no action is taken, the occurrence is presented again every **15 minutes**
- Snooze affects only the current occurrence, never the recurring schedule
- At most one open occurrence per reminder; overdue reminders do not multiply into duplicate doses
- Local Room database; no login, cloud, ads, analytics, or network permission
- Exact one-shot alarms where Android grants exact-alarm access, with inexact fallback otherwise
- Boot/time/timezone recovery
- High-importance silent notification/full-screen-intent path; no app-generated sound or vibration
- Settings page for notification, exact-alarm and full-screen access status
- Test reminder button

## Android permissions/special access

On recent Android versions the app needs user approval for:

1. Notifications
2. Exact alarms / "Alarms & reminders"
3. Full-screen intents / full-screen notifications

Android remains in control of whether a full-screen intent actually opens over the current UI. If it does not, the app posts a prominent high-priority notification that opens the same reminder screen.

## Build

Recommended toolchain:

- JDK 17
- Android SDK Platform 36
- Android SDK Build Tools 35.0.0 or newer
- Gradle 8.13
- Android Gradle Plugin 8.13.2

From a machine with Gradle and the Android SDK configured:

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

A GitHub Actions workflow is included at `.github/workflows/android.yml`; pushing this project to GitHub will run the tests and produce `OmegaReminder-debug-apk` as a downloadable workflow artifact.

## Important acceptance checks on the real phone

After installation, grant the three relevant permissions/special accesses from the app's Settings screen, then use **Test-Erinnerung anzeigen**. Also test one real reminder with the phone locked and unlocked, Snooze 10/30/60, and an ignored reminder across at least two 15-minute retries.
