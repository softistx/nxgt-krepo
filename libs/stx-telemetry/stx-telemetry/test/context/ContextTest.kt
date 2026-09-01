package com.strange.telemetry.context

import com.strange.telemetry.fixture.Collector
import com.strange.telemetry.fixture.collecting
import com.strange.telemetry.span
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive

/**
 * The claim this library is built on, under test.
 *
 * Every scenario here is one an MDC fails. They are cheap — no exporter has to run, no container has
 * to start — and they are the reason the current span is a `CoroutineContext.Element`: without them
 * the difference between this design and a thread-local is an assertion in a README.
 */
class ContextTest :
    FeatureSpec({
        feature("the current span follows the work") {
            scenario("it survives a change of dispatcher") {
                val telemetry = collecting(Collector())
                telemetry.use {
                    withTelemetry(telemetry) {
                        span("outer") {
                            val here = currentSpan().shouldNotBeNull()
                            withContext(Dispatchers.IO) {
                                currentSpan() shouldBe here
                            }
                            withContext(Dispatchers.Default) {
                                currentSpan() shouldBe here
                            }
                        }
                    }
                }
            }

            scenario("it is still right after fifty suspensions on a shared pool") {
                val telemetry = collecting(Collector())
                telemetry.use {
                    withTelemetry(telemetry) {
                        span("outer") {
                            val here = currentSpan().shouldNotBeNull()
                            repeat(50) {
                                withContext(Dispatchers.Default) { delay(1) }
                                currentSpan() shouldBe here
                            }
                        }
                    }
                }
            }

            scenario("a plain thread borrowed from inside a span sees nothing") {
                val telemetry = collecting(Collector())
                telemetry.use {
                    withTelemetry(telemetry) {
                        span("outer") {
                            currentSpan().shouldNotBeNull()
                            var seen: Any? = "unset"
                            val thread = Thread.ofVirtual().unstarted { seen = currentSpan() }
                            thread.start()
                            thread.join()
                            // The mirror belongs to the coroutine's thread, not to every thread —
                            // which is the half of an MDC's behaviour that was never the problem.
                            seen.shouldBeNull()
                        }
                    }
                }
            }

            scenario("two sibling coroutines each see their own span") {
                val telemetry = collecting(Collector())
                telemetry.use {
                    withTelemetry(telemetry) {
                        coroutineScope {
                            val both =
                                listOf("left", "right")
                                    .map { name ->
                                        async(Dispatchers.Default) {
                                            span(name) {
                                                delay(5)
                                                currentSpan()?.spanId
                                            }
                                        }
                                    }.awaitAll()
                            both[0] shouldNotBe both[1]
                            both[0].shouldNotBeNull()
                            both[1].shouldNotBeNull()
                        }
                    }
                }
            }

            scenario("outside any span there is none, and asking is not an error") {
                currentSpan().shouldBeNull()
                currentTraceparent().shouldBeNull()
            }

            scenario("it is gone again once the span returns") {
                val telemetry = collecting(Collector())
                telemetry.use {
                    withTelemetry(telemetry) {
                        span("brief") { currentSpan().shouldNotBeNull() }
                        currentSpan().shouldBeNull()
                    }
                }
            }
        }

        feature("attributes are inherited") {
            scenario("withAttributes reaches every span and log below it") {
                val collector = Collector()
                val telemetry = collecting(collector)
                withTelemetry(telemetry) {
                    withAttributes("tenant" to "acme") {
                        span("charge", "orderId" to "A-91") {
                            span("authorise") {}
                        }
                    }
                }
                telemetry.close()

                val inner = collector.span("authorise").shouldNotBeNull()
                inner.attributes.values["tenant"] shouldBe JsonPrimitive("acme")
                inner.attributes.values["orderId"] shouldBe JsonPrimitive("A-91")
            }

            scenario("a sibling launched outside the block does not carry them") {
                val collector = Collector()
                val telemetry = collecting(collector)
                withTelemetry(telemetry) {
                    withAttributes("tenant" to "acme") { span("inside") {} }
                    span("outside") {}
                }
                telemetry.close()

                collector
                    .span("inside")
                    .shouldNotBeNull()
                    .attributes.values["tenant"] shouldBe JsonPrimitive("acme")
                collector
                    .span("outside")
                    .shouldNotBeNull()
                    .attributes.values["tenant"]
                    .shouldBeNull()
            }

            scenario("a span's own attributes stay on that span") {
                val collector = Collector()
                val telemetry = collecting(collector)
                withTelemetry(telemetry) {
                    span("outer") {
                        attribute("only", "here")
                        span("inner") {}
                    }
                }
                telemetry.close()

                collector
                    .span("outer")
                    .shouldNotBeNull()
                    .attributes.values["only"] shouldBe JsonPrimitive("here")
                collector
                    .span("inner")
                    .shouldNotBeNull()
                    .attributes.values["only"]
                    .shouldBeNull()
            }
        }

        feature("the installed default") {
            scenario("withTelemetry wins over it, which is what keeps specs apart") {
                val mine = Collector()
                val global = Collector()
                val installed = collecting(global).install()
                val scoped = collecting(mine)

                withTelemetry(scoped) { span("scoped") {} }
                installed.close()
                scoped.close()

                mine.span("scoped").shouldNotBeNull()
                global.spans shouldBe emptyList()
            }
        }
    })
