package com.strange.material.form

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

class PasswordMeterTest :
    FeatureSpec({
        feature("passwordGrade") {
            scenario("empty is empty, not weak") {
                passwordGrade("") shouldBe PasswordGrade.Empty
            }

            scenario("a short lowercase word is weak") {
                passwordGrade("secret") shouldBe PasswordGrade.Weak
            }

            scenario("length, case, a digit and a symbol reach strong") {
                passwordGrade("Correct-Horse-Battery-1") shouldBe PasswordGrade.Strong
            }
        }
    })
