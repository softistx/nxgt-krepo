package com.softistx.storage

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

class ObjectStorageTest :
    FeatureSpec({

        feature("a client that is closed").config(enabled = MinioTestServer.available) {
            scenario("closing it twice is not an error") {
                // Not a hypothetical: Ktor's DI closes every AutoCloseable it hands out when the
                // application stops, and whoever built this one has its own claim to closing it.
                val storage = MinioTestServer.connect()

                storage.close()
                storage.close()
            }
        }

        feature("buckets").config(enabled = MinioTestServer.available) {
            scenario("one is created, listed, and removed again") {
                MinioTestServer.withStorage { storage ->
                    val name = MinioTestServer.bucketName()

                    storage.bucketExists(name) shouldBe false
                    storage.ensureBucket(name).name shouldBe name

                    storage.bucketExists(name) shouldBe true
                    storage.buckets() shouldContain name

                    storage.deleteBucket(name)
                    storage.bucketExists(name) shouldBe false
                    storage.buckets() shouldNotContain name
                }
            }

            scenario("ensuring one that is already there is not an error") {
                MinioTestServer.withStorage { storage ->
                    val name = MinioTestServer.bucketName()
                    try {
                        storage.ensureBucket(name)
                        storage.ensureBucket(name)

                        storage.bucketExists(name) shouldBe true
                    } finally {
                        storage.deleteBucket(name)
                    }
                }
            }

            scenario("a handle costs nothing and asks nothing") {
                MinioTestServer.withStorage { storage ->
                    storage.bucket("never-created").name shouldBe "never-created"
                }
            }
        }
    })
