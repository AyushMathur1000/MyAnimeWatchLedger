-- V004: Add optional personal sentiment and Watch Later ordering.
-- NULL means unassigned. Priority 1 is most preferred; 10 is least preferred.
ALTER TABLE ledger_entry
    ADD COLUMN preference TEXT
    CHECK (preference IS NULL OR preference IN ('LIKE', 'DISLIKE'));

ALTER TABLE ledger_entry
    ADD COLUMN watch_later_priority INTEGER
    CHECK (watch_later_priority IS NULL OR (watch_later_priority BETWEEN 1 AND 10 AND status = 'WATCH_LATER'));

CREATE INDEX idx_ledger_entry_watch_later_priority
    ON ledger_entry(watch_later_priority)
    WHERE status = 'WATCH_LATER';
