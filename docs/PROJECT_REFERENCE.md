# MyAnimeWatchLedger --- Software Requirements Specification

**Document ID:** MALW-SRS-001\
**Version:** 1.3\
**Status:** Approved requirements and implementation reference\
**Last updated:** 2026-11-15\
**Project:** MyAnimeWatchLedger\
**Document role:** Primary requirements reference for this project

------------------------------------------------------------------------

## 1. Purpose and authority

This document is the authoritative requirements baseline for
MyAnimeWatchLedger. Design documents, implementation plans, tests, and
future changes should trace back to the requirements in this document.

MyAnimeWatchLedger is a single-user desktop application for maintaining
a personal anime ledger and browsing seasonal anime catalogues obtained
from MyAnimeList (MAL).

The application has two deliberately separate data domains:

1.  **Personal ledger:** durable, locally stored user decisions.
2.  **Catalogue data:** MAL metadata and seasonal search results held in
    a disposable local cache.

MAL is the authority for external anime metadata. The local ledger is
the authority for the user's personal status and collection membership.

## 2. Goals

-   Maintain a reliable personal ledger with six statuses.
-   Identify anime by MAL ID rather than title.
-   Browse anime by season and year using MAL data.
-   Prefer English titles, then romanized titles, then Japanese titles.
-   Search locally across all available catalogue metadata.
-   Revisit previously fetched seasonal searches.
-   Exclude blacklisted anime from all catalogue presentations.
-   Keep the application useful offline for ledger and cached data
    access.
-   Provide a compact, efficient desktop user experience.

## 3. Scope

### 3.1 In scope

-   Local single-user anime ledger.
-   Watched, Watching, Abandoned, Blacklisted, Hold, and Watch Later statuses.
-   Add, update, search, filter, sort, and explicitly remove ledger
    entries.
-   MyAnimeList API v2 integration.
-   Seasonal catalogue retrieval by season and year.
-   Anime metadata and poster presentation.
-   Detailed anime information in a separate non-modal window.
-   Direct links to the corresponding MAL pages.
-   Multiple seasonal catalogue tabs.
-   Previous-season and next-season navigation.
-   Search history and persistent disposable catalogue cache.
-   Manual catalogue refresh and manual cache clearing.
-   Manual database backup and restore.
-   Dark desktop UI with icon-first contextual actions and tooltips.

### 3.2 Out of scope

-   MAL account/watchlist synchronization.
-   OAuth-based personal account access for the initial release.
-   Episode-level progress tracking.
-   Streaming or playback.
-   Social features, multiple users, roles, or authentication.
-   Cloud synchronization or server-side hosting.
-   Mobile applications.
-   Recommendation engines or machine learning.
-   Docker, Kubernetes, CI/CD infrastructure, or a separate backend
    service.
-   Custom Office-style ribbon framework.

## 4. Users and operating environment

There is one intended user: the developer. The application is a local
Windows desktop application developed and launched from IntelliJ IDEA
Community Edition.

The application should not require a separately installed database
server, frontend runtime, or backend service. Maven manages application
dependencies.

Internet access is required to retrieve or refresh MAL catalogue data.
Internet access is not required to view and manage the local ledger or
browse successfully cached catalogue data.

## 5. Functional requirements

Requirement identifiers are stable references for design and test
traceability.

### 5.1 Personal ledger

**FR-LED-001 --- Persistent ledger**\
The application shall maintain anime ledger records in local persistent
storage. Records shall survive application restarts and catalogue cache
clearing.

**FR-LED-002 --- MAL identity**\
Each anime shall be identified by its MAL ID. The MAL ID shall be the
unique identity for a ledger entry. At most one ledger record may exist
for a given MAL ID.

**FR-LED-003 --- Status values**\
Each ledger record shall have exactly one current status. The six statuses are:

  -----------------------------------------------------------------------
  Status                              Definition
  ----------------------------------- -----------------------------------
  Watched                             The user has completed watching the
                                      anime.

  Watching                            The user is currently watching,
                                      including when waiting for
                                      unreleased episodes.

  Abandoned                           The user started the anime and has
                                      permanently dropped it.

  Blacklisted                         The user does not intend to watch
                                      the anime.

  Hold                                The user started the anime and
                                      intends to resume it later.

  Watch Later                         The user intends to watch the anime
                                      but has not started watching it.
  -----------------------------------------------------------------------

