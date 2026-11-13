-- V003: Persist the identities and ordering of open Anime Details tabs.
-- Deliberately no foreign key to ledger or metadata cache: tabs are independent UI state.

CREATE TABLE open_anime_detail_tab (
    mal_id    INTEGER PRIMARY KEY CHECK (mal_id > 0),
    tab_order INTEGER NOT NULL UNIQUE CHECK (tab_order >= 0)
);

CREATE INDEX idx_open_anime_detail_tab_order
    ON open_anime_detail_tab(tab_order);
