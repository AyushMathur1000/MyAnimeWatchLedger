-- V002: Add the distinct Watch Later ledger status.
-- Rebuild the table because SQLite cannot alter an existing CHECK constraint.
-- Preserve all existing ledger identities, titles, statuses, and timestamps.

CREATE TABLE ledger_entry_v2 (
    mal_id        INTEGER PRIMARY KEY CHECK (mal_id > 0),
    title         TEXT NOT NULL CHECK (length(trim(title)) > 0),
    status        TEXT NOT NULL CHECK (
        status IN ('WATCHED', 'WATCHING', 'ABANDONED', 'BLACKLISTED', 'HOLD', 'WATCH_LATER')
    ),
    date_added    INTEGER NOT NULL,
    last_modified INTEGER NOT NULL
);

INSERT INTO ledger_entry_v2 (mal_id, title, status, date_added, last_modified)
SELECT mal_id, title, status, date_added, last_modified
FROM ledger_entry;

DROP TABLE ledger_entry;
ALTER TABLE ledger_entry_v2 RENAME TO ledger_entry;

CREATE INDEX idx_ledger_entry_status ON ledger_entry(status);
CREATE INDEX idx_ledger_entry_title_nocase ON ledger_entry(title COLLATE NOCASE);
CREATE INDEX idx_ledger_entry_date_added ON ledger_entry(date_added);
CREATE INDEX idx_ledger_entry_last_modified ON ledger_entry(last_modified);
