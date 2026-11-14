# Keyboard Shortcuts and Keyboard Navigation

This document describes keyboard behaviour currently available in MyAnimeWatchLedger.

## Application-wide shortcuts

There are currently **no dedicated application-wide shortcut bindings** for switching workspaces, opening the current season, refreshing a catalogue, changing an anime's ledger status, or opening settings. These actions are available through the visible controls and tooltips.

Do not assume that a shortcut used by another desktop application is implemented here.

## Standard control behaviour

The interface uses standard JavaFX controls. They retain their normal keyboard interaction, subject to the focused control and the operating system.

| Context | Keyboard interaction | Notes |
|---|---|---|
| Moving between controls | `Tab` / `Shift+Tab` | Moves focus forward/backward through focusable controls. |
| Buttons and checkboxes | `Space` or `Enter` | Activates the focused control where supported by the control. |
| Text fields | `Ctrl+A`, `Ctrl+C`, `Ctrl+X`, `Ctrl+V` | Standard select all, copy, cut, and paste operations on Windows. |
| Text editing | `Ctrl+Z` | Undo where supported by the focused JavaFX text control. |
| Combo boxes | `Alt+Down` or `F4`; arrow keys; `Enter`; `Esc` | Standard JavaFX combo-box popup interaction; exact behaviour can vary by platform/control skin. |
| Tables | Arrow keys | Standard table selection/navigation when the table has focus. |

The table above describes standard control behaviour, not custom MyAnimeWatchLedger command shortcuts. Platform conventions may differ outside Windows.

## Workspace-specific guidance

- **My Ledger:** Focus the table and use the arrow keys to move the selection. Use the visible status dropdown to change an entry's status.
- **Catalogue:** Focus a season/year selector or search field and use its standard JavaFX keyboard interaction. Season navigation and refresh currently have no dedicated key bindings.
- **Anime Details:** Focus the tab area or controls to use their standard JavaFX keyboard behaviour. No custom close-tab or next-tab shortcut is currently assigned.
- **Search History:** Use Tab navigation to reach the refresh and open-search actions. No custom refresh shortcut is assigned.
- **Settings:** Use Tab/Shift+Tab to move through preferences and actions.

## Future shortcut policy

When dedicated shortcuts are added, they should be documented here and exposed through tooltips or visible menu/action labels where practical. Shortcuts should avoid conflicting with standard text-editing and JavaFX control bindings.
