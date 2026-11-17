# MyAnimeWatchLedger

A local-first Java desktop application for maintaining a personal anime watch ledger and browsing seasonal anime catalogues from MyAnimeList (MAL).

## Features

- Personal ledger with **Watched**, **Watching**, **Abandoned**, **Blacklisted**, **Hold**, and **Watch Later** statuses.
- Seasonal catalogue browsing with multiple season tabs, local metadata search, adaptive grid and single-card horizontal carousel views.
- Anime details in a separate, non-modal workspace.
- Persistent search history and disposable catalogue cache.
- Manual database backup and restore, plus configurable automatic backups.
- Dark JavaFX interface with preference-controlled sound effects and animations.

## Requirements

- JDK 25
- IntelliJ IDEA (Maven project import is supported)
- Internet connection and a MAL Client ID for online catalogue/detail retrieval

No separately installed database server or frontend toolchain is required. The application uses an embedded SQLite database.

## Run

Open the repository as a Maven project in IntelliJ IDEA, select JDK 25 as the project SDK and Maven runner JDK, and run:

```bash
mvn clean test
mvn javafx:run
```

Alternatively, run the main class:

`com.myanimewatchledger.app.MyAnimeWatchLedgerApplication`

Configure the MAL Client ID under **Settings → MyAnimeList API**.

## Data location

On Windows, application data is stored in:

`%LOCALAPPDATA%\MyAnimeWatchLedger`

The SQLite database is `my_anime_watch_ledger.db`. If `LOCALAPPDATA` is unavailable, the application falls back to `~/.myanimewatchledger` under the user's home directory.

## Documentation

- [Project reference](docs/PROJECT_REFERENCE.md) — authoritative requirements, finalized decisions, implementation overview, build workflow, and phase acceptance/closure checklist.
- [Keyboard shortcuts](docs/KEYBOARD_SHORTCUTS.md) — current keyboard behaviour and shortcut support.
- [Single-card catalogue carousel](docs/features/single-card-catalogue-carousel.md) — presentation behaviour, settings, controls, and manual verification.\n- [Automatic database backups](docs/features/automatic-database-backups.md) — approved defaults, configurable schedule/retention/location, safety rules, and implementation plan.

## Project status

`develop` contains the merged single-card catalogue carousel (PR #18). The project reference records the approved requirements and current implementation. Run the Maven test suite and launch the JavaFX application locally when validating a checkout.
