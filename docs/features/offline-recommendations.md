# Offline Anime Recommendations

## Purpose

Provide explainable, personalised suggestions for anime that are not yet in My Ledger. Recommendations appear only in the single-card Catalogue carousel.

The first implementation is a deterministic local rules engine. It is intentionally not an external LLM integration or a trained machine-learning model.

## Data boundaries

The recommendation engine reads only:

- Ledger entries and their explicit Like/Dislike preference values.
- Anime metadata already present in the local `anime_metadata_cache`.
- The candidate metadata already supplied by the cached catalogue result.

It has no MAL gateway, HTTP client, or detail-lookup dependency. It must never initiate a MAL request. Missing metadata is skipped; a candidate with no cached metadata receives no recommendation.

Recommendation generation is read-only. It does not add or modify ledger entries. The user must explicitly apply a suggestion.

## Eligibility and status rules

- A candidate already in the ledger is ineligible.
- Watch Later may be suggested as a provisional intention to watch.
- Blacklisted may be suggested when explicit preference signals indicate a strong mismatch.
- Hold is never suggested for an anime outside the ledger because Hold means the user started watching and paused for an extended period.
- Watched, Watching, and Abandoned are not inferred from catalogue metadata.
- Preference is the existing Like/Dislike value.
- Watch Later priority follows the existing scale: 1 is highest and 10 is lowest. It is only suggested with Watch Later status.
- When evidence is insufficient, preference and priority remain unset and confidence is reduced.

## Stage 1 implementation

The application depends on the `RecommendationEngine` interface. The initial implementation is `RuleBasedRecommendationEngine`.

The local preference profile is derived only from explicit ledger Like/Dislike values whose anime metadata is available in the cache. Ledger status alone is not treated as proof that an anime was liked or disliked.

The current scoring implementation compares candidate genres, studios, and media type against the local preference profile. It produces:

- Suggested status.
- Optional Like/Dislike preference.
- Optional Watch Later priority.
- Taste compatibility percentage when comparable signals exist.
- Confidence percentage.
- Human-readable rationale.
- Engine identifier and version.

Thresholds and weights are implementation details of engine version 1 and can evolve independently of the presentation layer.

## Recommendation feedback

When a carousel recommendation is shown, the application stores:

- MAL ID.
- Engine identifier and version.
- Candidate metadata snapshot.
- Exact preference-profile snapshot used for scoring.
- Recommendation output and creation timestamp.

The user can:

- **Accept** — apply the suggested status and any suggested preference/priority to My Ledger.
- **Reject** — indicate disagreement with the recommendation.
- **Dismiss** — defer the decision; this is not a negative taste signal.

Feedback is stored independently for Status, Preference, and Watch Later Priority. The history model also supports Modified for future editable recommendations. A later adaptive engine can use these events without depending on the current rules implementation.

## Persistence

Schema migration V005 creates:

- `recommendation_event` for recommendation and feature snapshots.
- `recommendation_feedback` for dimension-specific user responses.

These tables are durable application data. Catalogue cache clearing must not delete them. They are part of the SQLite database and therefore included in database backup and restore.

## Future stages

- **Stage 1:** deterministic rules and durable event/feedback collection.
- **Stage 2:** adapt the preference profile and scoring from accepted, rejected, dismissed, and modified field-level feedback.
- **Stage 3:** optionally evaluate a local trained model or external LLM integration if the accumulated data and measured benefit justify it.

Any future model must preserve the local-only recommendation boundary unless the user explicitly approves a change to that requirement.
