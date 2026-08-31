package com.strange.material.data

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

class SortControlTest :
    FeatureSpec({
        feature("cycleSortDirection") {
            scenario("flips ascending to descending and back") {
                cycleSortDirection(SortDirection.Asc) shouldBe SortDirection.Desc
                cycleSortDirection(SortDirection.Desc) shouldBe SortDirection.Asc
            }
        }
    })
