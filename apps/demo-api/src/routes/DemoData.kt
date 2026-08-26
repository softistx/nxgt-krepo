package com.strange.demo.api.routes

import com.strange.demo.api.model.Category
import com.strange.demo.api.model.ErrorResponse
import com.strange.demo.api.model.Tag
import com.strange.demo.api.store.Ids
import com.strange.demo.api.store.Store
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

/** Holds one server's data. Each [startDemoServer] call gets its own, so tests never share state. */
internal class DemoData {
    val ids = Ids()
    val categories = Store<Category> { it.id }
    val tags = Store<Tag> { it.id }
}

internal fun ApplicationCall.id(): String = parameters["id"].orEmpty()

internal fun ApplicationCall.cursor(): String? = request.queryParameters["cursor"]

internal fun ApplicationCall.first(): Int? = request.queryParameters["first"]?.toIntOrNull()

internal fun ApplicationCall.last(): Int? = request.queryParameters["last"]?.toIntOrNull()

internal suspend fun ApplicationCall.notFound(what: String) {
    respond(HttpStatusCode.NotFound, ErrorResponse(status = 404, message = "no such $what"))
}
