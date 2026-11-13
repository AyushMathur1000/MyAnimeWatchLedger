# Requirements Change 001 — Watch Later

**Status:** Approved · **Approved:** 2026-11-14 · **Supersedes:** MALW-SRS-001 v1.1 status definition

## Decision

Add **Watch Later** as a distinct sixth ledger status. It is not an alias for Hold.

| Status | Definition |
|---|---|
| Watched | The user has completed watching the anime. |
| Watching | The user is currently watching, including while waiting for unreleased episodes. |
| Abandoned | The user started the anime and has permanently dropped it. |
| Blacklisted | The user does not intend to watch the anime. |
| Hold | The user started the anime and intends to resume it later. |
| Watch Later | The user intends to watch the anime but has not started watching it. |

Every ledger entry continues to have exactly one current status. Status transitions overwrite the previous status; no status-change history is introduced.

## Add-to-ledger default

When an anime is added from Catalogue without an explicitly selected status, its initial status shall be **Watch Later**. The user can still select any of the six statuses. This default applies only to new entries; it does not reclassify existing records.

## Behaviour

- Watch Later entries remain eligible for catalogue presentation.
- Hold continues to mean started but temporarily paused.
- Blacklisted remains the only status that excludes an anime from catalogue presentations.
- Changing away from Blacklisted makes cached catalogue memberships eligible again.
- Catalogue refresh and cache clearing continue not to change ledger membership or status.

## Traceability

This change revises the status count and status list in SRS Sections 2, 3.1 and 5.1, FR-LED-003 and FR-LED-005, the UI status filters/selectors, and release acceptance criteria. The six statuses must be supported consistently throughout the application.

## Persistence compatibility

Schema version 2 adds WATCH_LATER to the allowed status constraint. Migration V002 rebuilds the SQLite ledger table because SQLite cannot directly alter the existing CHECK constraint. It copies existing records and timestamps unchanged and recreates all ledger indexes. Existing databases are upgraded automatically.
