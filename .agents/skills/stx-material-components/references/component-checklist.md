# Component checklist

Run it before opening the PR. Each line is checkable; a line you cannot tick is either work left or a reason to write in the PR.

## Structure

- [ ] The component sits in one family folder of `src/components/` (or `src/foundation/`), with `Name.kt` and `NameStyles.kt`; neither file mixes two concerns or runs long.
- [ ] You looked at `docs/components.md` and extended an existing component where one fits.
- [ ] The package is `com.softistx.material.<foundation|components>.<folder>`; nothing new under `dev.nxgt`.
- [ ] The M3 function it wraps is named in the KDoc, or the KDoc says M3 has nothing to start from.
- [ ] A reusable helper that a second module wants moved to `libs/core/stx-common`, not copied.

## Signature

- [ ] Required parameters first, then `modifier: Modifier = Modifier`, then optional ones, `enabled`, `style: Style = Style`, content last.
- [ ] No `color`, `shape`, `backgroundColor` or `contentPadding` parameter: those are a `*Colors`, a `*Defaults.shapes()` or a Style.
- [ ] Business state is passed in; presentation state (pressed, hovered, focused) is remembered inside through `rememberUpdatedStyleState`.
- [ ] An inert component (a divider, a keycap) has no `style` and says why.
- [ ] A new parameter is defaulted. A removed or renamed one, or a new required one, is a source break: stop and ask the owner.

## Look

- [ ] No colour literal and no bare `dp` number: scheme roles, `Tone`, `StxTheme.spacing`, M3 `*Defaults` sizes.
- [ ] No `tween(...)`, no easing: only `StxMotion.spatial(...)` and `effects(...)`.
- [ ] Interaction states are Style blocks (`pressed`, `hovered`, `focused`, `disabled`, `selected`), not `animate*AsState`.
- [ ] A morphing control (`shapes =`) has no press `scale`; everything else interactive does.
- [ ] A Style that draws a border or background repeats the container's `shape(...)`, or the draw belongs in `*Colors`.
- [ ] One dominant line of type; secondary text uses `Emphasis`, not a grey.
- [ ] Empty, loading and error variants exist or are named as not applicable.

## Behaviour

- [ ] Role set where M3 sets none (`Role.Button`, `Role.Switch`, `Role.RadioButton`, `Role.Tab`, `Role.Checkbox`); state exposed (`selected`, `toggleableState`, `stateDescription`).
- [ ] Every `Icon` has a `description` or an explicit `null` because a label sits beside it.
- [ ] The target is 48 dp; a visual smaller than that still has `minimumInteractiveComponentSize()`.
- [ ] Reduced motion: `StxMotion(enabled = false)` leaves the component finished and readable.
- [ ] 2× font scale: no fixed `height` on text, no clipped label.
- [ ] Dark and light both render; `on*` colours sit on their pair.
- [ ] A lazy list item has a stable `key` and a `contentType`; no state read in composition that only draw needs (`jetpack-compose-performance`).

## Proof

- [ ] `test@jvm/components/<family>/` has a pixel spec for the change (shape, tone, state) and a semantics spec (role, action, state, 48 dp).
- [ ] The story is registered in the same change, with knobs for variant, `Enabled` and any content; `EveryStoryRendersTest` passes.
- [ ] `docs/components.md` has the row; `docs/tokens.md` has any new token.
- [ ] `.changeset/*.md` names `stx-material` at the right level (or `--empty`).
- [ ] `./kotlin build -m stx-material -p jvm` and `./kotlin test -m stx-material -p jvm`: output redirected to a file, `$?` echoed, file read. ktlint run as `ktlint -F --relative "**/*.kt" "!build/**"`.
- [ ] After publishing to `mavenLocal`, `./kotlin build -m md-catalog -p jvm` compiles the story.
- [ ] The PR names the specs it ran and, for a look change, carries a screenshot or the story id to open.
