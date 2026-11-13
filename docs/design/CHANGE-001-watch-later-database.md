# Database Design Change 001 — Watch Later

**Status:** Approved · **Schema version:** 2

The ledger status constraint now accepts WATCHED, WATCHING, ABANDONED, BLACKLISTED, HOLD, and WATCH_LATER. No separate Watch Later table or catalogue flag is introduced; the ledger remains the sole source of truth.

Migration V002 rebuilds ledger_entry into a replacement table with the expanded CHECK constraint, copies all rows verbatim, replaces the old table, and recreates the four existing indexes. The migration runner applies it transactionally and advances SQLite user_version to 2 only on success.

All existing MAL IDs, titles, statuses, date_added values, and last_modified values are preserved. No ledger-to-catalogue foreign key is introduced.

The application service's add operation without an explicit status selects WATCH_LATER. The explicit-status overload remains available.
