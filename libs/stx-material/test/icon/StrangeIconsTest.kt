package com.strange.material.icon

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

/**
 * The icon paths are hand-typed SVG, which is exactly the kind of thing that is wrong by one
 * character and only shows up as a blank square at render time. Building the vector parses the
 * path, so constructing the set *is* the parse — and these scenarios are what makes the parse run.
 */
class StrangeIconsTest :
    FeatureSpec({
        val icons =
            listOf(
                "Add" to StrangeIcons.Add,
                "Check" to StrangeIcons.Check,
                "Close" to StrangeIcons.Close,
                "ChevronUp" to StrangeIcons.ChevronUp,
                "ChevronLeft" to StrangeIcons.ChevronLeft,
                "ChevronRight" to StrangeIcons.ChevronRight,
                "ChevronDown" to StrangeIcons.ChevronDown,
                "Eye" to StrangeIcons.Eye,
                "EyeOff" to StrangeIcons.EyeOff,
                "Delete" to StrangeIcons.Delete,
                "Edit" to StrangeIcons.Edit,
                "Inbox" to StrangeIcons.Inbox,
                "Person" to StrangeIcons.Person,
                "Home" to StrangeIcons.Home,
                "Menu" to StrangeIcons.Menu,
                "MoreHoriz" to StrangeIcons.MoreHoriz,
                "Calendar" to StrangeIcons.Calendar,
                "Schedule" to StrangeIcons.Schedule,
                "Search" to StrangeIcons.Search,
                "Warning" to StrangeIcons.Warning,
            )

        feature("every icon in the set") {
            scenario("carries the name it is reached by") {
                icons.forEach { (name, icon) -> withClue(name) { icon.name shouldBe name } }
            }

            scenario("is drawn on the 24 by 24 grid Material uses") {
                icons.forEach { (name, icon) ->
                    withClue(name) {
                        icon.viewportWidth shouldBe 24f
                        icon.viewportHeight shouldBe 24f
                        icon.defaultWidth.value shouldBe 24f
                        icon.defaultHeight.value shouldBe 24f
                    }
                }
            }

            scenario("has path data that parsed into at least one node") {
                icons.forEach { (name, icon) -> withClue(name) { icon.root.size shouldBeGreaterThan 0 } }
            }
        }

        feature("the set as a whole") {
            scenario("names each icon once") {
                icons.map { it.first }.toSet().size shouldBe icons.size
            }
        }
    })
