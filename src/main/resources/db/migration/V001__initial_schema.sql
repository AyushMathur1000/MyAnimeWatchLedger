-- MyAnimeWatchLedger
-- V001: Initial schema
-- Ledger data is permanent. Catalogue and UI restoration data are disposable.
-- There are intentionally no foreign keys from the ledger to catalogue cache.

CREATE TABLE ledger_entry (
    mal_id        INTEGER PRIMARY KEY CHECK (mal_id > 0),
    title         TEXT NOT NULL CHECK (length(trim(title)) > 0),
    status        TEXT NOT NULL CHECK (
        status IN ('WATCHED', 'WATCHING', 'ABANDONED', 'BLACKLISTED', 'HOLD')
    ),
    date_added    INTEGER NOT NULL,
    last_modified INTEGER NOT NULL
);

CREATE INDEX idx_ledger_entry_status
    ON ledger_entry(status);

CREATE INDEX idx_ledger_entry_title_nocase
    ON ledger_entry(title COLLATE NOCASE);

CREATE INDEX idx_ledger_entry_date_added
    ON ledger_entry(date_added);

CREATE INDEX idx_ledger_entry_last_modified
    ON ledger_entry(last_modified);

CREATE TABLE catalogue_search (
    search_id                INTEGER PRIMARY KEY,
    season                   TEXT NOT NULL CHECK (
        season IN ('WINTER', 'SPRING', 'SUMMER', 'FALL')
    ),
    year                     INTEGER NOT NULL CHECK (year BETWEEN 1900 AND 9999),
    last_attempt_at          INTEGER,
    last_successful_fetch_at INTEGER,
    fetch_status             TEXT NOT NULL CHECK (
        fetch_status IN ('NEVER_FETCHED', 'SUCCESS', 'FAILED')
    ),
    last_error               TEXT,
    UNIQUE (season, year)
);

CREATE TABLE anime_metadata_cache (
    mal_id          INTEGER PRIMARY KEY CHECK (mal_id > 0),
    preferred_title TEXT NOT NULL,
    metadata_json   TEXT NOT NULL,
    searchable_text TEXT NOT NULL,
    cached_at       INTEGER NOT NULL
);

CREATE TABLE catalogue_search_anime (
    search_id     INTEGER NOT NULL,
    mal_id        INTEGER NOT NULL,
    display_order INTEGER NOT NULL CHECK (display_order >= 0),
    PRIMARY KEY (search_id, mal_id),
    FOREIGN KEY (search_id)
        REFERENCES catalogue_search(search_id)
        ON DELETE CASCADE,
    FOREIGN KEY (mal_id)
        REFERENCES anime_metadata_cache(mal_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_catalogue_search_anime_mal_id
    ON catalogue_search_anime(mal_id);

CREATE INDEX idx_catalogue_search_anime_order
    ON catalogue_search_anime(search_id, display_order);

CREATE TABLE open_catalogue_tab (
    search_id    INTEGER PRIMARY KEY,
    tab_order    INTEGER NOT NULL UNIQUE CHECK (tab_order >= 0),
    search_query TEXT NOT NULL DEFAULT '',
    FOREIGN KEY (search_id)
        REFERENCES catalogue_search(search_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_open_catalogue_tab_order
    ON open_catalogue_tab(tab_order);
