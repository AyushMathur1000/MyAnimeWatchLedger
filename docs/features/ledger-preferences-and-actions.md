# Ledger preferences and actions

## Scope

This feature adds optional user sentiment and Watch Later priority to the permanent local ledger, plus compact icon-only ledger row actions.

## Behaviour

- Ledger actions are icon-only: Details, Open MyAnimeList, and Remove. Each control has a descriptive tooltip and accessible text.
- Anime preference is independent of watch status and supports Like, Dislike, and Unassigned.
- Preference can be changed from Ledger, Catalogue cards, and Anime Details.
- Watch Later priority is optional, accepts integers 1–10, and treats 1 as most preferred and 10 as least preferred.
- Priority can only be assigned while status is WATCH_LATER. Leaving WATCH_LATER clears the priority; changing between statuses does not clear Like/Dislike.
- Both new fields are unassigned for existing and newly added entries by default.
- All changes persist locally in SQLite and publish ledger change events so open screens refresh.

## Persistence

Migration V004 adds nullable `preference` and `watch_later_priority` columns. The preference constraint accepts only LIKE or DISLIKE. Priority is constrained to 1–10. NULL represents Unassigned.

## Manual verification

1. Open My Ledger and confirm Details, MAL, and Remove are icon-only with useful tooltips.
2. Confirm existing entries show Preference = Unassigned.
3. Set Like from Ledger, then verify the same value in Catalogue and Anime Details.
4. Set Dislike from Catalogue and verify it in Ledger and Anime Details.
5. Clear the preference by selecting Unassigned.
6. Add or change an entry to Watch Later and assign priorities 1 and 10.
7. Confirm priority controls are disabled for every non-Watch-Later status.
8. Change a Watch Later entry to another status and confirm priority clears while preference remains.
9. Restart the application and confirm preference and priority persist.
10. Upgrade a database from schema version 3 and confirm existing ledger entries remain intact with both new fields unassigned.

## Verification status

Repository-side source inspection only. Maven tests and JavaFX manual verification must be run in a local development environment before merging.
