---
"stx-material": minor
---

`StateHost(state, onRetry, modifier, errorTitle, retryLabel, loadingLabel, loading, content)` renders a `UiState<T>`: card-shaped skeletons that arrive in turn while it loads (announced as "Loading", a polite live region), your `content` on data (all three faces sit in a `Box(modifier)`), and an error `Alert` with a Retry button on failure; the strings are parameters. `SkeletonCards(modifier, count)` is the default loading face, public so a screen can compose it.
