# MyAnimeWatchLedger — Requirements Traceability Matrix

**Document ID:** MALW-RTM-001  
**Version:** 1.0  
**Status:** Initial audit baseline — coverage confirmation in progress  
**Last updated:** 2026-11-14  
**Project:** MyAnimeWatchLedger  
**Authoritative source:** SRS v1.1 (MALW-SRS-001)

---

## 1. Purpose and status definitions

This matrix maps every stable functional/UI requirement identifier and all 27 SRS Section 11 acceptance criteria to a verification approach.

Status meanings:

- **Existing — confirmed:** an inspected test class or user-confirmed manual run provides evidence for the stated scope.
- **Planned:** verification is defined but no test evidence has yet been inspected or recorded.
- **Audit needed:** the implementation/test inventory must be inspected before coverage can be asserted.
- **Manual:** requires a documented manual acceptance scenario unless suitable UI automation is later introduced.

A requirement being implemented is not the same as being tested. No requirement is marked fully covered merely because its feature exists.

## 2. Existing evidence confirmed during initial audit

| Evidence ID | Source | Confirmed scope |
|---|---|---|
| EV-DB-01 | `DatabaseManagerTest` | Database initialization, parent directory creation, independent JDBC connections, foreign-key pragma, busy timeout |
| EV-DB-02 | `DatabaseBackupServiceTest` | Backup/restore round trip, corrupt backup rejection without active-data change, existing destination protection |
| EV-MAN-01 | User verification on Phase 4.4 branch | User reports `mvn clean test` passes and application works; this is branch-level verification, not requirement-by-requirement evidence |

The complete test tree has not yet been enumerated in this initial audit. Other existing test classes may provide coverage; verify them before classifying a planned item as a confirmed gap.

## 3. Functional requirements

### 3.1 Personal ledger

| SRS ID | Requirement area | Verification required | Initial status |
|---|---|---|---|
| FR-LED-001 | Persistent ledger | Integration: create/update, close/reopen database/application, verify record survives; cache clear must not remove it | Audit needed |
| FR-LED-002 | MAL ID identity | Persistence: duplicate MAL ID insert/upsert and unique constraint behaviour | Audit needed |
| FR-LED-003 | Five statuses | Domain + persistence: all five accepted; invalid status rejected; one current status only | Audit needed |
| FR-LED-004 | Current state only | Persistence: status update overwrites current value; no history requirement | Audit needed |
| FR-LED-005 | Add entry | Service + UI: add with each valid status and verify persisted row | Audit needed / Manual |
| FR-LED-006 | Change status | Service + UI: update each status; visible occurrences refresh without restart | Audit needed / Manual |
| FR-LED-007 | Explicit removal | Service + UI: confirm deletes; cancel preserves; verify no history requirement | Audit needed / Manual |
| FR-LED-008 | Ledger independence | Integration: catalogue fetch/refresh/cache clear leave ledger membership/status unchanged | Planned |
| FR-LED-009 | Preferred title storage | Persistence: preferred title is stored and returned for MAL ID | Audit needed |
| FR-LED-010 | Reactive title synchronization | Workflow: fresh metadata changes title only; status, date added, and membership remain unchanged; no periodic scan | Planned |
| FR-LED-011 | Ledger search | Repository: partial, case-insensitive title matching | Audit needed |
| FR-LED-012 | Ledger status filtering | Repository/UI: each status filter; search and status filter combine | Audit needed / Manual |
| FR-LED-013 | Ledger sorting | Repository/UI: title, date added, last modified ordering, including ties | Audit needed / Manual |
| FR-LED-014 | No episode tracking | Schema/model review: no required episode count/progress state or UI workflow | Audit needed |

### 3.2 Seasonal catalogue and MAL metadata

| SRS ID | Requirement area | Verification required | Initial status |
|---|---|---|---|
| FR-CAT-001 | Seasonal retrieval | Gateway integration: request selected year and each season; map complete result | Audit needed |
| FR-CAT-002 | Historical seasons | Gateway: supported historical season request and validation of unsupported values | Planned |
| FR-CAT-003 | Requested metadata | Mapper: verify requested fields and graceful handling of absent/null fields | Planned |
| FR-CAT-004 | Title preference | Unit: English → Romanized → Japanese fallback; empty values treated as unavailable | Planned |
| FR-CAT-005 | MAL page link | Unit/UI: URL derived from MAL ID and opened via default browser action | Manual |
| FR-CAT-006 | Detailed anime view | UI: separate non-modal window; opening does not reset workspace | Manual |
| FR-CAT-007 | Ledger indication and quick actions | UI/workflow: current status or Add action; quick status change reflects in ledger | Manual |
| FR-CAT-008 | Blacklist exclusion | Persistence/workflow: exclude by MAL ID in fresh, cached, open-tab, history, and filtered catalogue paths | Planned |
| FR-CAT-009 | Immediate blacklist propagation | Workflow/UI: Blacklisted removes from all open results; unblacklisting restores from cache without MAL call | Planned / Manual |
| FR-CAT-010 | Title sync from catalogue | Workflow: fresh catalogue metadata updates only preferred ledger title | Planned |