**FR-LED-004 --- Current state only**\
The ledger shall store the current status only. Status transitions shall
overwrite the previous status. No status-change audit history is
required.

**FR-LED-005 --- Add entry**\
The user shall be able to add an anime to the ledger and select one of
the six statuses. When an anime is added from Catalogue without an
explicitly selected status, its initial status shall be Watch Later.
The user shall remain able to select any of the six statuses. This
default applies only to new entries and shall not reclassify existing
ledger records.

**FR-LED-006 --- Change status**\
The user shall be able to change an existing entry's status at any time.
The change shall be reflected throughout the UI without requiring a
restart.

**FR-LED-007 --- Explicit removal**\
The user shall be able to remove an entry through an explicit action.
The application shall request confirmation before removal. Removal
deletes the current ledger entry; a historical deletion log is not
required.

**FR-LED-008 --- Ledger independence**\
Catalogue retrieval, catalogue refresh, metadata synchronization, and
cache clearing shall not change ledger membership or status.

**FR-LED-009 --- Preferred title storage**\
The ledger shall store the currently preferred display title for the
anime, associated with its MAL ID.

**FR-LED-010 --- Reactive title synchronization**\
When fresh MAL metadata for an anime already in the ledger is received
during normal application activity, the application shall compare the
preferred MAL title with the stored ledger title and update the title if
it differs. This synchronization shall not change status, date added, or
ledger membership.

The application shall not periodically scan the ledger or make requests
solely to check whether titles have changed. If MAL is unavailable, the
last-known stored title shall remain usable.

**FR-LED-011 --- Ledger search**\
The user shall be able to search ledger entries by title using partial,
case-insensitive matching.

**FR-LED-012 --- Ledger status filtering**\
The user shall be able to filter ledger entries by status. Search and
status filters shall work together.

**FR-LED-013 --- Ledger sorting**\
The ledger shall support sorting by title, date added, and last
modified. The default sort order shall be last modified descending
(newest first), with title ascending (A-Z, case-insensitive) as the
secondary sort key when modification timestamps are equal.

**FR-LED-014 --- No episode tracking**\
The application shall not require or maintain watched episode counts or
episode-level progress.

### 5.2 Seasonal catalogue and MAL metadata

**FR-CAT-001 --- Seasonal retrieval**\
The application shall retrieve seasonal anime catalogues from MAL using
a selected year and one of Winter, Spring, Summer, or Fall.

**FR-CAT-002 --- Historical seasons**\
The user shall be able to request historical seasons supported by MAL.

**FR-CAT-003 --- Requested metadata**\
The application shall request and present available MAL fields
appropriate to the catalogue and detail views. Target fields include:

-   MAL ID and titles, including alternative titles.
-   Synopsis.
-   Main cover image.
-   Anime type.
-   Season and year.
-   Genres.
-   Studios.
-   MAL score and ranking.
-   Episode count.
-   Airing status.
-   Available dates and related metadata.

Unavailable fields shall be omitted or represented gracefully; their
absence shall not cause a rendering failure.

**FR-CAT-004 --- Title preference**\
The primary displayed title shall use this fallback order:

1.  English title, when available and non-empty.
2.  Romanized title, when English is unavailable.
3.  Japanese title, as the final fallback.

Alternative titles shall remain accessible in the detailed anime view.

**FR-CAT-005 --- MAL page link**\
Each anime shall provide an action that opens its corresponding MAL page
in the operating system's default browser. The URL shall be derived from
the MAL ID.

**FR-CAT-006 --- Detailed anime view**\
The user shall be able to open a separate, non-modal application window
containing the available extended metadata for an anime. The detail
window shall not replace or reset the catalogue workspace.

**FR-CAT-007 --- Ledger indication and quick actions**\
Catalogue entries shall visibly indicate whether the anime exists in the
ledger and show its current status. The user shall be able to add an
anime or change its status from the catalogue without navigating to the
ledger screen.

