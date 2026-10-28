---
"stx-material": minor
---

`ListDetailLayout(selected, placeholderTitle, modifier, wideThreshold, listWidth, placeholder, list, detail)` shows a list and its detail side by side when the width it is offered reaches `wideThreshold` (720 dp), and one at a time below it, with no Navigation 3 back stack, so it folds inside a destination or a narrow desktop window. The list pane is `listWidth` (380 dp) wide; selection is plain state. `ListDetailLayoutDefaults` holds the two numbers and the default placeholder. It is not `ListDetail`, which stays the scene metadata of `AdaptiveNavDisplay`.
