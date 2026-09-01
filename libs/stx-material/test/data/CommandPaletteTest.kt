package com.softistx.material.data

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

class CommandPaletteTest :
    FeatureSpec({
        val items =
            listOf(
                CommandItem("New order", onRun = {}, group = "Orders"),
                CommandItem("Export CSV", onRun = {}, group = "Orders"),
                CommandItem("Settings", onRun = {}, group = "App"),
            )

        feature("filtering") {
            scenario("an empty query keeps every command") {
                filterCommands(items, "").size shouldBe 3
            }

            scenario("matches a label, ignoring case") {
                filterCommands(items, "csv").map { it.label } shouldBe listOf("Export CSV")
            }

            scenario("a miss returns nothing, not the whole list") {
                filterCommands(items, "banana") shouldBe emptyList()
            }
        }
    })
