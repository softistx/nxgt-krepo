package com.softistx.demo.api.routes

import com.softistx.demo.api.model.ErrorResponse
import com.softistx.demo.api.model.SessionInfo
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * `/session`: the two endpoints that make the document's `security` observable.
 *
 * `/whoami` inherits the root `security: - Bearer: []`, so a generated client has to attach a
 * token to reach it. `/echo` overrides that with `security: []` and reports what it received,
 * which is the only way to see that the client attached *nothing* — a header that is correctly
 * absent leaves no other trace.
 */
internal fun Route.sessionRoutes() {
    get("/session/whoami") {
        val token =
            call.request.headers[HttpHeaders.Authorization]
                ?.removePrefix("Bearer ")
                ?.takeIf { it.isNotBlank() }
        if (token == null) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse(status = 401, message = "no bearer token"))
        } else {
            call.respond(SessionInfo(subject = token))
        }
    }

    get("/session/echo") {
        call.respond(SessionInfo(authorization = call.request.headers[HttpHeaders.Authorization]))
    }
}
