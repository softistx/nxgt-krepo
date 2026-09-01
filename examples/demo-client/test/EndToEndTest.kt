package com.softistx.demo.client

import com.softistx.demo.api.startDemoServer
import com.softistx.demo.client.api.models.CategoryRequest
import com.softistx.demo.client.api.models.PatchTagRequest
import com.softistx.demo.client.api.models.SearchRequest
import com.softistx.demo.client.api.models.TagRequest
import com.softistx.demo.client.api.utils.ErrorResponseException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Drives the generated Ktorfit client against the real demo server over HTTP.
 *
 * This is what proves the whole chain: the plugin's generated interfaces, ktorfit-ksp's
 * implementations of them, and the server's own hand-written view of the same spec.
 */
class EndToEndTest :
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

        feature("reading and writing a resource") {
            scenario("creates a category and reads it back by id") {
                val created =
                    client.categories.createCategory(
                        CategoryRequest(name = "books", family = "media", description = "printed things"),
                    )
                created.name shouldBe "books"
                created.metadata.createdBy shouldBe "demo"

                val fetched = client.categories.findCategory(created.id)
                fetched shouldBe created
            }

            scenario("patch leaves omitted fields alone") {
                val tag = client.tags.createTag(TagRequest(name = "kotlin", family = "lang"))
                val patched = client.tags.patchTag(tag.id, PatchTagRequest(description = "the language"))

                patched.name shouldBe "kotlin"
                patched.family shouldBe "lang"
                patched.description shouldBe "the language"
            }

            scenario("delete removes the resource and a later read fails") {
                val tag = client.tags.createTag(TagRequest(name = "gone"))
                client.tags.deleteTag(tag.id)

                shouldThrow<ErrorResponseException> { client.tags.findTag(tag.id) }.status shouldBe 404
            }
        }

        feature("pagination") {
            scenario("paginates through a search") {
                repeat(3) { client.categories.createCategory(CategoryRequest(name = "bulk-$it")) }
                val all =
                    client.categories
                        .findCategories(SearchRequest())
                        .data
                        .shouldNotBeNull()

                val firstPage = client.categories.findCategories(SearchRequest(), first = 2)
                firstPage.data.shouldNotBeNull().map { it.id } shouldContainExactly all.take(2).map { it.id }
                val page = firstPage.metadata.shouldNotBeNull()
                page.hasNextPage shouldBe true

                val nextPage =
                    client.categories.findCategories(
                        SearchRequest(),
                        cursor = page.endCursor,
                        first = 2,
                    )
                nextPage.data.shouldNotBeNull().map { it.id } shouldContainExactly
                    all.drop(2).take(2).map { it.id }
            }
        }
    })