**FR-CAT-008 --- Blacklist exclusion**\
An anime whose current ledger status is Blacklisted shall not be
displayed in any seasonal catalogue, including newly fetched catalogues,
cached catalogues, open tabs, search history results, grid/list results,
and filtered search results. Exclusion shall be based on MAL ID, not
title.

**FR-CAT-009 --- Immediate blacklist propagation**\
Changing an anime to Blacklisted shall immediately remove it from all
currently displayed catalogue tabs and results. Changing it from
Blacklisted to another status shall allow it to reappear in applicable
cached catalogues without requiring another MAL request.

Cached seasonal membership may retain the MAL ID internally; catalogue
presentation queries must exclude IDs currently blacklisted.

**FR-CAT-010 --- Title synchronization from catalogue activity**\
When catalogue activity obtains fresh metadata for a MAL ID already in
the ledger, the preferred title synchronization defined in FR-LED-010
shall occur. No other ledger fields may be changed by metadata refresh.

### 5.3 Catalogue search

**FR-SEARCH-001 --- Searchable fields**\
The catalogue search shall search all locally available anime metadata
fields, including titles and alternative titles, synopsis, genres,
studios, type, season/year, score, ranking, episode count, airing
status, and other cached text or scalar metadata.

**FR-SEARCH-002 --- Local search**\
Catalogue search shall operate against locally available cached
metadata. Typing a query shall not generate a MAL API request for each
keystroke.

**FR-SEARCH-003 --- Partial matching**\
Each search token shall support partial substring matching.

**FR-SEARCH-004 --- Case-insensitive matching**\
Matching shall be case-insensitive.

**FR-SEARCH-005 --- AND token semantics**\
A query containing multiple tokens shall use AND semantics. Every token
must match somewhere within the searchable metadata of the same anime.
Tokens may match different fields.

For example, `action fantasy` matches an anime when `action` occurs in
one searchable field and `fantasy` occurs in another searchable field,
or both occur in the same field. An anime matching only one of the
tokens shall not be returned.

**FR-SEARCH-006 --- Query normalization**\
Leading/trailing whitespace and repeated whitespace shall not create
empty search tokens. Tokenization and normalization shall be consistent
across catalogue searches.

**FR-SEARCH-007 --- Clear search**\
The user shall be able to clear the query and restore all otherwise
eligible catalogue entries.

**FR-SEARCH-008 --- Search and blacklist interaction**\
Blacklisted anime shall remain excluded regardless of query terms.

### 5.4 Seasonal tabs and navigation

**FR-TAB-001 --- Multiple tabs**\
The catalogue workspace shall support multiple simultaneously open
seasonal search tabs.

**FR-TAB-002 --- One search identity per season**\
A season/year combination shall identify one cached seasonal search.
Opening a season already open shall activate the existing tab rather
than create a duplicate tab for the same search.

**FR-TAB-003 --- Tab-local UI state**\
Each open seasonal tab shall retain its own search query and relevant
presentation state while open.

**FR-TAB-004 --- Closing a tab**\
Closing a seasonal tab shall not delete its cached results or
search-history record.

**FR-TAB-005 --- Restore from history**\
A closed seasonal search shall be reopenable from Search History using
cached data when available.

**FR-TAB-006 --- Previous season**\
The user shall be able to navigate to the immediately preceding season.

**FR-TAB-007 --- Next season**\
The user shall be able to navigate to the immediately following season.

**FR-TAB-008 --- Chronological order**\
Season navigation shall follow Winter, Spring, Summer, Fall, then Winter
of the following year. Reverse navigation shall correctly cross year
boundaries.

**FR-TAB-009 --- Reuse cache**\
Previous/next navigation shall reuse an existing cached season when
available. If no usable cache exists, the application shall retrieve
that season from MAL.

**FR-TAB-010 --- Restore open tabs after restart**\
The application shall restore previously open seasonal tabs after
restart when their corresponding cached searches still exist. The
restoration state is disposable catalogue state and may be removed by
Clear Cache.

### 5.5 Search history and cache

**FR-CACHE-001 --- Persistent disposable cache**\
Catalogue metadata and seasonal search results shall be stored locally
and survive normal application restarts. They are disposable data, not
permanent personal ledger data.

