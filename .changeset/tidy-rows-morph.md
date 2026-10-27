---
"stx-material": minor
---

`ListTile` is Material 3's interactive `ListItem`: a clickable tile's shape now morphs under the press, the hover and the focus (4 dp at rest, 16 dp pressed) instead of the whole row scaling to 0.99, and it is still announced as a button. New parameters, after every existing one: `selected: Boolean? = null` makes a tile a single choice, as a list-detail's current row is (the chosen row is filled with M3's selected tone and announced as a `RadioButton` that is or is not selected; `null` keeps the plain button), and `group: ListTileGroup? = null` (`ListTileGroup(index, count)`) makes it one tile of a segmented list, round at the top of the first and the bottom of the last and squarer between, spaced by the new `ListTileDefaults.GroupGap`. Both need `onClick`; a tile without it stays a plain row. Visible default change: the press scale is gone from `ListTile`, and `StxStyles.listTile` is now an empty `Style` (M3 owns the look).
