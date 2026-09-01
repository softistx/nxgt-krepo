package com.softistx.openapi.emit

import com.softistx.openapi.ApiGroup
import com.softistx.openapi.ApiModel
import com.softistx.openapi.ErrorResponse
import com.softistx.openapi.Field
import com.softistx.openapi.ObjectType
import com.softistx.openapi.Operation
import com.softistx.openapi.TypeRef
import com.softistx.openapi.ktorfit.KtorfitEmitter
import com.softistx.openapi.render
import com.softistx.openapi.spring.SpringEmitter
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

private fun operation(
    id: String,
    vararg errors: ErrorResponse,
) = Operation(
    id = id,
    name = id,
    httpMethod = "GET",
    path = "orders/{id}",
    parameters = emptyList(),
    returnType = TypeRef.UnitRef,
    errors = errors.toList(),
)

private val PROBLEM = TypeRef.ModelRef("Problem")
private val VALIDATION = TypeRef.ModelRef("Validation")

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
                                ErrorResponse("404", PROBLEM),
                                ErrorResponse("500", PROBLEM),
                                ErrorResponse("400", VALIDATION),
                                ErrorResponse("401", null),
                            ),
                            operation("listOrders", ErrorResponse("default", PROBLEM)),
                            operation("ping"),
                        ),
                ),
            ),
        models =
            listOf(
                ObjectType(name = "Problem", fields = listOf(Field("detail", "detail", TypeRef.StringRef, required = true))),
                ObjectType(name = "Validation", fields = listOf(Field("field", "field", TypeRef.StringRef, required = true))),
            ),
    )

/**
 * The generated dispatch is the document's table of failures, written as code.
 *
 * It is a `when` rather than a map built at runtime because each leaf has to name a deserializer —
 * a `KSerializer` for kotlinx, a class literal for Jackson — and naming one statically is what
 * keeps reflection out of the error path.
 */
class ApiErrorDispatchTest :
    FeatureSpec({

        val ktorfit = KtorfitEmitter().render(MODEL).getValue("com.example.api.utils.ApiErrors")
        val spring = SpringEmitter().render(MODEL).getValue("com.example.api.utils.ApiErrors")

        feature("how a status becomes an exception") {

            scenario("statuses sharing an error schema share one branch") {
                // All 169 error responses in the demo document are the same shape; a branch per
                // status would be a hundred lines saying the same thing.
                ktorfit shouldContain "404, 500 -> problemError(json, status, rawBody)"
            }

            scenario("a second schema on the same operation gets its own branch") {
                ktorfit shouldContain "400 -> validationError(json, status, rawBody)"
            }

            scenario("a status with no declared body falls through to the base exception") {
                // 401 above is documented but bodiless: nothing to parse, so nothing to name.
                ktorfit shouldNotContain "401 ->"
            }

            scenario("the document's default response becomes the client's else") {
                ktorfit shouldContain """"listOrders" -> when (status) {"""
                ktorfit shouldContain "else -> problemError(json, status, rawBody)"
            }

            scenario("an operation that declares no typed failure is not in the table at all") {
                ktorfit shouldNotContain """"ping" ->"""
            }
        }

        feature("what each style deserializes with") {

            scenario("kotlinx names the serializer, so nothing is resolved by reflection") {
                ktorfit shouldContain "json.decodeFromString(Problem.serializer(), rawBody)"
            }

            scenario("Jackson reads through a mapper that has found its modules") {
                spring shouldContain "mapper.readValue(rawBody, Problem::class.java)"
                spring shouldContain "JsonMapper.builder().findAndAddModules().build()"
            }

            scenario("a body that does not parse becomes the base exception, not a second failure") {
                listOf(ktorfit, spring).forEach { it shouldContain "catch (e: Exception)" }
            }
        }

        feature("how each style reaches the HTTP layer") {

            scenario("Ktorfit reads the annotation off the request, inside on(Send)") {
                ktorfit shouldContain "on(Send) { request ->"
                ktorfit shouldContain "request.annotations.filterIsInstance<ApiOperation>()"
            }

            scenario("Spring carries the operation from the proxy to the filter as an attribute") {
                val support = SpringEmitter().render(MODEL).getValue("com.example.api.utils.ApiProxySupport")
                support shouldContain "method.getAnnotation(ApiOperation::class.java)"
                support shouldContain "builder.addAttribute(OPERATION_ATTRIBUTE, it.id)"
                spring shouldContain "request.attributes()[OPERATION_ATTRIBUTE]"
            }
        }

        feature("a document with nothing to dispatch") {

            scenario("no typed failure anywhere means no error file at all") {
                val plain = MODEL.copy(groups = MODEL.groups.map { group -> group.copy(operations = listOf(operation("ping"))) })
                KtorfitEmitter().render(plain) shouldNotContainKey "com.example.api.utils.ApiErrors"
                SpringEmitter().render(plain) shouldNotContainKey "com.example.api.utils.ApiErrors"
            }
        }
    })