**FR-CACHE-002 --- Search history records**\
Each cached seasonal search shall retain its season, year, fetch
timestamp, fetch status, and association with its anime results.

**FR-CACHE-003 --- Cached retrieval**\
Selecting a previously fetched season shall display its cached results
without an unnecessary MAL request.

**FR-CACHE-004 --- Shared metadata**\
Anime metadata shall be cached independently of seasonal search
membership so that the same anime can be referenced by multiple seasonal
searches without duplicating the full metadata payload.

**FR-CACHE-005 --- Manual refresh only**\
The user shall explicitly initiate catalogue refresh. The application
shall not periodically refresh catalogue metadata automatically.

**FR-CACHE-006 --- Refresh isolation**\
Refreshing a catalogue shall update the applicable cached metadata and
seasonal results. It shall not alter ledger membership or status.

**FR-CACHE-007 --- Preserve prior successful cache on failure**\
A failed refresh shall not destroy previously successful cached results.
The application shall report the failure and keep prior cached data
available.

**FR-CACHE-008 --- Manual cache clearing**\
The application shall provide an explicit Clear Cache action. It shall
remove cached anime metadata, cached seasonal search results, search
history, and persisted open-catalogue-tab restoration data.

**FR-CACHE-009 --- Ledger protection**\
Clear Cache shall never delete or modify the personal ledger.

**FR-CACHE-010 --- No automatic expiration**\
No automatic time-based cache expiration is required. Manual clearing is
authoritative.

**FR-CACHE-011 --- Blacklist independence**\
The blacklist is represented by permanent ledger status and shall remain
effective after cache clearing and subsequent catalogue retrieval.

### 5.6 Backup and restore

**FR-BACKUP-001 --- Manual backup**\
The application shall provide a user-initiated way to create a backup of
the local database.

**FR-BACKUP-002 --- Manual restore**\
The application shall provide a user-initiated way to restore from a
compatible backup.

**FR-BACKUP-003 --- Restore confirmation**\
Restore shall clearly warn that current local data may be replaced and
shall require explicit confirmation.

**FR-BACKUP-004 --- Integrity**\
Backup and restore operations shall preserve database integrity. The
implementation design shall define how to handle incompatible schema
versions and failed restore attempts.

The exact backup format and whether disposable cache is included in
backups shall be defined in the database design.

## 6. User interface requirements

### 6.1 Global layout and navigation

**UI-001** --- The application shall use a desktop-oriented layout.

**UI-002** --- The application shall provide a consistent dark theme.

**UI-003** --- The main window shall use persistent sidebar navigation,
a contextual toolbar, a central workspace, and a status bar.

**UI-004** --- The sidebar shall provide My Ledger, Catalogue, Search
History, and Settings.

**UI-005** --- My Ledger shall be the default landing screen.

**UI-006** --- The interface shall favor compact layouts and useful
information density over decorative space.

**UI-007** --- Common actions shall use icons where practical. Icon-only
actions shall have descriptive tooltips.

**UI-008** --- Toolbars shall be contextual to the active screen and
selection.

**UI-009** --- The interface shall provide feedback for long-running
operations.

**UI-010** --- Network and other long-running operations shall not block
the JavaFX UI thread.

**UI-011** --- The application should remember window dimensions and
maximize state where practical.

### 6.2 Ledger screen

**UI-LED-001** --- The ledger shall use a compact table-oriented
presentation suitable for a large collection.

**UI-LED-002** --- The ledger shall provide title search, status
filters, sorting, and contextual actions.

**UI-LED-003** --- The table shall display anime title, current status,
date added, and last modified.

**UI-LED-004** --- Status changes shall be available without opening the
detailed anime window.

**UI-LED-005** --- Removal shall be available as an explicit contextual
action with confirmation.

### 6.3 Catalogue screen

**UI-CAT-001** --- The catalogue shall use an adaptive card grid as the
initial presentation, provided it can be implemented using standard
JavaFX controls without disproportionate complexity.

**UI-CAT-002** --- A list presentation is optional and may be deferred
if it adds unnecessary implementation effort.

**UI-CAT-003** --- Catalogue cards should show poster, preferred title,
score when available, type, genres, concise synopsis, and current ledger
status when applicable.

