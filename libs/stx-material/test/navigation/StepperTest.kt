package com.strange.material.navigation

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

class StepperTest :
    FeatureSpec({
        feature("step status") {
            scenario("marks everything before the current index as done") {
                stepStatus(0, current = 2) shouldBe StepStatus.Done
                stepStatus(1, current = 2) shouldBe StepStatus.Done
            }

            scenario("marks the current index as current") {
                stepStatus(2, current = 2) shouldBe StepStatus.Current
            }

            scenario("marks everything after as upcoming, so a tap cannot skip ahead") {
                stepStatus(3, current = 2) shouldBe StepStatus.Upcoming
                stepStatus(4, current = 2) shouldBe StepStatus.Upcoming
            }
        }
    })
