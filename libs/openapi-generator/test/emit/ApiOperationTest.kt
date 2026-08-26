package com.strange.openapi.emit

import com.strange.openapi.ApiGroup
import com.strange.openapi.ApiModel
import com.strange.openapi.ErrorResponse
import com.strange.openapi.Field
import com.strange.openapi.ObjectType
import com.strange.openapi.Operation
import com.strange.openapi.SecurityRequirement
import com.strange.openapi.TypeRef
import com.strange.openapi.ktorfit.KtorfitEmitter
import com.strange.openapi.render
import com.strange.openapi.spring.SpringEmitter
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

private fun operation(
    id: String,
    errors: List<ErrorResponse> = emptyList(),
    security: List<SecurityRequirement> = emptyList(),
) = Operation(
    id = id,
    name = id,
    httpMethod = "GET",
    path = "orders/{id}",
    parameters = emptyList(),
    returnType = TypeRef.UnitRef,
    errors = errors,
    security = security,
)

private val MODEL =
    ApiModel(
        groups =
            listOf(
                ApiGroup(
                    name = "OrdersApi",
                    operations =
                        listOf(
                            operation(
                                "findOrder",
                                errors =
                                    listOf(
                                        ErrorResponse("404", TypeRef.ModelRef("Problem")),
                                        ErrorResponse("401", null),
                                        // A body that is not a generated declaration: no typed
                                        // exception can decode it, and none should be emitted.
                                        ErrorResponse("500", TypeRef.StringRef),
                                    ),
                                security = listOf(SecurityRequirement("Bearer")),
                            ),
                            operation("signIn"),
                        ),
                ),
            ),
        models =
            listOf(
                ObjectType(name = "Problem", fields = listOf(Field("detail", "detail", TypeRef.StringRef, required = true))),
            ),
    )

/**
 * A response arrives where the status code and the body live but the operation is anonymous. Both
 * of the per-operation facts a client needs down there — which error type a status maps to, and
 * whether to attach a credential — therefore travel on the function itself, in one annotation that
 * each client style reads its own way.
 */
class ApiOperationTest :
    FeatureSpec({

        val ktorfit = KtorfitEmitter().render(MODEL)
        val spring = SpringEmitter().render(MODEL)

        feature("the annotation on every generated function") {

            scenario("both styles emit it, with the document's operationId") {
                listOf(ktorfit, spring).forEach { files ->
                    val orders = files.getValue("com.example.api.OrdersApi")
                    orders shouldContain """@ApiOperation(
    id = "findOrder","""
                    orders shouldContain """id = "signIn""""
                }
            }

            scenario("it carries the resolved security schemes") {
                ktorfit.getValue("com.example.api.OrdersApi") shouldContain """security = ["Bearer"]"""
            }

            scenario("an operation that needs no credential says nothing rather than an empty array") {
                // `security: []` and no `security` at all are the same instruction, and the
                // annotation's own default already spells it.
                ktorfit.getValue("com.example.api.OrdersApi") shouldNotContain "security = []"
            }

            scenario("it is declared once, in the API package, and retained at runtime") {
                val declaration = ktorfit.getValue("com.example.api.ApiOperation")
                declaration shouldContain "public annotation class ApiOperation"
                declaration shouldContain "@Retention(AnnotationRetention.RUNTIME)"
                declaration shouldContain "@Target(AnnotationTarget.FUNCTION)"
            }
        }

        feature("the exceptions a client can throw") {

            scenario("one per error schema, not one per status code") {
                val exceptions = ktorfit.getValue("com.example.api.ApiExceptions")
                exceptions shouldContain "public class ProblemException("
                exceptions shouldContain "public val error: Problem"
                exceptions shouldNotContain "NotFoundException"
            }

            scenario("a body that is not a generated declaration gets no typed exception") {
                // The 500 above is a bare string; it still reaches the caller, as the base class.
                ktorfit.getValue("com.example.api.ApiExceptions") shouldNotContain "StringException"
            }

            scenario("the base class carries the status and the body as it arrived") {
                val exceptions = spring.getValue("com.example.api.ApiExceptions")
                exceptions shouldContain "public open class ApiException("
                exceptions shouldContain "public val status: Int"
                exceptions shouldContain "public val rawBody: String?"
            }

            scenario("both styles emit the same hierarchy") {
                ktorfit.getValue("com.example.api.ApiExceptions") shouldBe spring.getValue("com.example.api.ApiExceptions")
            }
        }

        feature("names the generated package has to keep free") {

            scenario("an interface named like a generated exception fails the emit") {
                val collided = MODEL.copy(groups = MODEL.groups.map { it.copy(name = "ProblemException") })
                val message = shouldThrow<EmitException> { KtorfitEmitter().render(collided) }.message
                message.shouldNotBeNull() shouldContain "'ProblemException'"
                message shouldContain "interfacePrefix"
            }
        }
    })
