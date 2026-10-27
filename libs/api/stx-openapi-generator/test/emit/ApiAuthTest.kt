package com.softistx.openapi.emit

import com.softistx.openapi.ApiGroup
import com.softistx.openapi.ApiModel
import com.softistx.openapi.Operation
import com.softistx.openapi.SecurityKind
import com.softistx.openapi.SecurityRequirement
import com.softistx.openapi.SecurityScheme
import com.softistx.openapi.TypeRef
import com.softistx.openapi.ktorfit.KtorfitEmitter
import com.softistx.openapi.render
import com.softistx.openapi.spring.SpringEmitter
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

/** One alternative per argument, an alternative's schemes joined by `+`; `""` is the anonymous one. */
private fun operation(
    id: String,
    vararg alternatives: String,
) = Operation(
    id = id,
    name = id,
    httpMethod = "GET",
    path = "orders",
    parameters = emptyList(),
    returnType = TypeRef.UnitRef,
    security =
        alternatives.map { alternative ->
            alternative.split('+').filter { it.isNotEmpty() }.map { SecurityRequirement(it) }
        },
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

private val BOTH: List<SourceEmitter> = listOf(KtorfitEmitter(), SpringEmitter())

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
                ktorfit shouldContain """request.headers.append("Authorization", ""${'"'}Bearer ${'$'}value""${'"'})"""
            }

            scenario("basic is encoded here rather than by the caller") {
                listOf(ktorfit, spring).forEach { it shouldContain "Base64.encode(" }
            }

            scenario("an api key goes where the document says it goes") {
                ktorfit shouldContain """request.headers.append("X-Api-Key", value)"""
                ktorfit shouldContain """request.url.parameters.append("access_token", value)"""
                spring shouldContain """authorized.header("X-Api-Key", value)"""
                spring shouldContain """authorized.cookie("sid", value)"""
            }

            scenario("Spring rebuilds the URL for a query key, which its builder cannot append to") {
                spring shouldContain """url.queryParam("access_token", value)"""
                spring shouldContain "authorized.url(url.build(true).toUri()).build()"
            }
        }

        feature("which operations get one") {

            scenario("the requirement is read off the annotation, not guessed") {
                ktorfit shouldContain "request.annotations.filterIsInstance<ApiOperation>().firstOrNull()"
                ktorfit shouldContain "val alternatives = operation.security"
                spring shouldContain "request.attributes()[SECURITY_ATTRIBUTE]"
            }

            scenario("an operation that asks for nothing is left alone") {
                val orders = KtorfitEmitter().render(MODEL).getValue("com.example.api.apis.OrdersApi")
                orders shouldContain """id = "signIn""""
                orders shouldNotContain """id = "signIn",
    security"""
            }
        }

        // The rule is the same text in both styles, so what one pins the other has too. Running it is
        // the demo client's AuthTest; these pin that it is generated, and in what order.
        feature("choosing one alternative") {

            scenario("alternatives are walked in order and the first with every credential wins") {
                listOf(ktorfit, spring).forEach { file ->
                    file shouldContain "for (alternative in alternatives) {"
                    file shouldContain "for (scheme in alternative.split(',')) {"
                    file shouldContain "if (!complete) break"
                    file shouldContain "satisfied = true"
                }
            }

            scenario("nothing is attached until an alternative is known to be complete") {
                listOf(ktorfit, spring).forEach { file ->
                    file shouldContain "val pending = mutableListOf<() -> Unit>()"
                    file shouldContain "pending.add {"
                    file shouldContain "pending.forEach { it() }"
                }
                // The attach statement sits inside the deferred lambda, not beside the slot read.
                ktorfit.substringAfter("pending.add {").substringBefore("pending.forEach") shouldContain
                    "request.headers.append("
            }

            scenario("a slot is read when its scheme is reached, and a missing credential ends the alternative") {
                ktorfit shouldContain "val value = credentials.bearer?.invoke()"
                ktorfit shouldContain "if (value == null) {"
                ktorfit shouldContain "complete = false"
            }

            scenario("the anonymous alternative only counts when no other is complete") {
                listOf(ktorfit, spring).forEach { file ->
                    file shouldContain "if (alternative.isEmpty()) {"
                    file shouldContain "anonymous = true"
                    file shouldContain "if (!satisfied && !anonymous) {"
                }
            }

            scenario("with none complete and no anonymous alternative it throws before sending") {
                ktorfit shouldContain "throw IllegalStateException("
                ktorfit shouldContain "needs a credential for one of"
                ktorfit shouldContain "${'$'}{operation.id}"
                spring shouldContain "${'$'}{operation}"
                // Names the alternatives with `+` for an AND and `or` between them.
                ktorfit shouldContain """.joinToString(" or ") { "[" + it.replace(",", " + ") + "]" }"""
            }

            scenario("an operation with no alternatives is left alone") {
                ktorfit shouldContain "if (alternatives.isEmpty()) return@onRequest"
                spring shouldContain "if (alternatives.isNullOrEmpty()) next.exchange(request) else"
            }
        }

        feature("a scheme no client can satisfy") {

            scenario("it gets no slot, because a slot for it could not work") {
                val digest = MODEL.copy(securitySchemes = SCHEMES + SecurityScheme("Digest", "digest", SecurityKind.Unsupported))
                KtorfitEmitter().render(digest).getValue("com.example.api.utils.ApiAuth") shouldNotContain "public var digest"
            }

            fun digestModel(vararg alternatives: String) =
                MODEL.copy(
                    securitySchemes = SCHEMES + SecurityScheme("Digest", "digest", SecurityKind.Unsupported),
                    groups = listOf(ApiGroup("OrdersApi", listOf(operation("listOrders", *alternatives)))),
                )

            scenario("an operation requiring only unsupported ones fails the emit, naming the operation") {
                BOTH.forEach { emitter ->
                    val message = shouldThrow<EmitException> { emitter.render(digestModel("Digest")) }.message
                    message.shouldNotBeNull() shouldContain "OrdersApi.listOrders requires 'Digest'"
                }
            }

            scenario("so does one whose every alternative needs one, even beside a handled scheme") {
                val message =
                    shouldThrow<EmitException> { KtorfitEmitter().render(digestModel("Digest+Bearer", "Digest")) }.message
                message.shouldNotBeNull() shouldContain "OrdersApi.listOrders requires 'Digest'"
            }

            scenario("an unsupported alternative is dropped when another one is handled") {
                BOTH.forEach { emitter ->
                    val orders = emitter.render(digestModel("Digest", "Bearer")).getValue("com.example.api.apis.OrdersApi")
                    orders shouldContain """security = ["Bearer"]"""
                    orders shouldNotContain "Digest"
                }
            }

            scenario("an alternative that mixes a handled and an unsupported scheme is dropped whole") {
                val orders =
                    KtorfitEmitter()
                        .render(digestModel("Bearer+Digest", "X-Key"))
                        .getValue("com.example.api.apis.OrdersApi")
                orders shouldContain """security = ["X-Key"]"""
            }

            scenario("the anonymous alternative is a handled one, so it keeps the operation emittable") {
                val orders =
                    KtorfitEmitter().render(digestModel("Digest", "")).getValue("com.example.api.apis.OrdersApi")
                orders shouldContain """security = [""]"""
            }
        }

        feature("a scheme name the annotation cannot carry") {

            scenario("a comma in it fails the emit, since the annotation joins an alternative's schemes with one") {
                val comma =
                    MODEL.copy(
                        securitySchemes =
                            SCHEMES + SecurityScheme("A,B", "ab", SecurityKind.ApiKeyHeader, parameterName = "X"),
                        groups = listOf(ApiGroup("OrdersApi", listOf(operation("listOrders", "A,B")))),
                    )
                val message = shouldThrow<EmitException> { KtorfitEmitter().render(comma) }.message
                message.shouldNotBeNull() shouldContain "'A,B'"
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
