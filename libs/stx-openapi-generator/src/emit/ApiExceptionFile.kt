package com.softistx.openapi.emit

import com.softistx.openapi.ApiModel
import com.softistx.openapi.TypeRef
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec

internal const val API_EXCEPTION: String = "ApiException"

internal const val EXCEPTIONS_FILE: String = "ApiExceptions"

/** The base exception, by the name it has in the generated package. */
internal fun apiExceptionName(options: EmitOptions): ClassName = ClassName(options.utilPackage, API_EXCEPTION)

/** `ErrorResponse` -> `ErrorResponseException`. */
internal fun exceptionNameFor(schema: String): String = "${schema}Exception"

/**
 * Every error schema the document actually uses, in the order a reader would meet them.
 *
 * Only a [TypeRef.ModelRef] body gets a typed exception: it is the one shape that names a
 * declaration this run generates, and so the one shape the generated dispatch can decode into
 * without reflection. A documented failure whose body is a bare string, a list, or a type the
 * consumer owns still reaches the caller — as [API_EXCEPTION], with the raw body attached.
 */
internal fun ApiModel.errorSchemas(): List<String> =
    groups
        .asSequence()
        .flatMap { it.operations }
        .flatMap { it.errors }
        .mapNotNull { (it.type as? TypeRef.ModelRef)?.name }
        .distinct()
        .sorted()
        .toList()

/**
 * The exception hierarchy a generated client throws.
 *
 * A non-2xx response is a documented outcome that the client had no way to express: the return
 * type describes the success and nothing described the failure, so a `404` carrying an
 * `ErrorResponse` surfaced as whatever the deserializer happened to say about a body that was not
 * the success type. One exception per error *schema* — not per status code — because the schema is
 * the document's own vocabulary, while `NotFoundException` would be this generator's invention and
 * two documents rarely mean the same thing by the same code.
 *
 * Thrown rather than returned. A sealed result type would let the compiler force the caller to
 * handle it, but it would change every generated signature and would have to be built twice, once
 * per client style — and the two styles agreeing on their signatures is the property this generator
 * has kept since its first version.
 */
internal fun apiExceptionFile(
    model: ApiModel,
    options: EmitOptions,
): FileSpec {
    val base =
        TypeSpec
            .classBuilder(API_EXCEPTION)
            .addModifiers(KModifier.PUBLIC, KModifier.OPEN)
            .addKdoc(
                """
                A response the document does not describe as a success.

                Thrown for every non-2xx status, including ones the document never mentions — a
                server is not obliged to have documented the way it fails. A subclass means the
                document described the body *and* it parsed; this class alone means one of those
                was not true, and [rawBody] is what arrived.
                """.trimIndent(),
            ).superclass(RUNTIME_EXCEPTION)
            .addSuperclassConstructorParameter("message")
            .primaryConstructor(
                FunSpec
                    .constructorBuilder()
                    .addParameter("status", INT)
                    .addParameter("rawBody", STRING.copy(nullable = true))
                    .addParameter(
                        ParameterSpec
                            .builder("message", STRING)
                            .defaultValue("%P", "HTTP \$status")
                            .build(),
                    ).build(),
            ).addProperty(
                PropertySpec
                    .builder("status", INT)
                    .initializer("status")
                    .addKdoc("The HTTP status the server answered with.")
                    .build(),
            ).addProperty(
                PropertySpec
                    .builder("rawBody", STRING.copy(nullable = true))
                    .initializer("rawBody")
                    .addKdoc("The response body exactly as it arrived, or null if there was none.")
                    .build(),
            ).build()

    val typed =
        model.errorSchemas().map { schema ->
            val bodyType = ClassName(options.modelPackage, schema)
            TypeSpec
                .classBuilder(exceptionNameFor(schema))
                .addModifiers(KModifier.PUBLIC)
                .addKdoc(
                    "A documented failure whose body is a [%T].\n\nThe document declares this " +
                        "schema for at least one non-2xx response, and the body parsed as one.",
                    bodyType,
                ).superclass(ClassName(options.utilPackage, API_EXCEPTION))
                .addSuperclassConstructorParameter("status")
                .addSuperclassConstructorParameter("rawBody")
                .addSuperclassConstructorParameter("%P", "HTTP \$status: \$error")
                .primaryConstructor(
                    FunSpec
                        .constructorBuilder()
                        .addParameter("status", INT)
                        .addParameter("rawBody", STRING.copy(nullable = true))
                        .addParameter("error", bodyType)
                        .build(),
                ).addProperty(
                    PropertySpec
                        .builder("error", bodyType)
                        .initializer("error")
                        .addKdoc("The body, parsed as the document said it would be.")
                        .build(),
                ).build()
        }

    return FileSpec
        .builder(options.utilPackage, EXCEPTIONS_FILE)
        .addFileComment(GENERATED_COMMENT)
        .addType(base)
        .apply { typed.forEach { addType(it) } }
        .build()
}

/**
 * A generated exception name must not be one the utils package already uses.
 *
 * The names in [RESERVED] are this generator's own and do not come from the document; the rest are
 * derived by appending `Exception` to a schema name, which is enough to reach one — a schema called
 * `Api` derives `ApiException`, the base class every other exception extends. That would be two
 * declarations with one name in one file set, and the last one written would win.
 *
 * Since the three packages were split apart this is the only collision left here: an interface and
 * a schema can now share a name freely, because they no longer share a package.
 */
internal fun ApiModel.requireExceptionNamesFree() {
    val clash = errorSchemas().map(::exceptionNameFor).filter { it in RESERVED }
    if (clash.isEmpty()) return
    throw EmitException(
        clash.joinToString(
            prefix =
                "these generated exceptions would collide with a declaration this generator " +
                    "already emits into the utils package: ",
            separator = ", ",
        ) { "'$it'" } + ". Rename the schema they come from with x-kotlin-name.",
    )
}

/** Every name the generator writes into the utils package that a document did not choose. */
private val RESERVED =
    setOf(
        API_EXCEPTION,
        API_OPERATION,
        AUTH_CONFIG,
        BASIC_CREDENTIALS,
        EXCEPTIONS_FILE,
        "ApiAuth",
        "ApiEnumConverters",
        "ApiErrors",
        "ApiErrorsConfig",
        "ApiProxySupport",
    )

private val RUNTIME_EXCEPTION = ClassName("kotlin", "RuntimeException")
