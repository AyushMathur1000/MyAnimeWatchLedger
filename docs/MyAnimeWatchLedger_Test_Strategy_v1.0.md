# MyAnimeWatchLedger — Test Strategy

**Document ID:** MALW-TEST-001  
**Version:** 1.0  
**Status:** Initial verification baseline  
**Last updated:** 2026-11-14  
**Project:** MyAnimeWatchLedger  
**Authority:** SRS v1.2 (MALW-SRS-001)  
**Related documents:** UI/UX Specification v1.0; Database Design Specification v1.0; Software Architecture Document v1.0; API Integration Specification v1.0; Build and Development Setup v1.0

---

## 1. Purpose

This document defines how MyAnimeWatchLedger is verified against its approved requirements. It establishes test levels, test design principles, fixtures, execution expectations, and merge quality gates.

The SRS v1.2 is the authoritative source for product behaviour. Tests shall retain and reference its stable requirement identifiers. This strategy does not silently change product requirements or treat implementation behaviour as the specification.

The initial release acceptance criteria are the 27 items in SRS Section 11. Approved CHANGE-001 adds Watch Later as the sixth status and defines it as distinct from Hold; tests must verify the distinction and the Catalogue-add default. The Requirements Traceability Matrix is maintained separately in `MyAnimeWatchLedger_Requirements_Traceability_Matrix_v1.0.md`.

## 2. Verification objectives

Verification shall demonstrate that:

1. The permanent ledger behaves independently from disposable catalogue data.
2. Ledger identity, status, persistence, and destructive-operation rules are enforced.
3. Catalogue retrieval, caching, refresh, blacklist filtering, and local search follow the SRS.
4. MAL integration handles incomplete, malformed, failed, and paginated responses without corrupting cached data.
5. SQLite constraints, transactions, migrations, backup, and restore preserve integrity.
6. JavaFX interactions provide the specified feedback and do not block the JavaFX Application Thread for long-running work.
7. The project remains buildable and runnable through Maven and IntelliJ IDEA Community using the approved Java 25 / JavaFX / SQLite stack.

## 3. Test principles

- **Requirement-first:** every acceptance test maps to one or more SRS requirement IDs or an SRS acceptance criterion.
- **Deterministic:** automated tests use fixed data, temporary directories/databases, fake gateways, and controlled HTTP responses.
- **No live API dependency:** ordinary automated tests must not call MAL or depend on its availability.
- **Isolation:** tests must not read, modify, or delete the developer's real application database or credentials.
- **Failure-path coverage:** verify preservation of the last successful cache and permanent ledger when operations fail.
- **No false confidence:** a passing unit test does not establish end-to-end UI acceptance; manual scenarios remain explicit.
- **Migration discipline:** test both a new database and databases created by prior supported schema versions.
- **Safe destructive tests:** use temporary data and verify confirmation/cancellation behaviour.
- **Observable outcomes:** assert persisted state and user-visible outcomes, not merely that methods returned without throwing.

## 4. Test levels and responsibilities

### 4.1 Unit tests

Test pure domain rules and isolated application behaviour.

Examples:
- Status value validation and allowed status set.
- Preferred title fallback: English, Romanized, then Japanese.
- Season chronology across Winter/Spring/Summer/Fall and year boundaries.
- Search tokenization, whitespace normalization, case-insensitive partial matching, and AND semantics across fields.
- MAL DTO-to-domain mapping with missing, null, and unknown fields.
- HTTP error classification and retryability.
- Image URL host/scheme validation and image preference/fallback.
- Application service orchestration with fake repositories/gateways.

Unit tests must not launch JavaFX, access the real filesystem outside a temporary directory, or call MAL.

### 4.2 Persistence and database integration tests

Use a fresh temporary SQLite database for each test or test class. Run the real migration runner and JDBC repositories.

Verify:
- Unique MAL ID and one-row-per-ID behaviour.
- Allowed status constraint and foreign-key enforcement.
- Repository search/filter/sort behaviour.
- Transaction commit and rollback across related repositories.
- Cache deletion isolation from ledger tables.
- Blacklist exclusion at the repository/query boundary.
- Migration from version 0 and each supported prior schema.
- Migration rollback/failure behaviour.
- Backup snapshot validity and restore compatibility, integrity, and rollback.

Tests must close JDBC resources and clean up temporary database, WAL, and SHM files.

### 4.3 API client and gateway tests

Use controlled HTTP responses or a local test server. Do not use the developer's Client ID in automated test source.

Verify:
- HTTPS endpoint/request construction and expected Client-ID header handling.
- Explicit requested fields and request parameters.
- One-page and multi-page seasonal retrieval.
- Sequential pagination and complete-result validation.
- Invalid/untrusted next-page URL rejection.
- Repeated next-page URL detection.
- Empty valid catalogue versus malformed/incomplete response.
- Missing and nullable metadata fields.
- HTTP 400/401/403/404/429/5xx mapping.
- Timeout, DNS/connectivity, invalid JSON, and malformed page handling.
- Bounded retries and Retry-After behaviour where implemented.
- Later-page failure does not return a partial result as complete.