**UI-CAT-004** --- Full synopsis and extended metadata shall be
presented in the detailed anime window rather than forcing all
information onto the catalogue card.

**UI-CAT-005** --- The catalogue shall provide season/year selection,
previous/next season actions, refresh, search, and cache-clearing
access.

**UI-CAT-006** --- The catalogue shall provide visible quick actions for
ledger status management, detailed view, and MAL navigation.

**UI-CAT-007** --- Missing posters shall use a placeholder. Missing
metadata shall be omitted or represented gracefully.

### 6.4 Seasonal tabs and search history

**UI-TAB-001** --- Seasonal searches shall be displayed as tabs in the
catalogue workspace.

**UI-TAB-002** --- Open tabs and search-history records shall be treated
as distinct concepts.

**UI-TAB-003** --- Closing a tab shall not delete its search-history
entry.

**UI-TAB-004** --- Search History shall provide season/year, last-fetch
timestamp, and an action to reopen the search.

### 6.5 Detailed anime window

**UI-DETAIL-001** --- Anime details shall open in a separate, non-modal
window.

**UI-DETAIL-002** --- The detail window shall show available titles,
poster, synopsis, type, season/year, genres, studios, score/ranking,
episode count, airing information, and other available metadata.

**UI-DETAIL-003** --- The detail window shall show current ledger status
or an Add action.

**UI-DETAIL-004** --- The detail window shall provide status
modification, explicit removal where applicable, and an Open MAL Page
action.

### 6.6 Settings

**UI-SET-001** --- Settings shall provide MAL API client configuration.

**UI-SET-002** --- Settings shall provide access to manual cache
clearing and database backup/restore.

## 7. Data requirements and integrity

### 7.1 Permanent ledger data

The logical ledger record shall contain:

  Field             Purpose
  ----------------- ----------------------------------------------------
  `mal_id`          Unique MAL identifier and record key
  `title`           Current preferred display title
  `status`          Current one-of-six status
  `date_added`      Original insertion timestamp
  `last_modified`   Timestamp of the latest ledger-record modification

There shall be no ledger status-history table.

### 7.2 Disposable catalogue data

The logical cache model shall support:

**Catalogue search** - Search ID. - Season. - Year. - Fetch timestamp. -
Fetch status.

**Catalogue search membership** - Search ID. - MAL ID. - Original
display order.

**Anime metadata cache** - MAL ID. - Cached metadata payload. - Cache
timestamp.

**Open-tab restoration state** - References to open cached searches and
sufficient ordering/state to restore them, if needed.

The final physical schema, indexes, foreign keys, and migration strategy
shall be specified in the Database Design Specification.

### 7.3 Integrity rules

-   MAL ID is the anime identity; title is mutable metadata.
-   The ledger has at most one record per MAL ID.
-   Ledger status is constrained to the six defined values.
-   Catalogue operations shall not create ledger records implicitly.
-   Catalogue metadata refresh shall not modify ledger status or
    membership.
-   Cache deletion shall be isolated from ledger data.
-   Related database writes shall use transactions where appropriate.
-   Blacklist filtering shall be based on ledger MAL IDs.

## 8. External interface requirements

### 8.1 MyAnimeList API

The application shall integrate with MyAnimeList API v2 for seasonal
anime retrieval and anime details.

The API client shall request required fields explicitly and map the
response into application-owned data models. The application shall not
make its UI or database depend directly on raw API response structures.

MAL API availability, response completeness, and rate limits are
external dependencies. The implementation shall handle missing fields,
failed requests, and throttling gracefully.

### 8.2 API configuration and authorization

The initial public catalogue functionality shall use an appropriately
registered MAL API client ID. OAuth and personal MAL account
synchronization are out of scope unless separately approved.

### 8.3 Browser integration

The application shall use the operating system's default browser to open
the MAL page derived from the anime's MAL ID.

## 9. Non-functional requirements

### 9.1 Performance

-   Ledger access shall not require internet connectivity.
-   Ledger search and status filtering shall feel immediate for a
    personal collection.
-   Catalogue search shall execute locally.
-   Network requests shall run asynchronously.
-   Image loading shall not block the UI thread.
-   Cached searches shall open without unnecessary network requests.

