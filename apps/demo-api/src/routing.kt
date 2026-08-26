package com.strange.demo.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.application.ApplicationCall

/** Holds one server's data. Each [startDemoServer] call gets its own, so tests never share state. */
internal class DemoData {
    val ids = Ids()
    val categories = Store<Category> { it.id }
    val tags = Store<Tag> { it.id }
}

internal fun Route.categoryRoutes(data: DemoData) {
    route("/categories") {
        post {
            val body = call.receive<CategoryRequest>()
            val category = data.categories.put(
                Category(
                    id = data.ids.next(),
                    name = body.name,
                    family = body.family,
                    description = body.description,
                    attributes = body.attributes,
                    metadata = audit(),
                )
            )
            call.respond(HttpStatusCode.Created, category)
        }

        post("/search") {
            // filter/sort are accepted and ignored; the demo only needs the body to round-trip
            call.receive<SearchRequest>()
            val page = data.categories.page(call.cursor(), call.first(), call.last())
            call.respond(PaginatedCategory(page.items, page.info()))
        }

        get("/{id}") {
            val category = data.categories.find(call.id()) ?: return@get call.notFound("category")
            call.respond(category)
        }

        put("/{id}") {
            val existing = data.categories.find(call.id()) ?: return@put call.notFound("category")
            val body = call.receive<CategoryRequest>()
            call.respond(
                data.categories.put(
                    existing.copy(
                        name = body.name,
                        family = body.family,
                        description = body.description,
                        attributes = body.attributes,
                        metadata = existing.metadata.touched(),
                    )
                )
            )
        }

        patch("/{id}") {
            val existing = data.categories.find(call.id()) ?: return@patch call.notFound("category")
            val body = call.receive<CategoryRequest>()
            call.respond(
                data.categories.put(
                    existing.copy(
                        name = body.name,
                        family = body.family ?: existing.family,
                        description = body.description ?: existing.description,
                        attributes = body.attributes ?: existing.attributes,
                        metadata = existing.metadata.touched(),
                    )
                )
            )
        }

        delete("/{id}") {
            if (!data.categories.remove(call.id())) return@delete call.notFound("category")
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

internal fun Route.tagRoutes(data: DemoData) {
    route("/tags") {
        post {
            val body = call.receive<TagRequest>()
            val tag = data.tags.put(
                Tag(
                    id = data.ids.next(),
                    name = body.name,
                    family = body.family,
                    description = body.description,
                    metadata = audit(),
                )
            )
            call.respond(HttpStatusCode.Created, tag)
        }

        post("/search") {
            call.receive<SearchRequest>()
            val page = data.tags.page(call.cursor(), call.first(), call.last())
            call.respond(PaginatedTag(page.items, page.info()))
        }

        get("/{id}") {
            val tag = data.tags.find(call.id()) ?: return@get call.notFound("tag")
            call.respond(tag)
        }

        put("/{id}") {
            val existing = data.tags.find(call.id()) ?: return@put call.notFound("tag")
            val body = call.receive<TagRequest>()
            call.respond(
                data.tags.put(
                    existing.copy(
                        name = body.name,
                        family = body.family,
                        description = body.description,
                        metadata = existing.metadata.touched(),
                    )
                )
            )
        }

        patch("/{id}") {
            val existing = data.tags.find(call.id()) ?: return@patch call.notFound("tag")
            val body = call.receive<PatchTagRequest>()
            call.respond(
                data.tags.put(
                    existing.copy(
                        name = body.name ?: existing.name,
                        family = body.family ?: existing.family,
                        description = body.description ?: existing.description,
                        metadata = existing.metadata.touched(),
                    )
                )
            )
        }

        delete("/{id}") {
            if (!data.tags.remove(call.id())) return@delete call.notFound("tag")
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun ApplicationCall.id(): String = parameters["id"].orEmpty()

private fun ApplicationCall.cursor(): String? = request.queryParameters["cursor"]

private fun ApplicationCall.first(): Int? = request.queryParameters["first"]?.toIntOrNull()

private fun ApplicationCall.last(): Int? = request.queryParameters["last"]?.toIntOrNull()

private suspend fun ApplicationCall.notFound(what: String) {
    respond(HttpStatusCode.NotFound, ErrorResponse(status = 404, message = "no such $what"))
}
