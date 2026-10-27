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
 *
 * `/either` and `/optional` have `security` with several alternatives, and report the header the
 * same way: it is what shows which alternative a client chose, and that it did not send two.
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

    // The server does not check the credential here: what these two exist for is the header the
    // client chose to send, which the echo shows.
    get("/session/either") {
        call.respond(SessionInfo(authorization = call.request.headers[HttpHeaders.Authorization]))
    }

    get("/session/optional") {
        call.respond(SessionInfo(authorization = call.request.headers[HttpHeaders.Authorization]))
    }
}
