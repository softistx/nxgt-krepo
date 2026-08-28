package com.strange.demo.spring

import com.strange.demo.api.DemoServer
import com.strange.demo.api.startDemoServer
import com.strange.demo.spring.api.models.CategoryRequest
import com.strange.demo.spring.api.models.SearchRequest
import com.strange.demo.spring.api.models.TagRequest
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Drives the generated Spring client against the real demo server over HTTP.
 *
 * Worth more than it looks: the server serialises with kotlinx.serialization and this client
 * deserialises with Jackson 3, so the test also pins down that both sides read the same document
 * the same way — including `date-time`, which the two libraries map to different Kotlin types.
 */
class EndToEndTest :
    FeatureSpec({

        lateinit var server: DemoServer
        lateinit var client: SpringDemoClient

        beforeSpec {
            server = startDemoServer(port = 0)
            client = SpringDemoClient(server.baseUrl)
        }

        afterSpec { server.close() }

        feature("reading and writing a resource") {
            scenario("creates a category and reads it back by id") {
                val created = client.categories.createCategory(CategoryRequest(name = "books", family = "media"))
                created.name shouldBe "books"
                created.metadata.createdBy shouldBe "demo"

                client.categories.findCategory(created.id) shouldBe created
            }
        }

        feature("pagination") {
            scenario("a paginated search comes back with its page info") {
                repeat(2) { client.tags.createTag(TagRequest(name = "tag-$it")) }

                val page = client.tags.findTags(SearchRequest(), first = 1)
                page.data.shouldNotBeNull().size shouldBe 1
                page.metadata.shouldNotBeNull().hasNextPage shouldBe true
            }
        }
    })
