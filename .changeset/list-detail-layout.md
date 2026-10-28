---
"stx-material": minor
---

`ListDetailLayout(selected, modifier, wideThreshold, listWidth, placeholder, list, detail)` shows a list and its detail side by side when the width it is offered reaches `wideThreshold` (720 dp), and one at a time below it, with no Navigation 3 back stack, so it folds inside a destination or a narrow desktop window. The list pane is `listWidth` (380 dp) wide; selection is plain state. `ListDetailLayoutDefaults` holds the two numbers and `Placeholder(title, modifier)`, the default second pane (write `placeholder = { ListDetailLayoutDefaults.Placeholder("Select a student", it) }` for another text). Each pane keeps its `rememberSaveable` state across a fold (a list's scroll position also survives a list, detail, list round trip); the detail's state is dropped when `selected` turns false. It is not `ListDetail`, which stays the scene metadata of `AdaptiveNavDisplay`.
