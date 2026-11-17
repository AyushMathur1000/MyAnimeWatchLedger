# Automatic Database Backups

**Status:** Approved requirements; implementation pending  
**Related requirements:** FR-BACKUP-001 through FR-BACKUP-009, UI-SET-004  
**Authoritative reference:** [Project Reference](../PROJECT_REFERENCE.md)

## Purpose

Add automatic local SQLite backups while retaining the existing manual
Create Backup and Restore Backup actions. Automatic backup is a safety
feature for the user's complete local database, not a replacement for
user-managed copies.

## User-configurable settings

Settings shall expose the following controls:

| Setting | Default | Available configuration |
|---|---|---|
| Automatic backups | Enabled | Enabled / Disabled |
| Frequency | Weekly | Daily, Weekly, Monthly |
| Retention count | 2 | Any positive whole number |
| Destination | `<application-data>/backups` | User-selected directory |

On Windows, the default destination is
`%LOCALAPPDATA%\\MyAnimeWatchLedger\\backups`. When
`LOCALAPPDATA` is unavailable, use the application's existing
`~/.myanimewatchledger/backups` fallback.

The frequency is measured from the last successful automatic backup:
Daily means 24 hours, Weekly means 7 days, and Monthly means one
calendar month. A first run with no successful automatic backup is due
immediately. The application checks whether a backup is due during
startup; it does not run a background timer while the application stays
open.

## Backup lifecycle

1. Load automatic-backup preferences and the last successful automatic
   backup timestamp from the local configuration/preferences file.
2. If automatic backups are enabled and the configured interval has
   elapsed (or no successful automatic backup exists), schedule one
   backup asynchronously.
3. Create a timestamped automatic backup in the configured destination.
   Use a clearly distinguishable filename prefix such as
   `my_anime_watch_ledger_auto_`.
4. Reuse the existing database snapshot and validation path in
   `DatabaseBackupService`. The current service uses SQLite
   `VACUUM INTO` to create a consistent snapshot and validates database
   integrity, foreign keys, and required schema.
5. Only after the new backup has passed validation, update the
   last-successful-backup timestamp and apply retention.
6. Retention sorts automatic-backup files newest first and removes only
   older automatic-backup files beyond the configured count.

Manual backups use the existing user-selected filename and are never
included in automatic retention cleanup. Restore remains a manual,
confirmed operation.

## Failure handling and safety

- Backup work must run off the JavaFX Application Thread.
- Automatic backup failure must not block or abort application startup.
- Show a clear, non-modal failure notification and leave the last
  successful timestamp unchanged so the next eligible startup retries.
- If the destination is missing or unwritable, report the problem; do
  not silently switch to another directory.
- Do not delete older backups until the new backup has been created and
  validated successfully.
- Retention cleanup must only match the application's automatic-backup
  filename pattern. Never delete manually created backups or unrelated
  files.
- If cleanup itself fails, preserve remaining files and report the
  cleanup problem; do not mark the newly created backup as failed.
- Backup files are ordinary, unencrypted SQLite databases. Settings
  should disclose this so users can protect the destination directory.

## Persistence boundary

Automatic-backup settings (enabled flag, frequency, retention count,
destination path) and the last successful automatic backup timestamp
belong in the existing local configuration/preferences file outside
SQLite. This avoids coupling backup scheduling to database restore and
prevents restoring an older database from silently resetting the
current machine's backup destination or schedule.

No database schema migration is required for these settings.

## Implementation plan

1. Extend the local preferences store with typed load/save methods,
   validation, and the approved defaults.
2. Add an automatic-backup coordinator/service that determines due
   state, generates automatic filenames, invokes the existing validated
   backup service, records success, and applies safe retention.
3. Trigger the coordinator once during application startup on a
   background executor; marshal completion/failure feedback to JavaFX.
4. Add Settings controls for enablement, frequency, retention count,
   and destination directory selection. Persist each change and restore
   the previous control value if saving fails.
5. Add unit tests for defaults, invalid preferences, due calculations,
   first-run behaviour, retention ordering, manual-backup exclusion,
   failed backup preservation, and destination errors.
6. Perform manual Windows verification for startup timing, Settings
   persistence, backup contents, restore compatibility, and user-visible
   failure feedback.

## Acceptance checklist

- [ ] Defaults are enabled, Weekly, 2 backups, and the application-data
      `backups` directory.
- [ ] Daily, Weekly, and Monthly frequencies can be selected.
- [ ] Retention count accepts positive whole numbers and is persisted.
- [ ] Destination can be changed and is persisted.
- [ ] Startup creates at most one automatic backup when due.
- [ ] First startup creates an automatic backup.
- [ ] A successful backup is validated before retention cleanup.
- [ ] Retention removes only older automatic backups.
- [ ] Manual backups and unrelated files are never removed.
- [ ] Failed backup does not update the last-successful timestamp or
      remove prior backups.
- [ ] Automatic backup failure does not block application startup.
- [ ] Manual backup and restore continue to work.
- [ ] Full Maven test suite and manual Windows verification pass.