### 4.4 Application workflow integration tests

Compose application services with real temporary persistence and fake gateways.

Verify:
- Successful refresh atomically updates metadata, searchable text, seasonal membership, and fetch state.
- A failed refresh preserves the prior successful catalogue.
- Ledger title synchronization updates only the preferred title and does not change status, date added, or membership.
- Catalogue retrieval never inserts a ledger record implicitly.
- Blacklist changes affect all catalogue query paths immediately.
- Cache clearing removes catalogue/search/tab restoration data while retaining ledger rows.
- Search history reopens cached results without unnecessary network calls.
- Open-tab restoration retains eligible tabs and tab-local query state.
- Duplicate operations are guarded and long-running operations are asynchronous.

### 4.5 UI and manual acceptance tests

JavaFX controls and window interactions require manual acceptance where automated UI infrastructure is not justified.

Manual scenarios must verify:
- Main navigation and My Ledger default landing screen.
- Ledger table, filters, sorting, contextual actions, and confirmation prompts.
- Catalogue card presentation, missing poster placeholder, detail window, MAL link action.
- Multiple independent seasonal tabs and tab-local queries.
- Search History reopen behaviour.
- Settings: Client ID configuration, cache clearing, backup and restore.
- Progress/error/success feedback and responsiveness during slow operations.
- Window size/maximize restoration where supported.

Do not claim UI acceptance from service-level tests alone.

## 5. Test data and fixtures

Use small, explicit fixtures with stable MAL IDs and intentionally varied fields.

Recommended fixture set:
- Anime A: English title, alternative titles, synopsis, genres, studio, score, poster.
- Anime B: Romanized title only.
- Anime C: Japanese title only.
- Anime D: missing poster, synopsis, score, episode count, and optional lists.
- Anime E: searchable terms split across different fields to prove AND semantics.
- Anime F: matches only one query token and must be excluded.
- Anime G: Blacklisted and present in cached seasonal membership.
- Two seasons sharing one MAL ID to prove metadata deduplication.
- Ledger records spanning all five statuses and distinct date-added/last-modified values.

Fixture metadata must not be copied from live MAL responses unless the test is explicitly a manual smoke test.

## 6. Requirement-oriented test coverage

The traceability matrix lists each SRS requirement and its verification method. At minimum, test design shall cover these high-risk invariants:

| Risk / invariant | Required verification |
|---|---|
| Ledger/cache separation | Clear cache and refresh operations leave ledger rows and statuses unchanged |
| MAL ID identity | Duplicate insert/upsert cannot create a second ledger row |
| Blacklist exclusion | Repository results exclude blacklisted IDs for fresh, cached, history, and search paths |
| AND search | Every token must match somewhere in the same anime's searchable metadata |
| Refresh atomicity | Failure at any page or persistence step leaves prior successful cache usable |
| Title synchronization | Only title changes; status/date-added/ledger membership remain stable |
| Offline use | Ledger and cached catalogue reads work with MAL unavailable |
| Migration safety | Failed migration rolls back and unsupported newer schema is rejected |
| Backup/restore safety | Invalid/newer backup rejected before active DB changes; failed replacement restores prior DB |
| UI responsiveness | Slow API/database operations do not block JavaFX Application Thread |

## 7. Initial test inventory and audit limitations

The complete `src/test/java` tree was enumerated on `develop`. It contains 30 test classes:

| Area | Existing test classes |
|---|---|
| Application bootstrap/execution/configuration | `ApplicationInitializerTest`, `BackgroundTaskExecutorTest`, `MalApiConnectionTest`, `MalClientConfigurationStoreTest` |
| Application services | `AnimeDetailServiceTest`, `AnimeDetailTabServiceTest`, `ApplicationServicesTest`, `CatalogueRefreshServiceTest`, `CatalogueSearchServiceTest`, `CatalogueTabServiceTest`, `LedgerServiceEventTest`, `SeasonNavigatorTest` |
| MAL infrastructure | `MalApiAnimeDetailGatewayTest`, `MalApiCatalogueGatewayTest`, `MalApiClientTest`, `MalApiFieldsTest`, `MalResponseMapperTest`, `MalSeasonalCatalogueReaderTest`, `ResilientMalApiTransportTest` |
| Persistence | `DatabaseBackupServiceTest`, `DatabaseManagerTest`, `DatabaseMigrationRunnerTest`, `DatabaseRestartPersistenceTest`, `JdbcRepositoryIntegrationTest` |
| Opt-in live integration | `MalCatalogueLiveSmokeTest` |
| Presentation/state | `MalImageLoaderTest`, `LedgerFilterTest`, `MainNavigationTest`, `WindowStateStoreTest` |

