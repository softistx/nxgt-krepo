package com.softistx.material.foundation.style

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.selected
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.softistx.material.NodeBounds
import com.softistx.material.probed
import com.softistx.material.renderProbe
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

/** What `styledBy` hands to a style's `selected { }` and `disabled { }` blocks, proved in pixels. */
class StyledByTest :
    FeatureSpec({
        fun count(
            color: Color,
            selected: Boolean,
            enabled: Boolean = true,
        ): Int {
            val box = NodeBounds()
            val style =
                Style {
                    selected { background(Color.Red) }
                    disabled { background(Color.Blue) }
                }
            return renderProbe(width = 40, height = 40, content = {
                Box(Modifier.size(20.dp).probed(box).styledBy(style, enabled = enabled, selected = selected))
            }) { settle().count(color) }
        }

        feature("styledBy") {
            scenario("a selected part wears the selected block") {
                count(Color.Red, selected = true) shouldBeGreaterThan 100
            }

            scenario("a disabled part wears the disabled block") {
                count(Color.Blue, selected = false, enabled = false) shouldBeGreaterThan 100
                count(Color.Blue, selected = false, enabled = true) shouldBe 0
            }

            scenario("an unselected part does not") {
                count(Color.Red, selected = false) shouldBe 0
            }
        }
    })
