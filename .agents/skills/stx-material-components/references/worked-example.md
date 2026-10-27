# Worked example: `ChoiceTile`

A small interactive component built the house way, with its proof. It is a teaching example and is not in the library; every file below compiled and its specs passed against `stx-material` on 1.12.0-alpha03 before being written here. Copy the shape, not the component.

What it demonstrates, in order of the steps in `SKILL.md`:

1. Placed in `components/content/`, as two files (`ChoiceTile.kt`, `ChoiceTileStyles.kt`).
2. M3 first: a clickable `Card` already has the ripple, focus and hover layers, the click action and a 48 dp target. Nothing is rebuilt.
3. Wrapped through `CardDefaults.cardColors(...)` and `MaterialTheme.shapes.largeIncreased`.
4. A `Style` for the one thing M3 has no parameter for (the press give and the disabled alpha). `style` is last and applied last.
5. Expressive: the `largeIncreased` shape, a tonal selection, spring motion from `StxMotion`.
6. Quality: selection is a tone, not a border; type from `Typography`; spacing from `StxTheme.spacing`.
7. Proof: a pixel spec and a semantics spec, each in its own file.
8. Named default: `StxStyles.choiceTile`.

**M3's clickable `Card` sets no `Role`.** The semantics spec failed on the first run for exactly that, so the component adds one: `Role.RadioButton`, because a tile is a single choice (a button that only acts would get `Role.Button`). The component adds the role and the specs assert it. `ClickableRolesTest` pins the same for `ListTile`, `Breadcrumb`, `Stepper`, `Accordion` and `Disclosure`.

## `src/components/content/ChoiceTile.kt`

```kotlin
package com.softistx.material.components.content

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.Card as MaterialCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.softistx.material.foundation.text.Emphasis
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.StxTheme

/**
 * A tile for choosing one option among a few: a plan, a delivery method.
 *
 * It is Material 3's clickable `Card`, which brings the ripple, the focus and hover layers and the
 * 48 dp target. M3 gives that card no role, so [ChoiceTile] adds `Role.RadioButton`: put the tiles
 * of one choice in a container with `Modifier.selectableGroup()`. Selection is a tone
 * ([choiceTileColors]), not a border.
 */
@Composable
fun ChoiceTile(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    style: Style = Style,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isEnabled = enabled }
    MaterialCard(
        onClick = onClick,
        modifier =
            modifier
                .semantics {
                    role = Role.RadioButton
                    this.selected = selected
                }
                .styleable(styleState, choiceTileStyle, style),
        enabled = enabled,
        shape = MaterialTheme.shapes.largeIncreased,
        colors = choiceTileColors(selected),
        elevation = CardDefaults.cardElevation(),
        interactionSource = interactionSource,
    ) {
        Column(
            modifier = Modifier.padding(StxTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.xs),
        ) {
            Typography(text = title, variant = TypographyVariant.TitleMedium)
            if (description != null) {
                Typography(
                    text = description,
                    variant = TypographyVariant.BodyMedium,
                    emphasis = Emphasis.Medium,
                )
            }
        }
    }
}
```

## `src/components/content/ChoiceTileStyles.kt`

```kotlin
package com.softistx.material.components.content

import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.animate
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.pressed
import androidx.compose.foundation.style.scale
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.softistx.material.components.button.DISABLED_ALPHA
import com.softistx.material.foundation.motion.MotionSpeed
import com.softistx.material.foundation.style.motion

/** Selection is a tone, not a border: the selected tile takes the secondary container. */
@Composable
internal fun choiceTileColors(selected: Boolean): CardColors {
    val scheme = MaterialTheme.colorScheme
    return if (selected) {
        CardDefaults.cardColors(
            containerColor = scheme.secondaryContainer,
            contentColor = scheme.onSecondaryContainer,
        )
    } else {
        CardDefaults.cardColors(
            containerColor = scheme.surfaceContainerLow,
            contentColor = scheme.onSurface,
        )
    }
}

/** The give under the finger and the disabled alpha: what M3's Card has no parameter for. */
internal val choiceTileStyle: Style =
    Style {
        pressed { animate(motion.spatial(MotionSpeed.Fast)) { scale(0.98f) } }
        disabled { animate(motion.effects()) { alpha(DISABLED_ALPHA) } }
    }
```

## `src/foundation/style/StxStyles.kt`

Name the default, so a caller can restate it and edit it (`style = StxTheme.styles.choiceTile then { … }`):

```kotlin
val choiceTile: Style get() = choiceTileStyle
```

(with `import com.softistx.material.components.content.choiceTileStyle`, beside `card`, `chip` and `listTile`.)

Why `ChoiceTile` has no `selected { border(...) }` block: a border drawn by a `Style` uses the Style's own `shape`. With no `shape(...)` it draws a *square* border around a rounded M3 container (measured: the top-left pixel is the border colour, not the background). If a Style does draw a border, repeat the container's shape in it: `Style { shape(shapes.largeIncreased); selected { border(2.dp, scheme.primary) } }`.

