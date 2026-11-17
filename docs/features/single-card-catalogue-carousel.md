# Single-card Catalogue Carousel

## Purpose

The Catalogue can be displayed as either an adaptive card Grid or a
single-card horizontal Carousel. Grid remains the default. The Carousel
shows one anime at a time; adjacent cards and partial previews are not
shown.

## Selecting the presentation

1. Open **Settings**.
2. Under **Catalogue presentation**, select **Grid** or **Single-card carousel**.
3. The preference is saved to the local UI preferences file.
4. Reopen the Catalogue workspace for the selected presentation to take effect.

The presentation preference is not stored in SQLite and does not require a
database migration. It is a global UI preference rather than a per-season
setting.

## Carousel layout and navigation

- The card uses a poster column beside an information column.
- The information column can scroll vertically when the synopsis or other
  content is longer than the available workspace.
- Only one result is displayed at a time.
- The Previous and Next buttons move through the current season tab's visible
  result set. The position indicator shows the current item and total count.
- Previous is disabled at the first result; Next is disabled at the last.
  Navigation does not wrap.
- With the Carousel active, Left and Right arrow keys navigate to the previous
  and next visible anime when the event target is not a text input or combo box.
  Text caret movement and combo-box popup navigation retain their normal
  behaviour.

Search, the Hide anime already in My Ledger filter, and blacklist exclusion
continue to determine the visible result set. Switching presentation does not
issue additional MAL requests.

## Card actions

The Carousel card includes:

- **Like / Dislike:** available when the anime is already in My Ledger.
  Selecting the active preference again clears it.
- **My Ledger status:** a selector beside Like / Dislike. It can add an anime
  using any of the six ledger statuses or change the status of an existing
  entry.
- **Catalogue actions:** open the anime's MAL page, open Anime Details, add a
  new entry as Watch Later, or add a new entry as Blacklisted, subject to the
  existing action availability.

The Grid card keeps its existing compact quick actions. The Like / Dislike
buttons and the Carousel-only My Ledger status selector are not shown in Grid.

## Data and persistence boundaries

- Both presentations use the same catalogue result set, repository filtering,
  ledger state, and metadata.
- The Carousel is a presentation choice; it does not create another cache or
  catalogue query path.
- The selected presentation is stored in local UI preferences, outside the
  SQLite database.
- Ledger status and Like / Dislike preference remain ledger data and continue
  to use the existing ledger persistence behaviour.

## Manual verification checklist

1. Select Grid in Settings, reopen Catalogue, and confirm the adaptive grid is
   displayed without Like / Dislike or the Carousel status selector.
2. Select Single-card carousel, reopen Catalogue, and confirm exactly one
   anime is visible with no neighbouring-card preview.
3. Use Previous and Next and confirm the position indicator updates and the
   controls disable at the first and last results.
4. Use Left and Right when focus is outside text inputs and combo boxes;
   confirm the selected anime changes without switching season tabs.
5. Focus the search field and move the caret with Left and Right. Open a
   combo-box popup and confirm its arrow-key behaviour remains available.
6. Search, clear the search, and toggle Hide anime already in My Ledger;
   confirm navigation follows only the currently visible results.
7. Open an anime with a long synopsis and confirm the information column
   scrolls while the poster remains in place and the card stays within the
   Catalogue workspace.
8. Add an anime using each ledger status, then change the status of an
   existing entry. Confirm the displayed ledger status updates.
9. Set Like and Dislike for a ledger entry, clear the active preference, and
   confirm the value is reflected in the other ledger-aware screens.
10. Confirm blacklisted anime remain excluded from the Catalogue in both
    presentations.
11. Restart the application and confirm the selected presentation preference
    is retained.

## Verification note

This document describes the implementation present on `develop`. The
repository changes were reviewed when this document was written; run
`mvn clean test` and exercise the JavaFX application locally to verify the
current checkout and desktop behaviour.
