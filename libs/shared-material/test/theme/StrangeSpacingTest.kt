package com.strange.material.theme

import androidx.compose.ui.unit.dp
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

class StrangeSpacingTest :
    FeatureSpec({

        feature("the spacing ladder") {
            scenario("increases at every step, so no two steps read as the same distance") {
                val s = StrangeSpacing()
                val ladder = listOf(s.none, s.xxs, s.xs, s.sm, s.md, s.lg, s.xl, s.xxl)

                ladder.zipWithNext().forEach { (smaller, larger) ->
                    larger shouldBeGreaterThan smaller
                }
            }
        }

        feature("scaling for density") {
            scenario("scales every step, keeping the relationships between them") {
                val dense = StrangeSpacing().scaledBy(0.5f)

                dense.md shouldBe 8.dp
                dense.xxl shouldBe 24.dp
                dense.lg shouldBeGreaterThan dense.md
            }

            scenario("leaves zero at zero however it is scaled") {
                StrangeSpacing().scaledBy(3f).none shouldBe 0.dp
            }
        }
    })
