-- Keep recommendation impressions and user feedback durable and separate from disposable catalogue cache.
CREATE TABLE recommendation_event (
    event_id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    mal_id                   INTEGER NOT NULL CHECK (mal_id > 0),
    engine_id                TEXT NOT NULL CHECK (length(trim(engine_id)) > 0),
    engine_version           INTEGER NOT NULL CHECK (engine_version > 0),
    feature_snapshot_json   TEXT NOT NULL CHECK (json_valid(feature_snapshot_json)),
    recommendation_json      TEXT NOT NULL CHECK (json_valid(recommendation_json)),
    created_at               INTEGER NOT NULL
);

CREATE INDEX idx_recommendation_event_mal_id
    ON recommendation_event(mal_id);

CREATE INDEX idx_recommendation_event_created_at
    ON recommendation_event(created_at);

CREATE TABLE recommendation_feedback (
    feedback_id     INTEGER PRIMARY KEY AUTOINCREMENT,
    event_id        INTEGER NOT NULL REFERENCES recommendation_event(event_id) ON DELETE CASCADE,
    dimension       TEXT NOT NULL CHECK (dimension IN ('STATUS', 'PREFERENCE', 'WATCH_LATER_PRIORITY')),
    action          TEXT NOT NULL CHECK (action IN ('ACCEPTED', 'REJECTED', 'DISMISSED', 'MODIFIED')),
    selected_value  TEXT,
    responded_at    INTEGER NOT NULL,
    UNIQUE (event_id, dimension),
    CHECK (
        (action IN ('ACCEPTED', 'MODIFIED') AND selected_value IS NOT NULL AND length(trim(selected_value)) > 0)
        OR (action IN ('REJECTED', 'DISMISSED') AND selected_value IS NULL)
    )
);

CREATE INDEX idx_recommendation_feedback_event
    ON recommendation_feedback(event_id);
