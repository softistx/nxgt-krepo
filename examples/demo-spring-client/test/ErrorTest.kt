package com.strange.demo.spring

import com.strange.demo.api.DemoServer
import com.strange.demo.api.startDemoServer
import com.strange.demo.spring.api.models.TagRequest
import com.strange.demo.spring.api.models.TriggerFailureMode
import com.strange.demo.spring.api.utils.ApiException
import com.strange.demo.spring.api.utils.ErrorResponseException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * What a documented failure looks like at the call site.
 *
 * Before this, a failed call surfaced as Spring's own `WebClientResponseException` carrying an
 * unparsed body, and the document's account of the failure went unread. The generated processor and
 * filter are what close that — the processor puts the operation in a request attribute, the filter
 * reads it back beside the response — and only a round-trip against the real server shows that the
 * attribute survives `WebClientAdapter` and that a `Mono.error` reaches a `suspend` caller intact.

 * The exceptions are also the same shape the Ktorfit client throws, which is the property that
 * keeps the two styles substitutable.
 */
class ErrorTest :
    FeatureSpec({
        lateinit var server: DemoServer
        lateinit var client: SpringDemoClient

        beforeSpec {
            server = startDemoServer(port = 0)
            client = SpringDemoClient(server.baseUrl)
        }

        afterSpec { server.close() }

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
