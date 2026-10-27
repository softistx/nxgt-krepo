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
                            distinctColours(renderSchool(screen, viewport, dark)) shouldBeGreaterThan 8
                        }
                    }
                }
            }
        }

        feature("every data state") {
            SchoolScreen.entries.forEach { screen ->
                listOf(DataState.Loading, DataState.Empty, DataState.Failed).forEach { data ->
                    scenario("paints ${screen.name} while ${data.name}") {
                        val image = renderSchool(screen, Viewport.Phone, dark = false, SchoolStart(data = data))
                        distinctColours(image) shouldBeGreaterThan 8
                    }
                }
            }
        }

        feature("the opened detail views") {
            scenario("a student's profile, wide and narrow") {
                Viewport.entries.forEach {
                    val start = SchoolStart(studentId = 3)
                    distinctColours(renderSchool(SchoolScreen.Students, it, dark = false, start)) shouldBeGreaterThan 8
                }
            }
            scenario("a conversation thread, wide and narrow") {
                Viewport.entries.forEach {
                    val start = SchoolStart(conversationId = 1)
                    distinctColours(renderSchool(SchoolScreen.Messages, it, dark = false, start)) shouldBeGreaterThan 8
                }
            }
        }
    })
