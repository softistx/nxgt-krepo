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
        fun reds(
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
            }) { settle().count(Color.Red) }
        }

        feature("styledBy") {
            scenario("a selected part wears the selected block") {
                reds(selected = true) shouldBeGreaterThan 100
            }

            scenario("an unselected part does not") {
                reds(selected = false) shouldBe 0
            }
        }
    })
