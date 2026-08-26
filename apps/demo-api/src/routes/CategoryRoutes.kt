package com.strange.demo.api.routes

import com.strange.demo.api.model.Category
import com.strange.demo.api.model.CategoryRequest
import com.strange.demo.api.model.PaginatedCategory
import com.strange.demo.api.model.SearchRequest
import com.strange.demo.api.model.audit
import com.strange.demo.api.model.touched
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

/** The `/categories` half of the spec slice this demo server implements. */
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
                ),
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
                    ),
                ),
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
                    ),
                ),
            )
        }

        delete("/{id}") {
            if (!data.categories.remove(call.id())) return@delete call.notFound("category")
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
