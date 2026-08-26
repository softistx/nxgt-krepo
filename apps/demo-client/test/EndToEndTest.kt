package dev.nxgt.demo.client

import dev.nxgt.demo.api.startDemoServer
import dev.nxgt.demo.client.api.model.CategoryRequest
import dev.nxgt.demo.client.api.model.PatchTagRequest
import dev.nxgt.demo.client.api.model.SearchRequest
import dev.nxgt.demo.client.api.model.TagRequest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Drives the generated Ktorfit client against the real demo server over HTTP.
 *
 * This is what proves the whole chain: the plugin's generated interfaces, ktorfit-ksp's
 * implementations of them, and the server's own hand-written view of the same spec.
 */
class EndToEndTest : FunSpec({
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

    test("creates a category and reads it back by id") {
        val created = client.categories.createCategory(
            CategoryRequest(name = "books", family = "media", description = "printed things")
        )
        created.name shouldBe "books"
        created.metadata.createdBy shouldBe "demo"

        val fetched = client.categories.findCategory(created.id)
        fetched shouldBe created
    }

    test("paginates through a search") {
        repeat(3) { client.categories.createCategory(CategoryRequest(name = "bulk-$it")) }
        val all = client.categories.findCategories(SearchRequest()).data.shouldNotBeNull()

        val firstPage = client.categories.findCategories(SearchRequest(), first = 2)
        firstPage.data.shouldNotBeNull().map { it.id } shouldContainExactly all.take(2).map { it.id }
        firstPage.metadata.shouldNotBeNull().hasNextPage shouldBe true

        val nextPage = client.categories.findCategories(
            SearchRequest(),
            cursor = firstPage.metadata!!.endCursor,
            first = 2,
        )
        nextPage.data.shouldNotBeNull().map { it.id } shouldContainExactly
            all.drop(2).take(2).map { it.id }
    }

    test("patch leaves omitted fields alone") {
        val tag = client.tags.createTag(TagRequest(name = "kotlin", family = "lang"))
        val patched = client.tags.patchTag(tag.id, PatchTagRequest(description = "the language"))

        patched.name shouldBe "kotlin"
        patched.family shouldBe "lang"
        patched.description shouldBe "the language"
    }

    test("delete removes the resource and a later read fails") {
        val tag = client.tags.createTag(TagRequest(name = "gone"))
        client.tags.deleteTag(tag.id)

        shouldThrow<Exception> { client.tags.findTag(tag.id) }
    }
})
