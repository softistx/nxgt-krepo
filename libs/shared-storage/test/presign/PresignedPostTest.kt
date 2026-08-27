package com.strange.storage.presign

import com.strange.storage.HttpProbe
import com.strange.storage.MinioTestServer
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe

class PresignedPostTest :
    FeatureSpec({

        val hello = "posted bytes".toByteArray()

        feature("a signed upload form").config(enabled = MinioTestServer.available) {
            scenario("a browser posting the fields it was given lands the object") {
                MinioTestServer.withBucket { bucket ->
                    val form = bucket.presignedPost("uploads/avatar.png")

                    form.url shouldBe "${MinioTestServer.endpoint}/${bucket.name}"
                    form.fields shouldContainKey "policy"
                    form.fields["key"] shouldBe "uploads/avatar.png"

                    HttpProbe.postForm(form.url, form.fields, "avatar.png", hello).status shouldBe 204

                    bucket.get("uploads/avatar.png") shouldBe hello
                }
            }

            scenario("an upload larger than the policy allows is refused by the store") {
                MinioTestServer.withBucket { bucket ->
                    val form = bucket.presignedPost("uploads/small.bin", sizeRange = 1L..64L)

                    val tooBig = ByteArray(256)
                    HttpProbe.postForm(form.url, form.fields, "small.bin", tooBig).status shouldBe 400

                    bucket.exists("uploads/small.bin") shouldBe false
                    HttpProbe.postForm(form.url, form.fields, "small.bin", hello).status shouldBe 204
                    bucket.exists("uploads/small.bin") shouldBe true
                }
            }

            scenario("a content type other than the signed one is refused") {
                MinioTestServer.withBucket { bucket ->
                    val form = bucket.presignedPost("uploads/avatar.png", contentType = "image/png")

                    val wrong = form.fields + ("Content-Type" to "application/zip")
                    HttpProbe.postForm(form.url, wrong, "avatar.png", hello, contentType = "application/zip").status shouldBe 403

                    HttpProbe.postForm(form.url, form.fields, "avatar.png", hello, contentType = "image/png").status shouldBe 204
                    bucket.stat("uploads/avatar.png")!!.contentType shouldBe "image/png"
                }
            }

            scenario("the key is signed, so the form cannot be pointed at another object") {
                MinioTestServer.withBucket { bucket ->
                    val form = bucket.presignedPost("uploads/avatar.png")

                    val elsewhere = form.fields + ("key" to "uploads/somewhere-else.png")
                    HttpProbe.postForm(form.url, elsewhere, "avatar.png", hello).status shouldBe 403

                    bucket.exists("uploads/somewhere-else.png") shouldBe false
                }
            }
        }
    })
