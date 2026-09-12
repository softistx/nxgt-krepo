package com.softistx.material.form

import androidx.compose.ui.state.ToggleableState
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

class TriStateCheckboxTest :
    FeatureSpec({
        feature("cycleCheckState") {
            scenario("walks off, on, indeterminate, off") {
                cycleCheckState(CheckState.Off) shouldBe CheckState.On
                cycleCheckState(CheckState.On) shouldBe CheckState.Indeterminate
                cycleCheckState(CheckState.Indeterminate) shouldBe CheckState.Off
            }
        }

        feature("toToggleableState") {
            scenario("maps onto foundation's three values") {
                CheckState.Off.toToggleableState() shouldBe ToggleableState.Off
                CheckState.On.toToggleableState() shouldBe ToggleableState.On
                CheckState.Indeterminate.toToggleableState() shouldBe ToggleableState.Indeterminate
            }
        }
    })