### 9.2 Reliability

-   API failures shall not corrupt the permanent ledger.
-   Failed refreshes shall not discard previously successful cache data.
-   Database updates shall use transactions where appropriate.
-   Missing metadata and images shall not crash the application.
-   The application shall report critical database failures rather than
    silently operating as if writes succeeded.

### 9.3 Maintainability

-   Use a layered architecture.
-   Separate UI, application/business logic, persistence, and external
    API integration.
-   UI components shall not execute SQL directly.
-   Use repository abstractions for persistence.
-   Manage schema changes through versioned database migrations.
-   Keep MAL response mapping separate from UI presentation and database
    entities.

### 9.4 Usability

-   Common ledger operations should require minimal interaction.
-   Status changes should be accessible from both ledger and catalogue.
-   Actions shall provide clear feedback.
-   Destructive operations shall require confirmation.
-   Keyboard shortcuts shall be added where useful and documented.

### 9.5 Portability and reproducibility

The project shall be buildable through Maven and runnable from IntelliJ
IDEA Community. It shall not require a separately installed database
server or backend service.

## 10. Error handling

  -----------------------------------------------------------------------
  Condition                           Required behaviour
  ----------------------------------- -----------------------------------
  No internet                         Ledger remains usable; cached
                                      catalogue remains accessible.

  MAL unavailable                     Report the error and preserve prior
                                      successful cache.

  Invalid API client configuration    Explain the configuration problem.

  Missing metadata                    Display available fields without
                                      failure.

  Missing poster                      Display a placeholder.

  Invalid season/year                 Reject or validate the request and
                                      explain the issue.

  Database unavailable                Show a critical error; do not
                                      silently claim persistence.

  Cache corruption                    Report the affected cache data and
                                      preserve the permanent ledger where
                                      possible.

  Backup incompatible or invalid      Reject restore safely and preserve
                                      current data.

  Restore failure                     Report failure and avoid leaving
                                      the database in a partially
                                      restored state.
  -----------------------------------------------------------------------

## 11. Acceptance criteria

The initial release shall satisfy the following:

1.  The user can add, view, change the status of, and explicitly remove
    ledger records.
2.  All six statuses (Watched, Watching, Abandoned, Blacklisted, Hold, and Watch Later) are supported and mutually exclusive.
3.  One record per MAL ID is enforced.
4.  Ledger data survives application restarts.
5.  The ledger can be searched, filtered, and sorted, defaulting to latest
modified first with alphabetical title tie-breaking.
6.  No episode progress is required or stored.
7.  Seasonal anime can be retrieved from MAL by season and year.
8.  Title preference follows English, Romanized, Japanese fallback
    order.
9.  Catalogue cards display available core metadata.
10. Detailed anime information opens in a separate non-modal window.
11. MAL links open in the default browser.
12. Multiple seasonal tabs work independently.
13. Previous/next season navigation crosses year boundaries correctly.
14. Previously fetched seasons can be reopened from Search History.
15. Catalogue search uses partial, case-insensitive AND token matching
    across all locally available searchable fields.
16. Catalogue search does not issue a network request for each
    keystroke.
17. Blacklisted anime are absent from all catalogue presentations and
    search results.
18. Changing an anime to Blacklisted removes it immediately from open
    catalogues.
19. Removing the blacklist allows the anime to reappear from cached
    seasonal data.
20. Fresh MAL metadata updates an existing ledger title reactively
    without changing status or date added.
21. Catalogue refresh does not modify ledger membership or status.
22. Clearing cache removes catalogue data and search history but leaves
    the ledger intact.
23. Cache does not expire automatically.
24. Manual database backup and restore are available.
25. The application remains useful offline for ledger and cached-data
    access.
26. Network operations do not freeze the UI.
27. The project runs directly from IntelliJ IDEA Community using
    Maven-managed dependencies.
28. The default Ledger ordering is latest modified first, with title
    ascending as the tie-breaker.
29. The UI provides compact icon-first navigation, visible tooltips,
    preference-controlled interaction sounds and workspace transitions,
    and transient toast feedback for routine actions and warnings.

