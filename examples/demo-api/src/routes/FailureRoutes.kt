package com.strange.demo.api.routes

import com.strange.demo.api.model.ErrorResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * `/failures/{mode}`: a server that fails on request.
 *
 * A generated client's error path has four distinct outcomes and only one of them — a documented
 * status with a body that parses — happens by accident anywhere else in this server. The other
 * three need a server willing to misbehave on purpose, so they live here rather than being
 * approximated by mocking out the HTTP layer, which is the thing under test.
 */
internal fun Route.failureRoutes() {
    get("/failures/{mode}") {
        when (call.parameters["mode"]) {
            "ok" -> {
                call.respond(HttpStatusCode.NoContent)
            }

            // Documented, with the body the document names.
            "typed" -> {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(status = 404, message = "asked to fail"))
            }

            // Documented as a status, with nothing said about a body — and none sent.
            "bodiless" -> {
                call.respond(HttpStatusCode.ServiceUnavailable)
            }

            // A status the document never mentions. A server is not obliged to have described
            // every way it can fail.
            "undocumented" -> {
                call.respondText("I am a teapot", status = HttpStatusCode.fromValue(418))
            }

            // A documented status whose body is not what the document said it would be.
            "garbled" -> {
                call.respondText(
                    "not json at all",
                    contentType = ContentType.Text.Plain,
                    status = HttpStatusCode.NotFound,
                )
            }

            else -> {
                call.notFound("failure mode")
            }
        }
    }
}
