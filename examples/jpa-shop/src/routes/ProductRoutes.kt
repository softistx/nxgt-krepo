package com.strange.example.shop.routes

import com.strange.example.shop.domain.ProductService
import com.strange.example.shop.domain.ProductSpecs
import com.strange.example.shop.model.EditProduct
import com.strange.example.shop.model.NewProduct
import com.strange.example.shop.model.ProductPage
import com.strange.example.shop.model.view
import com.strange.jpa.criteria.and
import com.strange.jpa.session.session
import com.strange.jpa.session.transaction
import com.strange.ktor.jpa.jpa
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * The layer above the service: HTTP in, HTTP out, and nothing about persistence.
 *
 * **Read in `session { }`, write in `transaction { }`.** A session flushes only inside a
 * transaction, so a write handed a plain session would report success and store nothing — every
 * write verb on the session refuses rather than allowing that, and these routes are what refusing
 * protects.
 */
fun Route.productRoutes() {
    route("/products") {
        // GET /products?search=&under=&first=20&skip=0
        get {
            val first = call.request.queryParameters["first"]?.toIntOrNull() ?: 20
            val skip = call.request.queryParameters["skip"]?.toIntOrNull() ?: 0
            val search = call.request.queryParameters["search"]
            val under = call.request.queryParameters["under"]?.toLongOrNull()

            // The specifications compose: whichever the query string asked for, `and`ed together.
            var spec = ProductSpecs.available
            search?.let { spec = spec and ProductSpecs.named(it) }
            under?.let { spec = spec and ProductSpecs.upTo(it) }

            // The service orders by name and then by the identifier, so that a page boundary cannot
            // land between two products sharing a name.
            val page = call.jpa.session { session -> service(null).page(session, first, skip, spec) }

            call.respond(
                ProductPage(
                    data = page.data.map { it.view() },
                    hasPreviousPage = page.info.hasPreviousPage,
                    hasNextPage = page.info.hasNextPage,
                ),
            )
        }

        // GET /products/summary — three columns, packaged by Hibernate into the result class
        get("/summary") {
            val first = call.request.queryParameters["first"]?.toIntOrNull() ?: 20
            call.respond(call.jpa.session { session -> service(null).summaries(session, first) })
        }

        get("/{id}") {
            val id = call.parameters["id"]!!.toLong()
            // byId throws JpaNotFoundException, which the failure handler turns into a 404
            val product = call.jpa.session { session -> service(null).byId(session, id) }
            call.respond(product.view())
        }

        post {
            val input = call.receive<NewProduct>()
            val created =
                call.jpa.transaction { session ->
                    service(call.principalName()).create(session, input)
                }
            call.respond(HttpStatusCode.Created, created.view())
        }

        patch("/{id}") {
            val id = call.parameters["id"]!!.toLong()
            val input = call.receive<EditProduct>()
            val updated =
                call.jpa.transaction { session ->
                    service(call.principalName()).update(session, id, input)
                }
            call.respond(updated.view())
        }

        delete("/{id}") {
            val id = call.parameters["id"]!!.toLong()
            call.jpa.transaction { session -> service(call.principalName()).delete(session, id) }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

/** Per request, because the principal is: it is what the service stamps into the audit columns. */
private fun service(principal: String?) = ProductService(principal = principal)

/** Stand-in for real authentication — the point is only that the principal arrives per call. */
private fun io.ktor.server.application.ApplicationCall.principalName(): String? = request.headers["X-Acting-As"]