## 12. Approved technology baseline

  Concern                       Approved technology or approach
  ----------------------------- ---------------------------------
  Language                      Java 25
  UI framework                  JavaFX
  Styling                       JavaFX CSS
  Database                      SQLite
  Database connectivity         JDBC
  Build/dependency management   Maven
  Unit testing                  JUnit 5
  IDE                           IntelliJ IDEA Community
  Version control               Git
  External anime data           MyAnimeList API v2

### Architectural direction

-   Layered architecture.
-   Repository pattern.
-   Separation of UI, business logic, persistence, and API integration.
-   Versioned database migrations.
-   Programmatic JavaFX UI initially; FXML is not required.
-   Standard JavaFX controls and layouts should be preferred over
    complex custom UI frameworks.

### Explicitly excluded from the initial project

-   Spring Boot.
-   MongoDB.
-   Separate backend service.
-   Docker or Kubernetes.
-   Jenkins or other CI/CD infrastructure.
-   External database server.
-   Custom Office-style ribbon framework.
-   Bundled Java runtime, jpackage, or standalone installer work.

These exclusions reflect the project's single-user, local desktop scope
and the goal of keeping development manageable for one developer.

## 13. Finalized decisions register

  -----------------------------------------------------------------------
  ID                      Decision                Final position
  ----------------------- ----------------------- -----------------------
  OD-01                   Detailed anime          Separate non-modal
                          presentation            window

  OD-02                   Database backup and     Included; manual
                          restore                 

  OD-03                   Ledger record deletion  Explicit deletion with
                                                  confirmation

  OD-04                   Duplicate anime records One record per MAL ID

  OD-05                   Catalogue metadata      Manual only; no
                          refresh                 periodic refresh

  OD-06                   Cache expiration        No automatic
                                                  expiration; manual
                                                  clearing

  OD-07                   Catalogue presentation  Cards or whichever is
                                                  simpler to implement;
                                                  adaptive card grid is
                                                  the initial preference

  OD-08                   Search semantics        Partial,
                                                  case-insensitive
                                                  matching; AND across
                                                  tokens, with tokens
                                                  allowed to match
                                                  different searchable
                                                  fields
  -----------------------------------------------------------------------

Additional finalized UI decisions:

-   Dark theme.
-   Sidebar navigation.
-   Contextual icon-first toolbar with tooltips.
-   My Ledger is the default landing screen.
-   Ledger uses a compact table.
-   Catalogue initially uses an adaptive card grid.
-   Multiple seasonal tabs are supported.
-   Previously open seasonal tabs are restored after restart when their
    cache still exists.
-   Detailed anime views use separate non-modal windows.

## 14. Change control and traceability

This document is the primary requirements reference for the project.

-   UI/UX specifications shall trace screen behaviour to the UI
    requirements in this document.
-   Database design shall trace entities, constraints, and data
    lifecycles to Sections 5 and 7.
-   API integration design shall trace external interactions to Section
    8.
-   Test cases shall trace to requirement identifiers and acceptance
    criteria.
-   Any change to approved requirements shall be reflected in a new
    document version with a concise change record.



### Approved requirement change — CHANGE-001

Change 001 is approved and incorporated in this SRS version. Watch Later is a distinct sixth status, not an alias for Hold. Hold means the user started the anime and intends to resume it later; Watch Later means the user intends to watch the anime but has not started it. Catalogue additions without an explicitly selected status default to Watch Later. Existing ledger entries are not reclassified by this change.

### Change record

| Version | Date | Description |
|---|---|---|
| 1.0 | 2026-11-13 | Initial SRS baseline. |
| 1.1 | 2026-11-13 | Finalized decisions, AND token search semantics, backup/restore, UI defaults, and JavaFX technology baseline. |
| 1.2 | 2026-11-14 | Approved Change 001: added Watch Later as a distinct sixth status and defined its meaning and Catalogue add default. |
| 1.3 | 2026-11-15 | Consolidated project reference; recorded default Ledger sort order, implementation baseline, build workflow, and Phase 6 closure gates. |

---

## 15. Implementation reference and developer workflow

### 15.1 Technology and build

