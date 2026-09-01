package com.softistx.demo.api.routes

import com.softistx.demo.api.model.PaginatedTag
import com.softistx.demo.api.model.PatchTagRequest
import com.softistx.demo.api.model.SearchRequest
import com.softistx.demo.api.model.Tag
import com.softistx.demo.api.model.TagRequest
import com.softistx.demo.api.model.audit
import com.softistx.demo.api.model.touched
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

/** The `/tags` half of the spec slice this demo server implements. */
internal fun Route.tagRoutes(data: DemoData) {
    route("/tags") {
        post {
            val body = call.receive<TagRequest>()
            val tag =
                data.tags.put(
                    Tag(
                        id = data.ids.next(),
                        name = body.name,
                        family = body.family,
                        description = body.description,
                        metadata = audit(),
                    ),
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
                    ),
                ),
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
                    ),
                ),
            )
        }

        delete("/{id}") {
            if (!data.tags.remove(call.id())) return@delete call.notFound("tag")
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
