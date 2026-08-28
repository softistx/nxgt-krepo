package com.strange.example.shop.routes

import com.strange.example.shop.domain.Product
import com.strange.example.shop.domain.ProductRepository
import com.strange.example.shop.domain.ProductService
import com.strange.example.shop.model.EditProduct
import com.strange.example.shop.model.NewProduct
import com.strange.example.shop.model.ProductPage
import com.strange.example.shop.model.view
import com.strange.jpa.JpaNotFoundException
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
 * transaction, so a write handed a plain session would report success and store nothing. Nothing
 * checks it for you: which of the two a handler opens is the handler's decision, and these four
 * lines are where it is made.
 */
fun Route.productRoutes() {
    val repository = ProductRepository()

    route("/products") {
        // GET /products?search=&under=&first=20&skip=0
        get {
            val first = call.request.queryParameters["first"]?.toIntOrNull() ?: 20
            val skip = call.request.queryParameters["skip"]?.toIntOrNull() ?: 0
            val search = call.request.queryParameters["search"]
            val under = call.request.queryParameters["under"]?.toLongOrNull()

            val (products, total) =
                call.jpa.session { session ->
                    // One session, two questions built from the same named filters — the sort ends
                    // with the identifier so that a page boundary cannot land between equal names.
                    repository.search(session, search, under, first, skip) to
                        repository.countMatching(session, search, under)
                }

            call.respond(
                ProductPage(
                    data = products.map { it.view() },
                    total = total,
                    hasNextPage = skip + products.size < total,
                ),
            )
        }

        // GET /products/summary — three columns, packaged by Hibernate into the result class
        get("/summary") {
            val first = call.request.queryParameters["first"]?.toIntOrNull() ?: 20
            call.respond(call.jpa.session { session -> repository.summaries(session, first) })
        }

        get("/{id}") {
            val id = call.parameters["id"]!!.toLong()
            // JpaNotFoundException is what the failure handler turns into a 404
            val product =
                call.jpa.session { session ->
                    repository.findById(session, id) ?: throw JpaNotFoundException(Product::class, id)
                }
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
