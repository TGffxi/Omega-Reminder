# Omega Reminder 1.1 Always-Fullscreen Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every due Omega Reminder visibly occupy the screen with Done/+10/+30/+60 controls, using the existing lock-screen full-screen activity plus an overlay while unlocked.

**Architecture:** Keep Room and `ReminderCoordinator` as the single source of truth. Add a short-lived `OverlayReminderService` using `TYPE_APPLICATION_OVERLAY`; continue posting the existing alarm notification/full-screen intent for lock-screen behavior. Both presentation paths observe/use the same reminder/occurrence state so actions synchronize automatically.

**Tech Stack:** Android/Kotlin, Jetpack Compose, Room, AlarmManager, WindowManager overlay, foreground service, GitHub Actions release signing.

**Spec:** `docs/superpowers/specs/2026-09-17-omega-reminder-1.1-overlay-design.md`

## Global Constraints

- `versionCode = 2`, `versionName = "1.1.0"`.
- Release artifact is `OmegaReminder-1.1.apk`.
- Use the same release signing key/secrets as Omega Reminder 1.0.
- App stays silent: no alarm sound and no forced vibration.
- Every reminder exposes `Erledigt`, `+10`, `+30`, `+60`.
- Room remains authoritative; do not create a second occurrence/snooze state machine.
- Existing 15-minute retry, reboot/time/timezone rebuild, exact-alarm fallback and notification fallback stay intact.
- Overlay service is not permanent; it exists only while an outstanding reminder needs visual presentation.
- Existing database schema remains compatible; no Room migration.

---

### Task 1: Version and permission surface

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `.github/workflows/android.yml`

**Interfaces:**
- Produces Android permissions `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`.
- Registers `.overlay.OverlayReminderService` as a non-exported `specialUse` foreground service.
- Produces signed artifact `OmegaReminder-1.1.apk`.

- [ ] Change app version to code 2 / name 1.1.0.
- [ ] Add overlay/foreground-service permissions and service declaration.
- [ ] Change release workflow output/artifact names from 1.0 to 1.1.
- [ ] Run `git diff --check`.

### Task 2: Make snooze universal without schema migration

**Files:**
- Modify: `app/src/main/java/com/openai/omegareminder/ui/edit/EditReminderScreen.kt`
- Modify: `app/src/main/java/com/openai/omegareminder/alarms/ReminderCoordinator.kt`
- Modify: `app/src/main/java/com/openai/omegareminder/notifications/ReminderNotifier.kt`
- Modify: `app/src/main/java/com/openai/omegareminder/ui/reminder/ReminderActivity.kt`
- Test: `app/src/test/kotlin/com/openai/omegareminder/domain/OccurrencePolicyTest.kt` (existing behavior retained)

**Interfaces:**
- All presentation paths allow snooze 10/30/60.
- Existing `presentationMode` field remains stored but is no longer used to hide snooze.

- [ ] Remove the mode selector from the editor and always save `FULLSCREEN_SNOOZE`.
- [ ] Remove the coordinator guard that rejects snooze for legacy no-snooze rows.
- [ ] Always add +10/+30/+60 actions to notifications.
- [ ] Always render +10/+30/+60 in `ReminderActivity`.
- [ ] Run unit tests.

### Task 3: Add overlay presentation controller/service

**Files:**
- Create: `app/src/main/java/com/openai/omegareminder/overlay/OverlayReminderService.kt`
- Create: `app/src/main/java/com/openai/omegareminder/overlay/OverlayController.kt`
- Modify: `app/src/main/java/com/openai/omegareminder/OmegaReminderApp.kt`
- Modify: `app/src/main/java/com/openai/omegareminder/alarms/ReminderCoordinator.kt`
- Modify: `app/src/main/java/com/openai/omegareminder/notifications/NotificationChannels.kt`

**Interfaces:**
- `OverlayController.showIfAllowed()` starts the service only when `Settings.canDrawOverlays(context)` is true.
- `OverlayController.hideIfIdle()` requests service cleanup after state changes.
- Service observes `repository.reminders` + `repository.occurrences`, selects oldest `OUTSTANDING`, and renders one full-screen overlay.
- Overlay buttons call `coordinator.complete(id)` / `coordinator.snooze(id, minutes)`.

- [ ] Create a foreground-service notification channel that is silent and low importance.
- [ ] Implement overlay permission check and service start/stop controller.
- [ ] Implement service with `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`, full-screen match-parent dimensions, Compose UI and Room observation.
- [ ] Ensure one overlay only; update existing content rather than stacking windows.
- [ ] Ensure service stops and removes its window when no outstanding reminder remains.
- [ ] Trigger overlay presentation from regular due, retry, snooze-due and rebuild-recovered paths.
- [ ] Request cleanup after complete/snooze/disable/delete.
- [ ] Run compile/unit tests in CI.

### Task 4: Overlay permission and test UX

**Files:**
- Modify: `app/src/main/java/com/openai/omegareminder/ui/MainActivity.kt`
- Modify: `app/src/main/java/com/openai/omegareminder/ui/settings/SettingsScreen.kt`

**Interfaces:**
- `overlayEnabled: () -> Boolean`
- `onOpenOverlaySettings: () -> Unit`
- Settings action opens `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` for this package.
- Test reminder uses overlay when permission exists, otherwise the existing activity fallback.

- [ ] Add `Über anderen Apps anzeigen` permission row.
- [ ] Open package overlay settings directly.
- [ ] Make test reminder exercise overlay path when granted.
- [ ] Show concise warning in settings when overlay permission is missing.
- [ ] Verify returning from Android settings refreshes state.

### Task 5: Verification and signed release

**Files:**
- Verify all modified/created files.
- No source behavior outside the approved scope.

- [ ] Run `git diff --check`.
- [ ] Run all unit tests through GitHub Actions.
- [ ] Build release APK through GitHub Actions.
- [ ] Verify APK signing in workflow.
- [ ] Download `OmegaReminder-1.1-final` artifact.
- [ ] Verify ZIP/APK integrity and compute SHA-256.
- [ ] Confirm signer certificate SHA-256 matches 1.0 signer (`a175c0045e2e44177dee8a55f09a41bc6ca9aa061a46472c4d567c0cf3363731`).
- [ ] Deliver `OmegaReminder-1.1.apk`.
