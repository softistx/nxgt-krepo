---
"stx-material": minor
---

`ListTile` is Material 3's interactive `ListItem`. New parameters, after every existing one: `selected: Boolean? = null` makes a tile a single choice, as a list-detail's current row is (the chosen row takes M3's selected tone and is announced as a `RadioButton` that is or is not selected), and `group: ListTileGroup? = null` (`ListTileGroup(index, count)`) makes it one tile of a segmented list, spaced by the new `ListTileDefaults.GroupGap`. Both need `onClick` (M3 has no static segmented item): `selected` or `group` without one throws an `IllegalArgumentException`.

Visible default changes of a clickable `ListTile`, before then after:
- The press: the whole row scaled to 0.99 under a plain rectangular ripple; now M3's state layers (pressed, hovered, focused) and a shape that morphs, with no scale. `StxStyles.listTile` is now an empty `Style`.
- The rest shape: square corners; now M3's expressive 4 dp corners (`ListItemDefaults.shapes()`), 12 dp hovered, 16 dp pressed or focused, and the row is clipped to it.
- Semantics: a `Role.Button` on the clickable modifier; now one merged node with `Role.Button` (M3's `ListItem` sets none), so a screen reader reads title, supporting text and trailing as a single button. That role holds only while `selected == null`; with `selected` it is a `RadioButton`.
- A grouped tile sits on `surfaceContainerLow` instead of the plain `surface` an ungrouped tile has.
A tile without `onClick` is unchanged.
