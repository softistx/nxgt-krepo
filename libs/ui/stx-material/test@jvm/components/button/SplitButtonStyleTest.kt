package com.softistx.material.components.button

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.pressed
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import com.softistx.material.Corner
import com.softistx.material.NodeBounds
import com.softistx.material.components.surface.MenuItem
import com.softistx.material.foundation.i18n.StxStrings
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.probed
import com.softistx.material.renderProbe
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * The `style` of a [SplitButton] reads the state of both halves: a `pressed` block fires while
 * either one is down, and a `disabled` block while the control is off. Proved on drawn pixels.
 */
class SplitButtonStyleTest :
    FeatureSpec({
        feature("a split button's style") {
            scenario("a pressed block applies while either half is pressed") {
                val bounds = NodeBounds()
                renderProbe(width = 300, content = {
                    flat {
                        SplitButton(
                            "Save",
                            onClick = {},
                            overflow = listOf(MenuItem("Save as", onClick = {})),
                            modifier = Modifier.padding(20.dp).probed(bounds),
                            style = Style { pressed { alpha(0.2f) } },
                        )
                    }
                }) {
                    settle()
                    val leadingAt = Offset(bounds.rect.left + 20f, bounds.rect.center.y)
                    val trailingAt = Offset(bounds.rect.right - 20f, bounds.rect.center.y)
                    // A pixel of the container above the label, so neither glyph nor text is what is read.
                    val above = Offset(bounds.rect.left + 20f, bounds.rect.top + 5f)
                    val probe = NodeBounds().apply { rect = Rect(above, above + Offset(1f, 1f)) }
                    val rest = settle().pixelAt(probe, Corner.TopLeft)
                    val leading = press(leadingAt).pixelAt(probe, Corner.TopLeft)
                    release(leadingAt)
                    val trailing = press(trailingAt).pixelAt(probe, Corner.TopLeft)
                    leading shouldNotBe rest
                    trailing shouldNotBe rest
                }
            }

            scenario("a disabled block applies when the control is disabled") {
                fun ink(enabled: Boolean): Int {
                    val bounds = NodeBounds()
                    var drawn = 0
                    renderProbe(width = 300, content = {
                        flat {
                            SplitButton(
                                "Save",
                                onClick = {},
                                overflow = listOf(MenuItem("Save as", onClick = {})),
                                modifier = Modifier.padding(20.dp).probed(bounds),
                                enabled = enabled,
                                style = Style { disabled { alpha(0f) } },
                            )
                        }
                    }) { drawn = settle().ink(bounds) }
                    return drawn
                }
                (ink(enabled = true) > 0) shouldBe true
                ink(enabled = false) shouldBe 0
            }
        }
    })

/** [content] in the light theme, without M3's 48 dp touch target around it. */
@Composable
private fun flat(content: @Composable () -> Unit) {
    StxTheme(strings = StxStrings.En, isDark = false) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) { content() }
    }
}