### 3.3 Catalogue search

| SRS ID | Requirement area | Verification required | Initial status |
|---|---|---|---|
| FR-SEARCH-001 | Searchable fields | Integration: searchable projection includes titles, synopsis, genres, studios, type, season/year, score, ranking, episodes, airing and other cached fields | Planned |
| FR-SEARCH-002 | Local search | Service test: query causes no MAL gateway invocation | Planned |
| FR-SEARCH-003 | Partial matching | Unit/repository: substring matches within a field | Planned |
| FR-SEARCH-004 | Case-insensitive matching | Unit/repository: mixed-case query returns same match | Planned |
| FR-SEARCH-005 | AND token semantics | Unit/repository: all tokens required; tokens may match different fields of one anime | Planned |
| FR-SEARCH-006 | Query normalization | Unit: trim and repeated whitespace; no empty tokens | Planned |
| FR-SEARCH-007 | Clear search | UI/service: empty query restores all otherwise eligible records | Manual |
| FR-SEARCH-008 | Search and blacklist interaction | Repository: blacklist exclusion remains effective for every query | Planned |

### 3.4 Seasonal tabs and navigation

| SRS ID | Requirement area | Verification required | Initial status |
|---|---|---|---|
| FR-TAB-001 | Multiple tabs | UI: open multiple season tabs and switch between them | Manual |
| FR-TAB-002 | One search identity per season | Service/UI: reopening same season activates existing tab; no duplicate identity | Planned / Manual |
| FR-TAB-003 | Tab-local UI state | UI: independent query and presentation state per tab | Manual |
| FR-TAB-004 | Closing a tab | Integration: close tab, verify cached search/history remains | Planned |
| FR-TAB-005 | Restore from history | Integration/UI: reopen closed season from cached history without unnecessary MAL request | Planned / Manual |
| FR-TAB-006 | Previous season | Unit/UI: navigate to immediately previous season | Planned / Manual |
| FR-TAB-007 | Next season | Unit/UI: navigate to immediately next season | Planned / Manual |
| FR-TAB-008 | Chronological order | Unit: Winter/Spring/Summer/Fall ordering in both directions across year boundary | Planned |
| FR-TAB-009 | Reuse cache | Service: cached season reused; gateway called only when no usable cache exists | Planned |
| FR-TAB-010 | Restore tabs after restart | Integration/UI: reopen persisted eligible tabs and per-tab query state after restart | Planned / Manual |

### 3.5 Search history and cache

| SRS ID | Requirement area | Verification required | Initial status |
|---|---|---|---|
| FR-CACHE-001 | Persistent disposable cache | Integration: metadata and seasonal results survive restart | Audit needed |
| FR-CACHE-002 | Search history records | Persistence: season/year, timestamp, fetch status, membership persisted | Audit needed |
| FR-CACHE-003 | Cached retrieval | Service: cached season displayed without gateway request | Planned |
| FR-CACHE-004 | Shared metadata | Persistence: same MAL ID referenced by multiple seasons without duplicated metadata row | Planned |
| FR-CACHE-005 | Manual refresh only | UI/service: explicit refresh invokes gateway; no periodic scheduler or implicit refresh | Audit needed |
| FR-CACHE-006 | Refresh isolation | Workflow: refresh changes cache only, not ledger membership/status | Planned |
| FR-CACHE-007 | Preserve prior cache on failure | Workflow: failure before/after later page or during persistence preserves last successful result | Planned |
| FR-CACHE-008 | Manual cache clearing | Integration/UI: remove metadata, seasonal membership, history, open-tab restoration | Planned / Manual |
| FR-CACHE-009 | Ledger protection | Integration: cache clear leaves all ledger rows and statuses unchanged | Planned |
| FR-CACHE-010 | No automatic expiration | Code/config review + time-independent test: cache not removed by age | Audit needed |
| FR-CACHE-011 | Blacklist independence | Integration: blacklist remains after cache clear and subsequent fetch | Planned |

### 3.6 Backup and restore

