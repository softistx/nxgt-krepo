package com.softistx.openapi.emit

import com.softistx.openapi.ApiModel
import com.softistx.openapi.Operation
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec

internal const val ENDPOINT: String = "Endpoint"
internal const val ENDPOINTS: String = "Endpoints"

/**
 * The document's routes, as constants.
 *
 * The one thing a spec-first application still types by hand. A controller's specs name features
 * `feature("POST /orders")`, a Ktor server writes `route("/orders")`, and a `WebTestClient` call
 * spells `uri("/orders/{id}")` — three literals that the document cannot check and that go stale
 * without a word when a path changes. Generated, they cannot: renaming a path renames the constant,
 * and every caller of the old one stops compiling.
 *
 * It sits in [EmitOptions.packageName] itself rather than under `.utils`, which is the only file
 * that does. `.utils` is the machinery a client needs and a caller mostly does not; this is the
 * opposite — contract data a caller reads, and the one generated thing a *server* wants, having no
 * generated interface of its own to drift against.
 *
 * Emitted for every client style, `None` included. Endpoint constants are not an API surface: they
 * are the document's own strings, and the hand-written Ktor server that `client: None` exists for is
 * the case with the most to lose from typing them twice.
 */
internal fun endpointsFile(
    model: ApiModel,
    options: EmitOptions,
): FileSpec {
    val endpoint = ClassName(options.packageName, ENDPOINT)
    val operations =
        model.groups
            .flatMap { it.operations }
            .sortedBy { it.constant }

    return FileSpec
        .builder(options.packageName, ENDPOINTS)
        .addFileComment(GENERATED_COMMENT)
        .addType(endpointType(endpoint))
        .addType(endpointsObject(endpoint, operations))
        .addFunction(pathFunction(endpoint))
        .build()
}

/** The record one endpoint is. */
private fun endpointType(endpoint: ClassName): TypeSpec =
    TypeSpec
        .classBuilder(endpoint)
        .addModifiers(KModifier.PUBLIC, KModifier.DATA)
        .addKdoc(
            """
            One operation the document declares.

            A `data class` and not a `value class`: a value class carries exactly one property, and
            an endpoint is four facts. A `data class` also gives `equals`, so an endpoint can be a
            map key or an assertion subject.
            """.trimIndent(),
        ).primaryConstructor(
            FunSpec
                .constructorBuilder()
                .addParameter("method", STRING)
                .addParameter("value", STRING)
                .addParameter("operationId", STRING)
                .addParameter("summary", STRING.copy(nullable = true))
                .build(),
        ).addProperty(
            PropertySpec
                .builder("method", STRING)
                .initializer("method")
                .addKdoc("The HTTP verb, uppercase — `PUT`.")
                .build(),
        ).addProperty(
            PropertySpec
                .builder("value", STRING)
                .initializer("value")
                .addKdoc("The path template, leading slash included — `/orders/{id}`.")
                .build(),
        ).addProperty(
            PropertySpec
                .builder("operationId", STRING)
                .initializer("operationId")
                .addKdoc("The document's own `operationId` — what to grep the document for.")
                .build(),
        ).addProperty(
            PropertySpec
                .builder("summary", STRING.copy(nullable = true))
                .initializer("summary")
                .addKdoc("The operation's `summary`, or null where the document gives none.")
                .build(),
        ).addProperty(
            PropertySpec
                .builder("label", STRING)
                .getter(
                    FunSpec
                        .getterBuilder()
                        .addStatement("return %P", "[\$method] \$value")
                        .build(),
                ).addKdoc(
                    """
                    `[PUT] /orders/{id}` — the endpoint as a line of prose.

                    Computed rather than stored, so the format has one definition and cannot drift
                    from one endpoint to the next. Written for a test name:
                    `feature(Endpoints.PUT_ORDERS_ID.label)`.
                    """.trimIndent(),
                ).build(),
        ).build()

/** The constants themselves. */
private fun endpointsObject(
    endpoint: ClassName,
    operations: List<Operation>,
): TypeSpec {
    val builder =
        TypeSpec
            .objectBuilder(ENDPOINTS)
            .addModifiers(KModifier.PUBLIC)
            .addKdoc("Every operation the document declares, by verb and path.")

    operations.forEach { operation ->
        val summary = operation.summary
        builder.addProperty(
            PropertySpec
                .builder(operation.constant, endpoint)
                .initializer(
                    // `%S` and not `%L` for the summary: it is prose from the document, so it can
                    // hold a quote or a backslash, and only `%S` escapes those.
                    if (summary == null) "%T(%S, %S, %S, null)" else "%T(%S, %S, %S, %S)",
                    *listOfNotNull(endpoint, operation.httpMethod, "/" + operation.path, operation.id, summary)
                        .toTypedArray(),
                ).apply { summary?.let { addKdoc("%L", it) } }
                .build(),
        )
    }

    return builder
        .addProperty(
            PropertySpec
                .builder("all", LIST.parameterizedBy(endpoint))
                .initializer(
                    operations.joinToString(
                        prefix = "listOf(\n⇥",
                        separator = ",\n",
                        postfix = ",\n⇤)",
                    ) { it.constant },
                ).addKdoc(
                    """
                    Every endpoint above, in the order they are declared here.

                    What an object gives up against an enum, handed back: a spec can assert that the
                    document has no untested route by iterating rather than by remembering.
                    """.trimIndent(),
                ).build(),
        ).build()
}

/** Filling a template is the other half of naming one. */
private fun pathFunction(endpoint: ClassName): FunSpec =
    FunSpec
        .builder("path")
        .addModifiers(KModifier.PUBLIC)
        .receiver(endpoint)
        .addParameter(
            ParameterSpec
                .builder("values", STRING, KModifier.VARARG)
                .build(),
        ).returns(STRING)
        .addKdoc(
            """
            [Endpoint.value] with its template variables filled, in order.

            `Endpoints.GET_ORDERS_ID.path("42")` gives `/orders/42`.

            The wrong number of values throws rather than returning a half-filled path: a URL with a
            literal `{id}` still in it answers 404, and a 404 names nothing.
            """.trimIndent(),
        ).addCode(
            """
            |val template = %T("\\{[^/}]*}")
            |val slots = template.findAll(value).count()
            |require(slots == values.size) {
            |⇥"${'$'}label takes ${'$'}slots path value(s), but ${'$'}{values.size} were given"⇤
            |}
            |var next = 0
            |return template.replace(value) { values[next++] }
            |
            """.trimMargin(),
            ClassName("kotlin.text", "Regex"),
        ).build()
