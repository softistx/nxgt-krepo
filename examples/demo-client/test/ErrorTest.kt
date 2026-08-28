package com.strange.demo.client

import com.strange.demo.api.startDemoServer
import com.strange.demo.client.api.models.TagRequest
import com.strange.demo.client.api.models.TriggerFailureMode
import com.strange.demo.client.api.utils.ApiException
import com.strange.demo.client.api.utils.ErrorResponseException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * What a documented failure looks like at the call site.
 *
 * Before this, a `404` carrying an `ErrorResponse` was handed to kotlinx as if it were the success
 * type, and what surfaced was a deserialization complaint about a body of the wrong shape: the
 * status never reached the caller and the parsed body never existed. The generated `ApiErrors`
 * plugin is what closes that, and only a round-trip against the real server can show it does.
 */
class ErrorTest :
    FeatureSpec({
        lateinit var server: AutoCloseable
        lateinit var client: DemoClient

        beforeSpec {
            val started = startDemoServer(port = 0)
            server = started
            client = DemoClient(started.baseUrl)
        }

        afterSpec {
            client.close()
            server.close()
        }

        feature("a failure the document describes") {

            scenario("a 404 arrives as its own exception, with the body parsed") {
                val tag = client.tags.createTag(TagRequest(name = "gone"))
                client.tags.deleteTag(tag.id)

                val thrown = shouldThrow<ErrorResponseException> { client.tags.findTag(tag.id) }
                thrown.status shouldBe 404
                thrown.error.status shouldBe 404
                thrown.error.message shouldBe "no such tag"
                thrown.rawBody.orEmpty() shouldContain "no such tag"
            }

            scenario("the typed exception is still an ApiException, so one catch covers both") {
                shouldThrow<ApiException> {
                    client.failures.triggerFailure(TriggerFailureMode.TYPED)
                }.status shouldBe 404
            }
        }

        feature("a failure the document describes less completely") {

            scenario("a documented status with no body is still that status") {
                val thrown = shouldThrow<ApiException> { client.failures.triggerFailure(TriggerFailureMode.BODILESS) }
                thrown.status shouldBe 503
                thrown.shouldBeInstanceOf<ApiException>()
                (thrown is ErrorResponseException) shouldBe false
            }

            scenario("a status the document never mentions still reaches the caller, with the body") {
                val thrown = shouldThrow<ApiException> { client.failures.triggerFailure(TriggerFailureMode.UNDOCUMENTED) }
                thrown.status shouldBe 418
                thrown.rawBody shouldBe "I am a teapot"
            }

            scenario("a body that does not parse does not throw a second exception over the first") {
                val thrown = shouldThrow<ApiException> { client.failures.triggerFailure(TriggerFailureMode.GARBLED) }
                thrown.status shouldBe 404
                thrown.rawBody shouldBe "not json at all"
                (thrown is ErrorResponseException) shouldBe false
            }
        }

        feature("the success path is untouched") {

            scenario("a 2xx still returns normally with the plugin installed") {
                client.failures.triggerFailure(TriggerFailureMode.OK)
            }
        }
    })