| SRS ID | Requirement area | Verification required | Initial status |
|---|---|---|---|
| FR-BACKUP-001 | Manual backup | Existing test + UI: user selects destination; snapshot validates; destination not overwritten | Existing — partial (EV-DB-02); UI manual |
| FR-BACKUP-002 | Manual restore | Existing round-trip test + compatibility, migration, and replacement-failure tests | Existing — partial (EV-DB-02) |
| FR-BACKUP-003 | Restore confirmation | UI manual: warning identifies replacement scope; cancel performs no change | Manual |
| FR-BACKUP-004 | Integrity | Existing corrupt-file test + newer schema, missing tables/columns, foreign-key failure, migration failure, rollback and reopen failure tests | Existing — partial; additional cases planned |

## 4. User interface requirements

| SRS ID | Requirement area | Verification required | Initial status |
|---|---|---|---|
| UI-001 | Desktop-oriented layout | Manual visual acceptance | Manual |
| UI-002 | Consistent dark theme | Manual visual acceptance across all screens/windows | Manual |
| UI-003 | Sidebar, toolbar, workspace, status bar | Manual layout/navigation | Manual |
| UI-004 | Four primary navigation destinations | Manual navigation | Manual |
| UI-005 | My Ledger default landing screen | Startup UI test/manual | Manual |
| UI-006 | Compact information-dense layout | Manual visual acceptance | Manual |
| UI-007 | Icons and descriptive tooltips | Manual interaction audit | Manual |
| UI-008 | Contextual toolbars | Manual per-screen/selection audit | Manual |
| UI-009 | Long-operation feedback | Manual with delayed fake operation or controlled slow response | Planned / Manual |
| UI-010 | Non-blocking JavaFX thread | Service/thread test + manual responsiveness test | Planned |
| UI-011 | Window dimensions/maximize persistence | Manual restart test | Manual |
| UI-LED-001 | Compact ledger table | Manual visual/large-list acceptance | Manual |
| UI-LED-002 | Search, filters, sort, contextual actions | Manual plus repository tests | Manual |
| UI-LED-003 | Required ledger columns | Manual column/content check | Manual |
| UI-LED-004 | Status change without detail window | Manual workflow | Manual |
| UI-LED-005 | Removal with confirmation | Manual confirm/cancel workflow | Manual |
| UI-CAT-001 | Adaptive card grid | Manual layout/resizing acceptance | Manual |
| UI-CAT-002 | Optional list presentation | No test required unless implemented | Not applicable unless implemented |
| UI-CAT-003 | Catalogue card metadata | Manual with complete and sparse metadata fixtures | Manual |
| UI-CAT-004 | Extended details in detail window | Manual | Manual |
| UI-CAT-005 | Season controls, navigation, refresh, search, cache access | Manual workflow | Manual |
| UI-CAT-006 | Visible quick actions | Manual workflow | Manual |
| UI-CAT-007 | Poster and metadata placeholders | Unit/image tests + manual visual check | Planned / Manual |
| UI-TAB-001 | Seasonal tabs | Manual | Manual |
| UI-TAB-002 | Tabs/history are distinct | Workflow/manual | Manual |
| UI-TAB-003 | Closing tab retains history | Workflow/manual | Manual |
| UI-TAB-004 | History fields and reopen action | Manual | Manual |
| UI-DETAIL-001 | Separate non-modal window | Manual | Manual |
| UI-DETAIL-002 | Extended metadata presentation | Manual with sparse/full fixtures | Manual |
| UI-DETAIL-003 | Current status or Add action | Manual | Manual |
| UI-DETAIL-004 | Status, removal, MAL page actions | Manual | Manual |
| UI-SET-001 | MAL client configuration | Manual configuration and validation | Manual |
| UI-SET-002 | Cache clearing and backup/restore access | Manual Settings workflow | Manual |

## 5. Acceptance criteria (SRS Section 11)

