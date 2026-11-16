# Incremental Catalogue UX

**Branch:** `feature/incremental-catalogue-ux`  
**Base:** `develop`  
**Scope:** Catalogue card reconciliation and poster image reuse

## Observed cause

Before this change, `CatalogueView.loadCards()` cleared the complete `FlowPane` and called `createCard()` for every result. The ledger-change listener called `loadCards()` for every open seasonal tab. Consequently, a single ledger mutation recreated unrelated card nodes and poster `ImageView` instances.

## Implemented approach

### Stable catalogue card nodes

- Each open seasonal tab retains a card cache keyed by MAL ID.
- `loadCards()` reconciles the current query result against that cache.
- A card node is reused when its preferred title, metadata JSON, and ledger status have not changed.
- New or changed anime receive a new card node; cards no longer in the visible result set are detached.
- The child list is reconciled with targeted insertions, removals, and moves rather than clearing and repopulating the entire `FlowPane`.
- Cards removed temporarily by a search or the “Hide anime already in My Ledger” filter remain cached in that tab and can be reused when they become visible again.

The existing ledger-change event remains the notification mechanism. Its listener still asks each open tab to reconcile its query result, but the reconciliation now preserves unaffected card nodes. This also keeps the existing repository/search rules authoritative for blacklist and filter eligibility.

### Shared poster image cache

- `MalImageLoader` now shares JavaFX `Image` instances across catalogue cards and tabs, keyed by the validated MAL image URL.
- Concurrent card requests for the same URL reuse the same in-flight JavaFX background-loading image.
- The cache is access-ordered and bounded to 120 entries.
- Failed images are evicted so a later attempt can retry; existing preferred/fallback URL behaviour and MAL-host validation remain intact.
- The cache is in-memory only; no database schema or persistent image files are introduced.

## Behaviour retained

- MAL ID remains the stable anime identity.
- The existing `LedgerChangeEvent` publication and repository filtering remain unchanged.
- Search, refresh, hide-ledger filtering, and blacklist changes continue to use the existing catalogue service/repository.
- The existing asynchronous JavaFX image loading and placeholder behaviour remain in place.

## Verification checklist

1. Add one anime to the ledger and confirm unrelated card nodes and poster images remain unchanged.
2. Add an anime as `BLACKLISTED` and confirm only that anime disappears from every open catalogue tab.
3. Change an anime from `BLACKLISTED` to a non-blacklisted status and confirm it reappears when it belongs to that tab's cached season and matches its query.
4. Toggle “Hide anime already in My Ledger” and confirm unaffected cards are retained.
5. Search and clear a search; confirm previously visible cards are reused.
6. Refresh a season with unchanged results; confirm existing card nodes and loaded poster images are retained.
7. Refresh with changed metadata; confirm only cards with changed displayed data are replaced.
8. Open two seasons containing the same MAL poster URL and confirm the shared cache returns the same in-flight/loaded `Image` instance.
9. Exercise failed preferred and fallback image URLs; confirm placeholder behaviour and failed-entry eviction.
10. Run the complete Maven test suite and manually inspect the Catalogue in the JavaFX application.

## Limitations

The image cache is bounded by entry count, not decoded image byte size. A future refinement may use weighted/byte-aware eviction if profiling shows memory pressure. The per-tab card cache is scoped to the lifetime of the open tab and retains cards hidden by local search/filtering so they can be reused.