Notable inspected coverage:
- `CatalogueRefreshServiceTest`: successful refresh, valid empty result, gateway failure preserving previous membership/metadata/timestamp, database failure rollback, failure after ledger title synchronization rollback, no implicit ledger insertion, blacklist exclusion, and conservative metadata merge.
- `ApplicationServicesTest`: ledger add/duplicate prevention/status/rename/search/removal; catalogue AND search and Unicode normalization; season navigation; open-tab restoration/reorder/close.
- `JdbcRepositoryIntegrationTest`: ledger CRUD/title search, blacklist filtering plus AND token repository query, tab persistence, multi-repository transaction rollback, cache-clear ledger protection, invalid JSON rejection, and failed-fetch preservation.
- `DatabaseMigrationRunnerTest`: initial schema, version-1 upgrade preserving ledger rows, idempotence, newer-version rejection, migration rollback, and cache/ledger separation.
- `MalResponseMapperTest`: title preference/fallback, nullable fields, unknown fields/enums, required identity/title, searchable projection, and conservative merge.
- `MalApiCatalogueGatewayTest`: endpoint fields, mapping, pagination completion, and invalid year rejection.
- `DatabaseBackupServiceTest`: backup/restore round trip, corrupt backup rejection without active-data change, and destination overwrite protection.

The live smoke test is explicitly opt-in with `-Dmal.live=true`; it is not part of the ordinary deterministic test requirement. The inventory identifies test classes, not a guarantee that every assertion or branch is sufficient. The matrix remains the place to record precise requirement-level evidence and remaining gaps.

## 8. Prioritization

### P0 — Data integrity and destructive safety
- Ledger persistence and identity.
- Ledger/cache isolation.
- Transaction rollback and refresh preservation.
- Blacklist enforcement.
- Migration safety.
- Backup/restore compatibility, atomic replacement, and rollback.

### P1 — Core product behaviour
- Status transitions, ledger search/filter/sort.
- Catalogue retrieval and pagination.
- Search semantics.
- Tabs/history/cache reuse and restoration.
- Title synchronization.
- Offline behaviour.

### P2 — Presentation and resilience
- Missing metadata/poster rendering.
- UI feedback and window-state persistence.
- Error-message quality and retry affordances.
- Performance checks with realistic local data volumes.

P0 failures block merge. P1 failures block release acceptance. P2 failures are tracked and prioritized based on impact; requirements marked mandatory remain release blockers.

## 9. Execution procedure

For each feature branch:

1. Pull the latest `develop` and create a feature branch.
2. Run `mvn clean test`.
3. Run focused tests for the changed domain/service/repository.
4. Add or update tests for every changed requirement.
5. Perform the relevant manual UI/API smoke scenarios.
6. Review test output and investigate all failures; do not suppress or weaken assertions merely to obtain a green build.
7. Update the traceability matrix and defect/gap register.
8. Open a PR with a concise summary, requirement IDs, test evidence, and known limitations.
9. Merge only after required checks and manual verification are complete.

Release-candidate verification additionally includes a clean checkout/build, startup against a fresh database, startup/migration against a supported previous database, offline smoke test, and backup/restore smoke test.

## 10. Merge quality gates

A feature PR is merge-ready only when:

- [ ] `mvn clean test` passes on the feature branch.
- [ ] New/changed behaviour has automated tests at the appropriate level.
- [ ] Relevant SRS requirement IDs are included in PR notes and traceability is updated.
- [ ] No automated test requires live MAL availability or personal credentials.
- [ ] Persistence changes verify transaction boundaries and ledger/cache isolation.
- [ ] Schema changes include immutable forward migration resources and migration tests.
- [ ] Backup/restore or other destructive operations demonstrate safe failure behaviour.
- [ ] UI changes are manually exercised where automated coverage is absent.
- [ ] No known P0 defect remains open.
- [ ] The branch is based on current `develop`; no direct commits to `develop`.

## 11. Defect reporting

Every defect record should include:
- Defect ID and severity (P0/P1/P2/P3).
- Related SRS requirement/acceptance criterion.
- Preconditions and fixture data.
- Reproduction steps.
- Expected result from the source specification.
- Actual result and relevant logs/stack trace.
- Affected version/branch/commit.
- Regression test added or planned.

A bug fix is incomplete until the regression test reproduces the original failure and passes after the fix.

## 12. Change control

If implementation and the approved SRS disagree, record the discrepancy. Do not silently modify test expectations to match implementation. Product-behaviour changes require an approved SRS update and a corresponding traceability/test update.

The SRS remains authoritative. Database Design governs physical persistence details; UI/UX governs screen and interaction details; Software Architecture governs component boundaries and threading; API Integration governs MAL request/mapping/pagination/error contracts within the SRS scope.

## 13. Change record

| Version | Date | Description |
|---|---|---|
| 1.0 | 2026-11-14 | Initial test strategy and merge quality gates based on SRS v1.1 and supporting project design specifications. |
| 1.1 | 2026-11-14 | Updated governing requirements reference to SRS v1.2 and added verification expectations for approved CHANGE-001 (six statuses and Watch Later default). |
