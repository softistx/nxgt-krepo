---
"stx-material": minor
---

Every interactive data and date part takes `style: Style = Style`, applied last, and `DataTable` gets sortable headers. New API: `style` on `DataTable` (plus `headerStyle` for the sortable headers), `Pagination`, `SortControl`, `CommandPalette`, `DateField`, `DateRangeField`, `TimeField` and `Calendar`; `DataTable(sort: TableSort? = null, onSortChange: ((TableSort) -> Unit)? = null)`, `TableColumn(header, value, sortable: Boolean = true)` and `TableSort(column, direction = Asc)`; `StxStyles.tableRow` (0.99 press scale of a clickable `DataTable` row) and `StxStyles.sortHeader` (0.97 press scale of a sortable header). Parameter placement: `style` comes after `enabled` (and after `label` on `Pagination`), and `DataTable` puts `sort`, `onSortChange`, `style`, `headerStyle` before `onRowClick`, so `DataTable(columns, rows) { row -> … }` still compiles; only a positional call that passed `onRowClick` as its fifth argument must name it. `CommandPalette` dresses its list of commands, not the dialog; `Calendar` and `CommandPalette` have no default style (M3's `DatePicker` and `AlertDialog` own their look). Visible default changes:

- `DataTable`: with `onSortChange` every column header whose `TableColumn.sortable` is true (all by default) becomes a button that sorts (ascending, then flipped); the others stay plain text. The current sort shows an arrow and announces "Ascending" or "Descending", also with `sort` alone (before: plain text headers; sorting needed a `SortControl` beside it).
- `DataTable`: with `onRowClick` the rows of the wide table are clickable and press to 0.99 (before: only the collapsed cards were clickable).
- `EntityHeader`: the title is announced as a heading (before: plain text).
