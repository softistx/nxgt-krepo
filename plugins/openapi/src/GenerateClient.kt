package com.strange.openapi.plugin

import com.strange.openapi.EmitOptions
import com.strange.openapi.SourceEmitter
import com.strange.openapi.ktorfit.KtorfitEmitter
import com.strange.openapi.models.ModelStyle
import com.strange.openapi.models.ModelsOnlyEmitter
import com.strange.openapi.parser.Grouping
import com.strange.openapi.parser.InterfaceNaming
import com.strange.openapi.parser.OpenApiParser
import com.strange.openapi.spring.SpringEmitter
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.isRegularFile

private fun ClientKind.emitter(models: ModelKind): SourceEmitter =
    when (this) {
        // Ktorfit deserializes through kotlinx.serialization here, so its models are not a choice.
        // An explicit Jackson request is a mistake worth failing on rather than quietly overriding.
        ClientKind.Ktorfit -> {
            require(models != ModelKind.Jackson) {
                "openapi: client Ktorfit always generates kotlinx.serialization models; " +
                    "remove `models: Jackson`, or switch to `client: Spring`"
            }
            KtorfitEmitter()
        }

        ClientKind.Spring -> {
            SpringEmitter(models.orElse(ModelStyle.Jackson))
        }

        ClientKind.None -> {
            ModelsOnlyEmitter(models.orElse(ModelStyle.Kotlinx))
        }
    }

/** Resolves [ModelKind.Auto] against the style the caller's client implies. */
private fun ModelKind.orElse(default: ModelStyle): ModelStyle =
    when (this) {
        ModelKind.Auto -> default
        ModelKind.Kotlinx -> ModelStyle.Kotlinx
        ModelKind.Jackson -> ModelStyle.Jackson
    }

private fun GroupBy.toGrouping(): Grouping =
    when (this) {
        GroupBy.Tag -> Grouping.Tag
        GroupBy.Path -> Grouping.Path
        GroupBy.None -> Grouping.None
    }

/** Generates model classes, and optionally a typed HTTP client, from an OpenAPI document. */
@OptIn(ExperimentalPathApi::class)
@TaskAction
public fun generateClient(
    @Input specFile: Path,
    @Output outputDir: Path,
    packageName: String,
    client: ClientKind,
    groupBy: GroupBy,
    models: ModelKind,
    interfacePrefix: String,
    interfaceSuffix: String,
) {
    if (!specFile.isRegularFile()) {
        error("openapi: spec file not found: $specFile")
    }
    require(packageName.isNotBlank()) { "openapi: packageName must not be blank" }

    outputDir.deleteRecursively()
    outputDir.createDirectories()

    val naming = InterfaceNaming(prefix = interfacePrefix, suffix = interfaceSuffix)
    val model = OpenApiParser(groupBy.toGrouping(), naming).parse(specFile)
    val files = client.emitter(models).emit(model, EmitOptions(packageName))
    files.forEach { it.writeTo(outputDir) }

    val surface =
        when (client) {
            ClientKind.None -> {
                "models only"
            }

            else -> {
                "$client client — ${model.groups.size} interface(s), " +
                    "${model.groups.sumOf { it.operations.size }} operation(s) and"
            }
        }
    println("openapi: generated $surface ${model.models.size} model(s) from ${specFile.fileName}")
}
