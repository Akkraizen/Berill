# Connected features

- Search is available in the shell on both platforms and via Ctrl+K (Command+K on macOS). Arrow keys choose a result; Enter opens or runs it. Plugins expose SearchSource through provides(). Results and commands use enabled plugins only.
- Day selects a local calendar date and combines DaySummarySource providers with the journal-YYYY-MM-DD daily note. Trash restores retained tombstones using a newer logical clock; files are retained for recovery.
- Settings → Data exports a local ZIP containing records.json, Markdown notes and content-addressed attachment files. Archives are limited to 256 MiB of expanded content. Import validates paths, version and SHA-256, then merges records using existing conflict resolution. Credentials and device metadata are not exported. Archives contain readable personal data: keep them in a trusted location.
- Recipe photos use a local SHA-256 file cache, upload before record synchronization and download lazily. Existing base64 photos migrate when the recipe gallery is opened. Deploy the new sync server before updating clients. Keep the berill-attachments volume with the database. Attachment APIs require the same bearer token and isolate files by user. End-to-end encryption also covers attachment payloads.
- Android accepts shared text and links. Choose Notes, Books or Watching from the installed plugins. Quick Settings exposes Water, Habit and Expense tiles. All 22 widgets have preview thumbnails, including inside the widget picker.
- Settings → Data enables daily reminders (habits at 20:00; water at 12:00, 15:00 and 18:00; timed planner items; deadlines at 09:00). Android requests notification permission and uses persisted alarms; delivery may be delayed by Android power management. Workout timers use the same mechanism. Android also refreshes schedules through persisted hourly work. On desktop notifications use the running system tray.
- Health Connect is Android-only and opt-in in Settings → Data. It imports the last seven local calendar days on connect and whenever the app resumes. Only read permissions for weight, steps and sleep are requested. Data is shown in Nutrition, Workouts and Day, and follows the configured sync/encryption settings. Manual daily weights take precedence. Revoke access from Android Health Connect settings.
- Language cards use persisted SM-2 intervals (again, known), shared between the trainer and flashcard widget. Existing dictionary entries remain unchanged.
- Finance includes daily/monthly recurring operations and monthly category budgets. Recurrence uses deterministic transaction IDs and preserves the original day of month across short months. Maintenance catches up while the app is running and through persisted hourly Android work. Operations already created remain ordinary editable transactions if the recurrence is paused or removed.
- Workout creation fills unspecified exercise weights from the most recent completed workout containing the same exercise, without replacing explicit program weights.

## Server backups

The backup container writes PostgreSQL dumps and an attachment tar archive locally. Optional S3
attachment storage and independent encrypted restic backups are documented in [STORAGE.md](STORAGE.md).
Initialize the repository explicitly before starting remote backups. Remote failures retain local data.

Health Connect permission flow, launcher previews, tile behavior and delivery under device sleep
still require verification on a physical Android device before distribution.
