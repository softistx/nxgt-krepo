package com.softistx.material.school

import com.softistx.material.school.session.DataState
import com.softistx.material.school.session.SchoolScreen
import com.softistx.material.school.session.SchoolStart
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

/**
 * Every screen, rendered headlessly at phone and desktop size in light and dark.
 *
 * A composable that throws at layout time compiles perfectly and only shows itself when somebody
 * opens that one screen at that one width. The grid below opens all of them, and then every
 * screen on each non-default data state, so the loading, empty and error faces cannot rot unseen.
 */
class EveryScreenRendersTest :
    FeatureSpec({
        /** Renders, checks the frame is not blank, and returns its bytes; the frame is closed. */
        fun paint(
            screen: SchoolScreen,
            viewport: Viewport,
            dark: Boolean,
            start: SchoolStart = SchoolStart(),
        ): ByteArray =
            renderSchool(screen, viewport, dark, start).use {
                distinctColours(it) shouldBeGreaterThan MinColours
                png(it)
            }

        feature("the registry") {
            scenario("lists the six screens the app claims (the dashboard is labelled Home so a phone bar fits it)") {
                SchoolScreen.entries.map { it.label } shouldBe
                    listOf("Home", "Students", "Grades", "Timetable", "Messages", "Settings")
            }
        }

        feature("every screen, size and theme") {
            SchoolScreen.entries.forEach { screen ->
                Viewport.entries.forEach { viewport ->
                    listOf(false, true).forEach { dark ->
                        scenario("paints ${screen.name} on ${viewport.name} in ${if (dark) "dark" else "light"}") {
                            paint(screen, viewport, dark)
                        }
                    }
                }
            }
        }

        feature("every data state") {
            SchoolScreen.entries.forEach { screen ->
                val loaded = lazy { paint(screen, Viewport.Phone, dark = false) }
                listOf(DataState.Loading, DataState.Empty, DataState.Failed).forEach { data ->
                    scenario("paints ${screen.name} while ${data.name}, differently from loaded") {
                        val frame = paint(screen, Viewport.Phone, dark = false, SchoolStart(data = data))
                        (frame.contentEquals(loaded.value)) shouldBe false
                    }
                }
            }
        }

        feature("the opened detail views") {
            scenario("a student's profile, wide and narrow") {
                Viewport.entries.forEach {
                    val frame = paint(SchoolScreen.Students, it, dark = false, SchoolStart(studentId = 3))
                    frame.contentEquals(paint(SchoolScreen.Students, it, dark = false)) shouldBe false
                }
            }
            scenario("a conversation thread, wide and narrow") {
                Viewport.entries.forEach {
                    val frame = paint(SchoolScreen.Messages, it, dark = false, SchoolStart(conversationId = 1))
                    frame.contentEquals(paint(SchoolScreen.Messages, it, dark = false)) shouldBe false
                }
            }
        }
    })
