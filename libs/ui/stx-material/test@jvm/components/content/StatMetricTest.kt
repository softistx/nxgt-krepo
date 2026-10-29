package com.softistx.material.components.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.NodeBounds
import com.softistx.material.foundation.i18n.StxStrings
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.probed
import com.softistx.material.renderProbe
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe

/**
 * The figure of a [Stat] keeps its weight when the type scale goes expressive, and stats in a row
 * keep one height whether or not they carry a delta.
 */
class StatMetricTest :
    FeatureSpec({
        feature("the metric of a stat") {
            scenario("is no lighter than the old SemiBold, and no lighter than M3's emphasised display") {
                // M3's displaySmallEmphasized is Medium (500); the figure must not fall back to it.
                val medium = metricInk(emphasizedDisplay = true)
                val semiBold = metricInk(emphasizedDisplay = false)
                semiBold shouldBeGreaterThanOrEqual medium
            }
        }

        feature("stats side by side") {
            scenario("keep the same height with and without a delta") {
                val with = NodeBounds()
                val without = NodeBounds()
                renderProbe(width = 420, height = 220, content = {
                    StxTheme(strings = StxStrings.En, isDark = false) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Stat("12", "Students", Modifier.probed(with), delta = "+2 this term")
                            Stat("3", "Absences", Modifier.probed(without))
                        }
                    }
                }) { settle() }
                without.rect.height shouldBe with.rect.height
            }

            scenario("can opt out of the reserved delta line for a stat standing alone") {
                val reserved = NodeBounds()
                val tight = NodeBounds()
                renderProbe(width = 420, height = 320, content = {
                    StxTheme(strings = StxStrings.En, isDark = false) {
                        Column {
                            Stat("3", "Absences", Modifier.probed(reserved))
                            Stat("3", "Absences", Modifier.probed(tight), reserveDelta = false)
                        }
                    }
                }) { settle() }
                (reserved.rect.height > tight.rect.height) shouldBe true
            }
        }
    })

/** Ink of the figure "88" in the metric style, or with M3's emphasised display style (Medium). */
private fun metricInk(emphasizedDisplay: Boolean): Int =
    renderProbe(width = 200, height = 90, content = {
        StxTheme(strings = StxStrings.En, isDark = false) {
            if (emphasizedDisplay) {
                Text("88", style = MaterialTheme.typography.displaySmallEmphasized)
            } else {
                Typography("88", TypographyVariant.Metric)
            }
        }
    }) { settle().ink() }
