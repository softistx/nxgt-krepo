package com.softistx.material.icon

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

/**
 * The icon paths are hand-typed SVG, which is exactly the kind of thing that is wrong by one
 * character and only shows up as a blank square at render time. Building the vector parses the
 * path, so constructing the set *is* the parse — and these scenarios are what makes the parse run.
 */
class StxIconsTest :
    FeatureSpec({
        val icons =
            listOf(
                "Add" to StxIcons.Add,
                "Check" to StxIcons.Check,
                "Close" to StxIcons.Close,
                "ChevronUp" to StxIcons.ChevronUp,
                "ChevronLeft" to StxIcons.ChevronLeft,
                "ChevronRight" to StxIcons.ChevronRight,
                "ChevronDown" to StxIcons.ChevronDown,
                "Eye" to StxIcons.Eye,
                "EyeOff" to StxIcons.EyeOff,
                "Delete" to StxIcons.Delete,
                "Edit" to StxIcons.Edit,
                "Inbox" to StxIcons.Inbox,
                "Person" to StxIcons.Person,
                "Home" to StxIcons.Home,
                "Menu" to StxIcons.Menu,
                "MoreHoriz" to StxIcons.MoreHoriz,
                "Calendar" to StxIcons.Calendar,
                "Schedule" to StxIcons.Schedule,
                "Star" to StxIcons.Star,
                "Search" to StxIcons.Search,
                "Warning" to StxIcons.Warning,
                "Copy" to StxIcons.Copy,
                "Minus" to StxIcons.Minus,
                "Attach" to StxIcons.Attach,
                "Info" to StxIcons.Info,
                "ViewList" to StxIcons.ViewList,
                "ViewGrid" to StxIcons.ViewGrid,
                "Send" to StxIcons.Send,
                "Pin" to StxIcons.Pin,
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