## `test@jvm/components/content/ChoiceTileRenderTest.kt`

```kotlin
package com.softistx.material.components.content

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import com.softistx.material.Corner
import com.softistx.material.NodeBounds
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.probed
import com.softistx.material.renderProbe
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** What a reader sees: the shape, the tone and the give. */
class ChoiceTileRenderTest :
    FeatureSpec({
        feature("a choice tile") {
            scenario("takes a different container when selected") {
                val on = NodeBounds()
                val off = NodeBounds()
                renderProbe(width = 360, height = 300, content = {
                    StxTheme(isDark = false) {
                        Column {
                            ChoiceTile("Pro", selected = true, onClick = {}, modifier = Modifier.probed(on))
                            ChoiceTile("Free", selected = false, onClick = {}, modifier = Modifier.probed(off))
                        }
                    }
                }) {
                    val frame = settle()
                    frame.pixelAt(on, Corner.Center, inset = 4) shouldNotBe frame.pixelAt(off, Corner.Center, inset = 4)
                }
            }

            scenario("rounds its corners") {
                val tile = NodeBounds()
                renderProbe(content = {
                    StxTheme(isDark = false) {
                        ChoiceTile("Pro", selected = false, onClick = {}, modifier = Modifier.probed(tile))
                    }
                }) {
                    val frame = settle()
                    frame.isTransparent(tile, Corner.TopLeft) shouldBe true
                    frame.isFilled(tile, Corner.Center) shouldBe true
                }
            }

            scenario("gives under the finger") {
                val tile = NodeBounds()
                renderProbe(content = {
                    StxTheme(isDark = false) {
                        ChoiceTile("Pro", selected = false, onClick = {}, modifier = Modifier.probed(tile))
                    }
                }) {
                    val rest = settle().ink(tile)
                    val pressed = press(tile).ink(tile)
                    pressed shouldBeLessThan rest
                }
            }
        }
    })
```

## `test@jvm/components/content/ChoiceTileSemanticsTest.kt`

```kotlin
package com.softistx.material.components.content

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.softistx.material.foundation.theme.StxTheme
import io.kotest.core.spec.style.FeatureSpec

/** What a screen reader hears: a radio button that knows whether it is selected, with a 48 dp target. */
@OptIn(ExperimentalTestApi::class)
class ChoiceTileSemanticsTest :
    FeatureSpec({
        feature("a choice tile's semantics") {
            scenario("is a radio button that reports its selection and meets 48 dp") {
                runComposeUiTest {
                    var chosen by mutableStateOf(false)
                    setContent {
                        StxTheme(isDark = false) {
                            ChoiceTile("Pro", selected = chosen, onClick = { chosen = true })
                        }
                    }
                    onNodeWithText("Pro")
                        .assertHasClickAction()
                        .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                        .assertIsNotSelected()
                        .assertHeightIsAtLeast(48.dp)
                        .performClick()
                    onNodeWithText("Pro").assertIsSelected()
                }
            }
        }
    })
```

`ChoiceTileRenderTest` asserts what changed in pixels: tone (selected differs from rest), shape (the corner is the background, the centre is not), give (pressed ink is smaller than rest). `settle()` renders eight frames of 100 ms, enough for every spring in this library. `ChoiceTileSemanticsTest` asserts the role, the click action, the selected state and the 48 dp floor. It imports `androidx.compose.ui.test.v2.runComposeUiTest`: the v1 function in `androidx.compose.ui.test`, which `ClickableRolesTest` and the older specs still use, is deprecated (the v2 one queues coroutines on a `StandardTestDispatcher`, as production does) and warns on every build.

## The story, in the same change

In `examples/material-demo/md-catalog/src/stories/components/content/ContentStories.kt`, inside the existing `storyGroup("Content")`:

```kotlin
story("Choice tile") { knobs ->
    var chosen by remember { mutableStateOf("Pro") }
    Column(
        modifier = Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm),
    ) {
        listOf("Free", "Pro", "Team").forEach { plan ->
            ChoiceTile(
                title = plan,
                description = knobs.text("Description", "Billed monthly"),
                selected = plan == chosen,
                onClick = { chosen = plan },
                enabled = knobs.flag("Enabled", true),
            )
        }
    }
}
```

The catalogue consumes the *published* `stx-material`, not the module: run `./kotlin publish mavenLocal -m stx-material --non-transitive` (AGENTS.md, *Publishing*) before `./kotlin build -m md-catalog`, or the story cannot see the new component.

## Docs and changeset

`docs/components.md`, Content table, and the box ticked in `docs/roadmap.md` in the same change:

```markdown
| `ChoiceTile` | `title`, `selected`, `onClick`, `description?`, `enabled` | `content/choice-tile` |
```

`.changeset/<name>.md` (a new component is `minor` in 0.x):

```markdown
---
"stx-material": minor
---

`ChoiceTile`: a card for choosing one option among a few, with a tonal selected state.
```
