# MyAnimeWatchLedger

A local-first Java desktop application for maintaining a personal anime watch ledger and browsing seasonal anime catalogues from MyAnimeList (MAL).

## Features

- Personal ledger with **Watched**, **Watching**, **Abandoned**, **Blacklisted**, **Hold**, and **Watch Later** statuses.
- Seasonal catalogue browsing with multiple season tabs and local metadata search.
- Anime details in a separate, non-modal workspace.
- Persistent search history and disposable catalogue cache.
- Database backup and restore.
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

## Project status

The current working branch is `feature/phase-6-qol`. Phase 6 QoL is complete and formally closed. See the project reference for the implemented scope and verification record.
