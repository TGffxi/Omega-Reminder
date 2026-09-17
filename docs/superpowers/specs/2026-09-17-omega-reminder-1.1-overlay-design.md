# Omega Reminder 1.1 – Always-Fullscreen Overlay Design

## Ziel
Bei jeder fälligen Erinnerung erscheint Omega Reminder als große Reminder-Oberfläche – gesperrt, entsperrt, auf dem Homescreen oder über einer anderen App.

Immer sichtbar:
- Erledigt
- +10 Min
- +30 Min
- +60 Min

Die App bleibt lautlos.

## Präsentationsstrategie
- Gesperrt / Display aus: bestehender Full-Screen-Intent mit `ReminderActivity`, `showWhenLocked` und `turnScreenOn`.
- Entsperrt / andere App / Homescreen: zusätzlich `TYPE_APPLICATION_OVERLAY` über `SYSTEM_ALERT_WINDOW`.
- Ohne Overlay-Freigabe: bestehende Alarm-Benachrichtigung + Full-Screen-Intent bleiben Fallback.

## Overlay-Service
Neu: `OverlayReminderService`.
- Läuft nur solange mindestens eine `OUTSTANDING`-Erinnerung angezeigt werden muss.
- Foreground-Service-Typ `specialUse`.
- Eigener stiller, niedriger Notification-Channel.
- Kein permanenter Hintergrunddienst.
- Beobachtet Room und zeigt die älteste offene Erinnerung.
- Bei mehreren offenen Erinnerungen folgt nach Erledigt/Snooze automatisch die nächste.
- Snoozed Erinnerungen werden erst bei erneutem Fälligwerden wieder angezeigt.
- 15-Minuten-Retries erzeugen kein zweites Overlay.

## Aktionen
Overlay, Notification und Lockscreen-Activity verwenden dieselbe `ReminderCoordinator`-Logik. Keine zweite Reminder- oder Snooze-Logik.

## Snooze immer verfügbar
Version 1.1 zeigt bei jeder Erinnerung immer `+10/+30/+60`.
Die bestehende `presentationMode`-Spalte bleibt nur zur Datenbank-Kompatibilität erhalten. Neue/bearbeitete Erinnerungen werden als `FULLSCREEN_SNOOZE` gespeichert. Alte `FULLSCREEN_NO_SNOOZE`-Datensätze bekommen ebenfalls Snooze.
Die Auswahl „Groß anzeigen ohne Snooze“ wird aus dem Editor entfernt.

## Berechtigungen
Zusätzlich:
- `SYSTEM_ALERT_WINDOW`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_SPECIAL_USE`

Einstellungen:
- neuer Status `Über anderen Apps anzeigen`
- `Öffnen` führt zu `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`
- bei fehlender Overlay-Freigabe weist 1.1 deutlich darauf hin, dass „immer Vollbild“ erst nach Freigabe möglich ist
- Test-Erinnerung testet bei vorhandener Freigabe auch den Overlay-Pfad

## Lockscreen + Overlay parallel
Bei Fälligkeit werden weiterhin Notification + Full-Screen-Intent erzeugt und bei Overlay-Freigabe zusätzlich der Overlay-Service aktiviert.
Aktion in einer Darstellung aktualisiert Room; die andere Darstellung verschwindet dadurch automatisch.

## Versionierung
- `versionCode = 2`
- `versionName = "1.1.0"`
- Release: `OmegaReminder-1.1.apk`
- dieselbe Release-Signatur wie 1.0

## Abnahmekriterien
1. Lockscreen/Display aus: große Reminder-Ansicht erscheint und Bildschirm geht an.
2. Homescreen entsperrt: Overlay erscheint.
3. Andere App aktiv: Overlay erscheint darüber.
4. Immer Erledigt +10/+30/+60.
5. Snooze entfernt Overlay und bringt es nach gewählter Zeit zurück.
6. Ohne Aktion bleibt Reminder offen; bestehender 15-Minuten-Retry bleibt aktiv.
7. Notification/Overlay/Activity bleiben synchron.
8. Mehrere offene Erinnerungen werden nacheinander gezeigt.
9. Ohne Overlay-Freigabe funktioniert der bisherige Fallback.
10. Update 1.0 → 1.1 ohne Deinstallation und Datenverlust.
11. Tests + Release-Build in GitHub Actions erfolgreich.
12. Release-APK mit derselben Signatur wie 1.0 verifiziert.
