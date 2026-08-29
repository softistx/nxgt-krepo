package com.strange.openapi.emit

import com.strange.openapi.ApiGroup
import com.strange.openapi.ApiModel
import com.strange.openapi.Operation
import com.strange.openapi.SecurityKind
import com.strange.openapi.SecurityRequirement
import com.strange.openapi.SecurityScheme
import com.strange.openapi.TypeRef
import com.strange.openapi.ktorfit.KtorfitEmitter
import com.strange.openapi.render
import com.strange.openapi.spring.SpringEmitter
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

private fun operation(
    id: String,
    vararg security: String,
) = Operation(
    id = id,
    name = id,
    httpMethod = "GET",
    path = "orders",
    parameters = emptyList(),
    returnType = TypeRef.UnitRef,
    security = security.map { SecurityRequirement(it) },
)

private val SCHEMES =
    listOf(
        SecurityScheme("Bearer", "bearer", SecurityKind.HttpBearer, description = "JWT bearer token"),
        SecurityScheme("Basic", "basic", SecurityKind.HttpBasic),
        SecurityScheme("X-Key", "xKey", SecurityKind.ApiKeyHeader, parameterName = "X-Api-Key"),
        SecurityScheme("QueryKey", "queryKey", SecurityKind.ApiKeyQuery, parameterName = "access_token"),
        SecurityScheme("Session", "session", SecurityKind.ApiKeyCookie, parameterName = "sid"),
        SecurityScheme("Flow", "flow", SecurityKind.OAuthToken),
    )

private val MODEL =
    ApiModel(
        groups = listOf(ApiGroup("OrdersApi", listOf(operation("listOrders", "Bearer"), operation("signIn")))),
        models = emptyList(),
        securitySchemes = SCHEMES,
    )

/**
 * The document already says which operations need a credential and which do not; what was missing
 * was any way for that to reach the request. Both styles now read it off `@ApiOperation`, and both
 * offer the same `ApiAuthConfig` — a credential is a fact about the document, not about the client.
 */
class ApiAuthTest :
    FeatureSpec({

        val ktorfit = KtorfitEmitter().render(MODEL).getValue("com.example.api.utils.ApiAuth")
        val spring = SpringEmitter().render(MODEL).getValue("com.example.api.utils.ApiAuth")

        feature("the credential slots") {

            scenario("one per declared scheme, whether or not an operation requires it") {
                listOf(ktorfit, spring).forEach { file ->
                    file shouldContain "public var bearer: (suspend () -> String?)? = null"
                    // Nothing in this document requires Session, and it still gets a slot.
                    file shouldContain "public var session: (suspend () -> String?)? = null"
                }
            }

            scenario("a slot suspends, so a credential that expires can be replaced behind it") {
                ktorfit shouldContain "suspend () -> String?"
            }

            scenario("basic asks for the two things it needs, not a pre-encoded string") {
                ktorfit shouldContain "public var basic: (suspend () -> BasicCredentials?)? = null"
                ktorfit shouldContain "public data class BasicCredentials("
            }

            scenario("a flow's slot says the token is the caller's to obtain") {
                ktorfit shouldContain "running one is not something this generator can do"
            }

            scenario("the document's own description reaches the slot") {
                ktorfit shouldContain "JWT bearer token"
            }
        }

        feature("where each credential is put") {

            scenario("a bearer and an oauth token go in the same header") {
                ktorfit shouldContain """"Bearer" -> {"""
                ktorfit shouldContain """"Flow" -> {"""
                ktorfit shouldContain """request.headers.append("Authorization", ""${'"'}Bearer ${'$'}it""${'"'})"""
            }

            scenario("basic is encoded here rather than by the caller") {
                listOf(ktorfit, spring).forEach { it shouldContain "Base64.encode(" }
            }

            scenario("an api key goes where the document says it goes") {
                ktorfit shouldContain """request.headers.append("X-Api-Key", it)"""
                ktorfit shouldContain """request.url.parameters.append("access_token", it)"""
                spring shouldContain """authorized.header("X-Api-Key", it)"""
                spring shouldContain """authorized.cookie("sid", it)"""
            }

            scenario("Spring rebuilds the URL for a query key, which its builder cannot append to") {
                spring shouldContain """url.queryParam("access_token", it)"""
                spring shouldContain "authorized.url(url.build(true).toUri()).build()"
            }
        }

        feature("which operations get one") {

            scenario("the requirement is read off the annotation, not guessed") {
                ktorfit shouldContain "request.annotations.filterIsInstance<ApiOperation>().firstOrNull()?.security"
                spring shouldContain "request.attributes()[SECURITY_ATTRIBUTE]"
            }

            scenario("an operation that asks for nothing is left alone") {
                val orders = KtorfitEmitter().render(MODEL).getValue("com.example.api.apis.OrdersApi")
                orders shouldContain """id = "signIn""""
                orders shouldNotContain """id = "signIn",
    security"""
            }
        }

        feature("a scheme no client can satisfy") {

            scenario("it gets no slot, because a slot for it could not work") {
                val digest = MODEL.copy(securitySchemes = SCHEMES + SecurityScheme("Digest", "digest", SecurityKind.Unsupported))
                KtorfitEmitter().render(digest).getValue("com.example.api.utils.ApiAuth") shouldNotContain "public var digest"
            }

            scenario("an operation requiring one fails the emit, naming the operation") {
                val digest =
                    MODEL.copy(
                        securitySchemes = SCHEMES + SecurityScheme("Digest", "digest", SecurityKind.Unsupported),
                        groups = listOf(ApiGroup("OrdersApi", listOf(operation("listOrders", "Digest")))),
                    )
                val message = shouldThrow<EmitException> { KtorfitEmitter().render(digest) }.message
                message.shouldNotBeNull() shouldContain "OrdersApi.listOrders requires 'Digest'"
            }
        }

        feature("a document that declares no scheme") {

            scenario("gets no auth file at all") {
                val open = MODEL.copy(securitySchemes = emptyList(), groups = listOf(ApiGroup("OrdersApi", listOf(operation("signIn")))))
                KtorfitEmitter().render(open) shouldNotContainKey "com.example.api.utils.ApiAuth"
                SpringEmitter().render(open) shouldNotContainKey "com.example.api.utils.ApiAuth"
            }
        }
    })
