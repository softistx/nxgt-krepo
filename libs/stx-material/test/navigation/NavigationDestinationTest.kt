package com.softistx.material.navigation

import com.softistx.material.icon.StxIcons
import com.softistx.material.theme.Tone
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class NavigationDestinationTest :
    FeatureSpec({
        fun destination(
            badge: String? = null,
            unread: Boolean = false,
            supporting: String? = null,
            chip: String? = null,
            shortcut: String? = null,
            section: String? = null,
            avatar: Boolean = false,
            picture: Any? = null,
        ) = NavigationDestination(
            label = "Inbox",
            icon = StxIcons.Inbox,
            supporting = supporting,
            badge = badge,
            unread = unread,
            chip = chip,
            shortcut = shortcut,
            section = section,
            avatar = avatar,
            picture = picture,
        )

        feature("the badge slot") {
            scenario("a count wins over an unread dot, because it is more specific") {
                destination(badge = "3", unread = true).resolvedBadge() shouldBe
                    ResolvedBadge.Label("3", Tone.Info)
            }

            scenario("unread without a count is a dot") {
                destination(unread = true).resolvedBadge() shouldBe ResolvedBadge.Dot
            }

            scenario("neither is nothing, so the slot stays empty") {
                destination().resolvedBadge() shouldBe ResolvedBadge.None
            }
        }

        feature("density") {
            scenario("a compact bar keeps the badge slot and drops rail-only decorations") {
                val item =
                    destination(
                        supporting = "Amara, Jonas",
                        chip = "Live",
                        shortcut = "⌘1",
                        section = "Mail",
                    )
                item.showSupporting(compact = true) shouldBe false
                item.showChip(compact = true) shouldBe false
                item.showShortcut(compact = true) shouldBe false
                item.showSection(compact = true, previous = null) shouldBe false
                item.resolvedBadge() shouldBe ResolvedBadge.None
            }

            scenario("a rail shows supporting text, chip, shortcut and a new section") {
                val item =
                    destination(
                        supporting = "Amara, Jonas",
                        chip = "Live",
                        shortcut = "⌘1",
                        section = "Mail",
                    )
                item.showSupporting(compact = false) shouldBe true
                item.showChip(compact = false) shouldBe true
                item.showShortcut(compact = false) shouldBe true
                item.showSection(compact = false, previous = null) shouldBe true
                item.showSection(compact = false, previous = "Mail") shouldBe false
            }
        }

        feature("leading") {
            scenario("a picture or an avatar flag is an Avatar, not the vector") {
                destination(avatar = true).usesAvatar() shouldBe true
                destination(picture = "https://example.com/a.jpg").usesAvatar() shouldBe true
                destination().usesAvatar() shouldBe false
            }
        }

        feature("label of a counted badge") {
            scenario("carries the tone the caller named") {
                val badge =
                    NavigationDestination(
                        label = "Inbox",
                        icon = StxIcons.Inbox,
                        badge = "12",
                        badgeTone = Tone.Warning,
                    ).resolvedBadge()
                val label = badge.shouldBeInstanceOf<ResolvedBadge.Label>()
                label.tone shouldBe Tone.Warning
            }
        }
    })