| AC | Acceptance criterion summary | Verification references | Initial status |
|---|---|---|---|
| AC-01 | Add, view, change status, explicitly remove ledger entries | FR-LED-005–007; UI-LED-004/005 | Audit needed / Manual |
| AC-02 | Five mutually exclusive statuses | FR-LED-003/004 | Audit needed |
| AC-03 | One record per MAL ID | FR-LED-002 | Audit needed |
| AC-04 | Ledger survives restart | FR-LED-001 | Audit needed |
| AC-05 | Ledger search, filtering, sorting | FR-LED-011–013 | Audit needed |
| AC-06 | No episode progress required/stored | FR-LED-014 | Audit needed |
| AC-07 | Retrieve seasonal anime by season/year | FR-CAT-001/002 | Audit needed |
| AC-08 | English/Romanized/Japanese title fallback | FR-CAT-004 | Planned |
| AC-09 | Catalogue cards display available core metadata | FR-CAT-003; UI-CAT-003/007 | Manual |
| AC-10 | Details open in separate non-modal window | FR-CAT-006; UI-DETAIL-001 | Manual |
| AC-11 | MAL links open in default browser | FR-CAT-005 | Manual |
| AC-12 | Multiple seasonal tabs work independently | FR-TAB-001–003 | Manual |
| AC-13 | Previous/next navigation crosses year boundaries | FR-TAB-006–008 | Planned / Manual |
| AC-14 | Previously fetched seasons reopen from history | FR-TAB-004/005; FR-CACHE-003 | Planned / Manual |
| AC-15 | Partial, case-insensitive AND search across local metadata | FR-SEARCH-001–006 | Planned |
| AC-16 | Search keystrokes do not issue network requests | FR-SEARCH-002 | Planned |
| AC-17 | Blacklisted anime absent from catalogue/search | FR-CAT-008; FR-SEARCH-008 | Planned |
| AC-18 | Blacklisting immediately removes from open catalogues | FR-CAT-009 | Planned / Manual |
| AC-19 | Removing blacklist restores from cache without MAL request | FR-CAT-009; FR-CACHE-003 | Planned |
| AC-20 | Fresh metadata updates existing ledger title only | FR-LED-010; FR-CAT-010 | Planned |
| AC-21 | Catalogue refresh does not change ledger membership/status | FR-LED-008; FR-CACHE-006 | Planned |
| AC-22 | Cache clear removes catalogue/history but preserves ledger | FR-CACHE-008/009 | Planned |
| AC-23 | Cache does not expire automatically | FR-CACHE-010 | Audit needed |
| AC-24 | Manual backup and restore available | FR-BACKUP-001/002; UI-SET-002 | Existing — partial / Manual |
| AC-25 | Offline ledger and cached-data access | SRS Sections 4, 9, 10 | Planned / Manual |
| AC-26 | Network operations do not freeze UI | UI-009/010 | Planned / Manual |
| AC-27 | Maven/IntelliJ Community build and run | Build Setup v1.0; SRS Section 12 | User-confirmed build/test; run manual |

## 6. Priority gap register

The following are verification tasks, not confirmed implementation defects.

| Gap ID | Priority | Verification gap | Suggested test / evidence |
|---|---|---|---|
| GAP-01 | P0 | Ledger/cache separation not evidenced in inspected tests | Clear-cache and failed-refresh integration tests with ledger assertions |
| GAP-02 | P0 | Refresh transaction atomicity and previous-cache preservation | Fake gateway failure on later page and forced persistence failure |
| GAP-03 | P0 | Blacklist enforcement across all repository paths | Repository tests for fresh, cached, history, and query results |
| GAP-04 | P0 | Migration path and migration rollback coverage not evidenced | Fresh DB, prior-version DB, newer-version rejection, injected migration failure |
| GAP-05 | P0 | Restore compatibility and replacement rollback cases incomplete in inspected evidence | Newer schema, missing schema, FK violation, migration failure, atomic move failure |
| GAP-06 | P1 | Search semantics not evidenced in inspected tests | Multi-token cross-field AND, partial, case-insensitive, whitespace cases |
| GAP-07 | P1 | MAL mapper and pagination coverage not evidenced in inspected tests | Controlled JSON fixtures and fake/local HTTP server |
| GAP-08 | P1 | Title synchronization invariants not evidenced | Assert title changes while status/date-added/membership remain fixed |
| GAP-09 | P1 | Tabs, history, cache reuse, and restart restoration not evidenced | Persistence and service integration tests plus manual UI scenarios |
| GAP-10 | P1 | Offline and JavaFX responsiveness acceptance not evidenced | Block/disable network and verify local operations; slow-operation UI smoke |
| GAP-11 | P2 | Sparse metadata and missing poster UI coverage | Fixtures with null/missing fields and placeholder visual check |
| GAP-12 | P2 | Window state, tooltip, contextual toolbar and visual density acceptance | Manual checklist across navigation destinations |

Do not close a gap by adding a test that merely exercises a method. The test must assert the SRS-required outcome and failure behaviour.

## 7. Next audit actions

1. Enumerate the complete `src/test/java` tree on this branch and map every existing test class to the matrix.
2. Inspect implementation for each P0 invariant before calling it a defect.
3. Add missing P0 tests first, then P1.
4. Execute `mvn clean test` locally and record the exact commit and result.
5. Perform the manual acceptance checklist for requirements that cannot be credibly covered by current automated tests.
6. Update statuses and attach test class/method references before Phase 4.5 is considered complete.

## 8. Change record

| Version | Date | Description |
|---|---|---|
| 1.0 | 2026-11-14 | Initial mapping of SRS v1.1 functional/UI requirements and all 27 acceptance criteria to verification methods and initial evidence/gaps. |
