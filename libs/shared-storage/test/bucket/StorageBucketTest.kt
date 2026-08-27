package com.strange.storage.bucket

import com.strange.storage.MinioTestServer
import com.strange.storage.ObjectNotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.minio.RemoveObjectsArgs
import io.minio.messages.DeleteRequest
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import java.io.ByteArrayInputStream

class StorageBucketTest :
    FeatureSpec({

        val hello = "hello object storage".toByteArray()

        feature("a round trip").config(enabled = MinioTestServer.available) {
            scenario("what went in comes back, with the content type it was given") {
                MinioTestServer.withBucket { bucket ->
                    val stored = bucket.put("notes/one.txt", hello, contentType = "text/plain")

                    stored.key shouldBe "notes/one.txt"
                    stored.etag.shouldNotBeNull()

                    bucket.get("notes/one.txt") shouldBe hello
                    bucket.stat("notes/one.txt")!!.contentType shouldBe "text/plain"
                }
            }

            scenario("an object larger than one part survives the multipart path") {
                MinioTestServer.withBucket { bucket ->
                    val large = ByteArray(6 * 1024 * 1024) { (it % 251).toByte() }

                    // -1 is the unknown-length path: the SDK buffers into parts to find out.
                    bucket.put("large.bin", ByteArrayInputStream(large), size = -1)

                    bucket.stat("large.bin")!!.size shouldBe large.size.toLong()
                    bucket.get("large.bin") shouldBe large
                }
            }

            scenario("a ranged read asks for only the part it wants") {
                MinioTestServer.withBucket { bucket ->
                    bucket.put("notes/one.txt", hello)

                    val slice = bucket.read("notes/one.txt", offset = 6, length = 6) { it.readBytes() }

                    slice!!.decodeToString() shouldBe "object"
                }
            }
        }

        feature("an object that is not there").config(enabled = MinioTestServer.available) {
            scenario("reading it is null; requiring it names what was missing") {
                MinioTestServer.withBucket { bucket ->
                    bucket.get("absent") shouldBe null
                    bucket.stat("absent") shouldBe null
                    bucket.exists("absent") shouldBe false

                    val failure = shouldThrow<ObjectNotFoundException> { bucket.require("absent") }
                    failure.key shouldBe "absent"
                    failure.bucket shouldBe bucket.name
                }
            }

            scenario("deleting it is not an error") {
                MinioTestServer.withBucket { bucket ->
                    bucket.delete("absent")
                }
            }
        }

        feature("listing").config(enabled = MinioTestServer.available) {
            scenario("by prefix, and every key under it") {
                MinioTestServer.withBucket { bucket ->
                    bucket.put("notes/one.txt", hello)
                    bucket.put("notes/deep/two.txt", hello)
                    bucket.put("other.txt", hello)

                    bucket.list().map { it.key }.toList() shouldContainExactlyInAnyOrder
                        listOf("notes/one.txt", "notes/deep/two.txt", "other.txt")
                    bucket.list(prefix = "notes/").map { it.key }.toList() shouldContainExactlyInAnyOrder
                        listOf("notes/one.txt", "notes/deep/two.txt")
                }
            }

            scenario("without recursion the store collapses each level into one entry") {
                MinioTestServer.withBucket { bucket ->
                    bucket.put("notes/one.txt", hello)
                    bucket.put("notes/deep/two.txt", hello)

                    // S3 has no directories — `notes/deep/` is the illusion offered instead.
                    bucket.list(prefix = "notes/", recursive = false).map { it.key }.toList() shouldContainExactlyInAnyOrder
                        listOf("notes/one.txt", "notes/deep/")
                }
            }

            scenario("an empty bucket lists nothing rather than failing") {
                MinioTestServer.withBucket { bucket ->
                    bucket.list().count() shouldBe 0
                }
            }
        }

        feature("copying").config(enabled = MinioTestServer.available) {
            scenario("the bytes are copied by the store, not through us") {
                MinioTestServer.withBucket { bucket ->
                    bucket.put("notes/one.txt", hello, contentType = "text/plain")

                    bucket.copy("notes/one.txt", "notes/copy.txt").key shouldBe "notes/copy.txt"

                    bucket.get("notes/copy.txt") shouldBe hello
                    bucket.get("notes/one.txt") shouldBe hello
                }
            }
        }

        feature("deleting in bulk").config(enabled = MinioTestServer.available) {
            scenario("every key goes, and nothing is reported as refused") {
                MinioTestServer.withBucket { bucket ->
                    bucket.put("a", hello)
                    bucket.put("b", hello)

                    bucket.deleteAll(listOf("a", "b", "never-existed")) shouldBe emptyList()

                    bucket.list().count() shouldBe 0
                    bucket.deleteAll(emptyList()) shouldBe emptyList()
                }
            }

            scenario("the SDK deletes nothing until its result is walked — which is why deleteAll walks it") {
                MinioTestServer.withBucket { bucket ->
                    bucket.put("a", hello)

                    val results =
                        bucket.client.removeObjects(
                            RemoveObjectsArgs
                                .builder()
                                .bucket(bucket.name)
                                .objects(listOf(DeleteRequest.Object("a")))
                                .build(),
                        )

                    bucket.exists("a") shouldBe true

                    results.forEach { it.get() }

                    bucket.exists("a") shouldBe false
                }
            }
        }
    })
