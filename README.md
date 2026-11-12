# MyAnimeWatchLedger

A local Java desktop application for maintaining a personal anime watch ledger and browsing cached seasonal catalogue data from MyAnimeList.

## Technology baseline

- Java 25
- JavaFX 25 (Controls; programmatic UI)
- Maven 3.10.0 via the included lightweight Maven bootstrap scripts
- SQLite through Xerial SQLite JDBC
- Jackson Databind
- JUnit Jupiter

## Prerequisites

Install a 64-bit JDK 25. No separate Maven installation or JavaFX SDK is required. On Windows, verify:

```powershell
java -version
javac -version
```

Both must report Java 25.

## Run from Windows

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd javafx:run
```

The first wrapper invocation downloads Apache Maven 3.10.0 into the current user's `.m2` directory. Maven then resolves project dependencies.

## Run from IntelliJ IDEA Community

1. Open the project by selecting its `pom.xml`.
2. Set Project SDK to JDK 25.
3. Set Maven importer and runner JDK to JDK 25.
4. Use the Maven Wrapper as the Maven distribution.
5. Run the `javafx:run` Maven goal, or run `MyAnimeWatchLedgerApplication` after Maven import.

## Local data

The application creates its data directory under `%LOCALAPPDATA%\MyAnimeWatchLedger` on Windows. The SQLite database is not stored in the repository. On other operating systems, the path falls back to the user's home directory under `.myanimewatchledger`.

## Project layout

```text
src/main/java/com/myanimewatchledger/
  app/             Application entry point and lifecycle
  config/          Local paths and configuration
  domain/          Domain models and rules (introduced in later phases)
  application/     Use cases and services (introduced in later phases)
  infrastructure/  SQLite, MAL API, filesystem adapters
  presentation/    JavaFX views and controllers
src/main/resources/
  db/migration/    Versioned SQLite migrations
  styles/          JavaFX CSS
src/test/java/     Automated tests
```

## Phase 0 scope

The current foundation includes the JavaFX launch lifecycle, local data-directory creation, SQLite JDBC connection initialization, a minimal launch screen, and an isolated SQLite smoke test. Product screens and schema migrations are intentionally deferred to subsequent implementation phases.
