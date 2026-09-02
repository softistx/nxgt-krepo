package com.softistx.storage.presign

import com.softistx.storage.HttpProbe
import com.softistx.storage.InvalidExpiryException
import com.softistx.storage.MinioTestServer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

class PresignTest :
    FeatureSpec({

        val hello = "presigned bytes".toByteArray()

        feature("a signed download").config(enabled = MinioTestServer.available) {
            scenario("a client with no credential can fetch the object") {
                MinioTestServer.withBucket { bucket ->
                    bucket.put("report.pdf", hello, contentType = "application/pdf")

                    val response = HttpProbe.get(bucket.presignedGet("report.pdf"))

                    response.status shouldBe 200
                    response.body shouldBe hello
                }
            }

            scenario("the same URL without its signature is refused") {
                MinioTestServer.withBucket { bucket ->
                    bucket.put("report.pdf", hello)

                    val unsigned = bucket.presignedGet("report.pdf").substringBefore('?')

                    HttpProbe.get(unsigned).status shouldBe 403
                }
            }

            scenario("the disposition and content type asked for are the ones served") {
                MinioTestServer.withBucket { bucket ->
                    bucket.put("report.pdf", hello, contentType = "application/pdf")

                    val response =
                        HttpProbe.get(
                            bucket.presignedGet("report.pdf", filename = "quarterly.pdf", contentType = "text/plain"),
                        )

                    response.status shouldBe 200
                    response.header("content-disposition") shouldBe "attachment; filename=\"quarterly.pdf\""
                    response.header("content-type") shouldBe "text/plain"
                }
            }

            scenario("signing does not check that the object is there") {
                MinioTestServer.withBucket { bucket ->
                    // The URL is arithmetic over the key, so it signs fine and 404s on use.
                    HttpProbe.get(bucket.presignedGet("never-uploaded")).status shouldBe 404
                }
            }
        }

        feature("a signed upload").config(enabled = MinioTestServer.available) {
            scenario("a client with no credential can put the object, and only at that key") {
                MinioTestServer.withBucket { bucket ->
                    val url = bucket.presignedPut("incoming/one.bin")

                    HttpProbe.put(url, hello).status shouldBe 200
                    bucket.get("incoming/one.bin") shouldBe hello

                    // The key is inside the signature: the same URL cannot be pointed elsewhere.
                    HttpProbe.put(url.replace("incoming/one.bin", "incoming/two.bin"), hello).status shouldBe 403
                    bucket.exists("incoming/two.bin") shouldBe false
                }
            }

            scenario("a signed delete removes it") {
                MinioTestServer.withBucket { bucket ->
                    bucket.put("gone.txt", hello)

                    HttpProbe.delete(bucket.presignedDelete("gone.txt")).status shouldBe 204

                    bucket.exists("gone.txt") shouldBe false
                }
            }
        }

        feature("an expiry the store would not sign").config(enabled = MinioTestServer.available) {
            scenario("is refused here rather than at the store") {
                MinioTestServer.withBucket { bucket ->
                    shouldThrow<InvalidExpiryException> { bucket.presignedGet("a", expiry = 8.days) }
                    shouldThrow<InvalidExpiryException> { bucket.presignedGet("a", expiry = 0.seconds) }
                    shouldThrow<InvalidExpiryException> { bucket.presignedPost("a", expiry = 8.days) }
                }
            }

            scenario("an expired URL is refused by the store") {
                MinioTestServer.withBucket { bucket ->
                    bucket.put("report.pdf", hello)

                    val url = bucket.presignedGet("report.pdf", expiry = 1.seconds)
                    delay(1_500)

                    val response = HttpProbe.get(url)
                    response.status shouldBe 403
                    response.text shouldContain "Request has expired"
                }
            }
        }
    })