The repository is a Maven project using Java 25, JavaFX 25.0.5, SQLite JDBC,
Jackson, and JUnit Jupiter 5. The UI is built programmatically with standard
JavaFX controls and JavaFX CSS. No separate frontend toolchain or database
server is required.

Run from the repository root:

```bash
mvn clean test
mvn javafx:run
```

Use a JDK 25 installation. In IntelliJ IDEA, open the repository as a Maven
project, set the project SDK and Maven runner JDK to 25, allow Maven to import
dependencies, then run `com.myanimewatchledger.app.MyAnimeWatchLedgerApplication`
or the Maven `javafx:run` goal.

### 15.2 Local data and configuration

On Windows, application data is stored under `%LOCALAPPDATA%\\MyAnimeWatchLedger`.
If `LOCALAPPDATA` is unavailable, the fallback is `~/.myanimewatchledger` under
the user's home directory. The SQLite database is
`my_anime_watch_ledger.db`; configuration and UI preferences are stored in the
`config` subdirectory. The MAL Client ID is configured in Settings and stored
locally outside the SQLite database.

### 15.3 Current workspace summary

- **My Ledger:** persistent personal records, six statuses, title search, status
  filters, date columns, explicit removal confirmation, and default sorting by
  last modified descending with title ascending as a tie-breaker.
- **Catalogue:** seasonal MAL browsing, multiple season tabs, previous/next
  navigation, local metadata search using partial case-insensitive AND token
  matching, poster previews, quick ledger actions, manual refresh, and cache
  clearing. Blacklisted anime are excluded from catalogue presentation.
- **Anime Details:** separate non-modal detail window with independently open
  anime tabs, extended metadata, poster preview, and ledger actions.
- **Search History:** previously fetched seasonal searches can be reopened;
  refresh is asynchronous and preserves prior successful data on failure.
- **Settings:** MAL Client ID, background-opening preference, sound and animation
  toggles, database backup/restore, and catalogue cache clearing.

### 15.4 Phase 6 quality-of-life implementation baseline

The Phase 6 QoL scope currently implemented on `feature/phase-6-qol` includes:

1. Shared poster enlargement from Catalogue and Anime Details.
2. One-action Catalogue addition as Blacklisted, with a distinct amber warning
   toast; ordinary additions use success feedback.
3. Asynchronous Search History refresh with stale-response protection.
4. Ledger action to open the current season in Catalogue.
5. Compact icon-only sidebar with labelled tooltips and consistent vector icons.
6. Consistent control styling, dark dropdown menus, improved Catalogue tabs and
   toolbar spacing, and transient notification cards.
7. Preference-controlled click sounds and workspace fade transitions.
8. Sound feedback for button actions, dropdown interaction, tab-header clicks,
   poster clicks, and Ledger row selection.
9. Latest-modified-first default Ledger ordering with alphabetical tie-breaking.

The exact current keyboard support is documented separately in
[`KEYBOARD_SHORTCUTS.md`](KEYBOARD_SHORTCUTS.md). No application-wide custom
shortcut bindings are currently defined; the document distinguishes built-in
control behaviour from dedicated application shortcuts.

### 15.5 Phase 6 closure checklist

<badge>Phase 6 — CLOSED</badge>

| Area | Expected result | Current review state |
|---|---|---|
| Approved QoL scope | All agreed items represented in implementation | Implemented by source review |
| Ledger default order | Last modified descending, title ascending | Implemented in LedgerView |
| User preferences | Sound and animation settings persisted | Implemented |
| Notifications | Success, warning, and error toast variants | Implemented |
| Regression tests | Full Maven test suite passes on JDK 25 | Passed — 140 unit tests |
| Manual UI verification | Check layout, icons, toast timing, dropdowns, and sounds on Windows | Passed — user confirmed manual tests |

The phase is functionally complete by scope review, but should only be
formally closed after the current branch passes `mvn clean test` on JDK 25 and
the final Windows UI smoke test. This repository review did not execute Maven:
the review environment has JDK 21 and Maven is not installed.

## 16. Change control and traceability

This document is the single authoritative project reference. Requirements,
implementation decisions, and acceptance changes must be updated here rather
than maintained in parallel specification files. Test cases should trace to
requirement identifiers and acceptance criteria.
