package dev.nxgt.openapi

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.BYTE_ARRAY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT

/** Emits Ktorfit-annotated interfaces plus `@Serializable` models. */
public class KtorfitEmitter : ClientEmitter {

    override fun emit(model: ClientModel, options: EmitOptions): List<FileSpec> {
        val apis = model.groups.map { group -> emitGroup(group, options) }
        val models = if (options.generateModels) {
            model.models.map { emitModel(it, options) }
        } else {
            emptyList()
        }
        return apis + models
    }

    private fun emitGroup(group: ApiGroup, options: EmitOptions): FileSpec {
        val type = TypeSpec.interfaceBuilder(group.name)
            .addKdoc("Generated from the OpenAPI document. Do not edit.")
            .apply { group.operations.forEach { addFunction(emitOperation(it, options)) } }
            .build()
        return FileSpec.builder(options.packageName, group.name)
            .addType(type)
            .build()
    }

    private fun emitOperation(operation: Operation, options: EmitOptions): FunSpec {
        val builder = FunSpec.builder(operation.name)
            .addModifiers(KModifier.ABSTRACT, KModifier.SUSPEND)
            .addAnnotation(
                AnnotationSpec.builder(ktorfitHttp(operation.httpMethod))
                    .addMember("%S", operation.path)
                    .build()
            )
            .returns(typeNameOf(operation.returnType, options))

        operation.summary?.takeIf { it.isNotBlank() }?.let { builder.addKdoc("%L", it) }
        if (operation.parameters.any { it.kind == ParamKind.Part }) {
            builder.addAnnotation(ktorfit("Multipart"))
        }
        if (operation.parameters.any { it.kind == ParamKind.Body }) {
            // Ktor refuses to serialize a body it has no Content-Type for ("Fail to prepare
            // request body for sending ... with Content-Type: null"), and the parser only
            // produces a @Body param for an application/json request body.
            builder.addAnnotation(
                AnnotationSpec.builder(ktorfit("Headers"))
                    .addMember("%S", "Content-Type: application/json")
                    .build()
            )
        }
        // Parameters that get a `= null` default must come last, or callers could not omit them.
        operation.parameters.sortedBy { it.isOptional }.forEach { builder.addParameter(emitParam(it, options)) }
        return builder.build()
    }

    private fun emitParam(param: Param, options: EmitOptions): ParameterSpec {
        val annotation = when (param.kind) {
            ParamKind.Path -> AnnotationSpec.builder(ktorfit("Path")).addMember("%S", param.wireName).build()
            ParamKind.Query -> AnnotationSpec.builder(ktorfit("Query")).addMember("%S", param.wireName).build()
            ParamKind.Header -> AnnotationSpec.builder(ktorfit("Header")).addMember("%S", param.wireName).build()
            ParamKind.Part -> AnnotationSpec.builder(ktorfit("Part")).addMember("%S", param.wireName).build()
            ParamKind.Body -> AnnotationSpec.builder(ktorfit("Body")).build()
        }
        val type = typeNameOf(param.type, options).copy(nullable = param.isOptional)
        return ParameterSpec.builder(param.name, type)
            .addAnnotation(annotation)
            .apply { if (param.isOptional) defaultValue("null") }
            .build()
    }

    /**
     * Whether the parameter is emitted as nullable with a `null` default.
     *
     * Ktorfit's KSP processor rejects a nullable `@Part` ("Part parameter type may not be
     * nullable"), so multipart parts stay non-null even when the spec marks them optional.
     */
    private val Param.isOptional: Boolean
        get() = !required && kind != ParamKind.Part

    private fun emitModel(model: ModelType, options: EmitOptions): FileSpec {
        val constructor = FunSpec.constructorBuilder()
        val properties = model.fields.map { field ->
            val type = typeNameOf(field.type, options).copy(nullable = !field.required)
            constructor.addParameter(
                ParameterSpec.builder(field.name, type)
                    .apply {
                        if (field.name != field.wireName) {
                            addAnnotation(
                                AnnotationSpec.builder(SERIAL_NAME).addMember("%S", field.wireName).build()
                            )
                        }
                        if (!field.required) defaultValue("null")
                    }
                    .build()
            )
            PropertySpec.builder(field.name, type).initializer(field.name).build()
        }
        val type = TypeSpec.classBuilder(model.name)
            .addModifiers(KModifier.DATA)
            .addAnnotation(SERIALIZABLE)
            .addKdoc("Generated from the OpenAPI document. Do not edit.")
            .primaryConstructor(constructor.build())
            .addProperties(properties)
            .build()
        return FileSpec.builder(options.modelPackage, model.name).addType(type).build()
    }

    private fun typeNameOf(type: TypeRef, options: EmitOptions): TypeName = when (type) {
        TypeRef.StringRef -> STRING
        TypeRef.IntRef -> INT
        TypeRef.LongRef -> LONG
        TypeRef.DoubleRef -> DOUBLE
        TypeRef.BooleanRef -> BOOLEAN
        TypeRef.InstantRef -> INSTANT
        TypeRef.JsonObjectRef -> JSON_OBJECT
        TypeRef.BinaryRef -> BYTE_ARRAY
        TypeRef.UnitRef -> UNIT
        is TypeRef.ListRef -> LIST.parameterizedBy(typeNameOf(type.element, options))
        is TypeRef.ModelRef -> ClassName(options.modelPackage, type.name)
    }

    private fun ktorfit(simpleName: String) = ClassName(KTORFIT_HTTP, simpleName)

    private fun ktorfitHttp(method: String) = when (method.uppercase()) {
        "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS" -> ktorfit(method.uppercase())
        else -> throw OpenApiParseException("unsupported HTTP method '$method'")
    }

    private companion object {
        const val KTORFIT_HTTP = "de.jensklingenberg.ktorfit.http"
        val SERIALIZABLE = ClassName("kotlinx.serialization", "Serializable")
        val SERIAL_NAME = ClassName("kotlinx.serialization", "SerialName")
        // kotlin.time.Instant, not kotlinx.datetime.Instant: the latter is a deprecated
        // typealias for it, and the stdlib type needs no dependency in the consuming module.
        val INSTANT = ClassName("kotlin.time", "Instant")
        val JSON_OBJECT = ClassName("kotlinx.serialization.json", "JsonObject")
    }
}
