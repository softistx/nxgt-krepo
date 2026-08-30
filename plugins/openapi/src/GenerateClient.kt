package com.strange.openapi.plugin

import com.strange.openapi.emit.EmitOptions
import com.strange.openapi.emit.SourceEmitter
import com.strange.openapi.emit.writeAllTo
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

internal fun ClientKind.emitter(models: ModelKind): SourceEmitter =
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
internal fun ModelKind.orElse(default: ModelStyle): ModelStyle =
    when (this) {
        ModelKind.Auto -> default
        ModelKind.Kotlinx -> ModelStyle.Kotlinx
        ModelKind.Jackson -> ModelStyle.Jackson
    }

internal fun GroupBy.toGrouping(): Grouping =
    when (this) {
        GroupBy.Tag -> Grouping.Tag
        GroupBy.Path -> Grouping.Path
        GroupBy.None -> Grouping.None
    }

/**
 * Generates model classes, and optionally a typed HTTP client, from every configured document.
 *
 * One task for every spec rather than one per spec, because `plugin.yaml` cannot fan out: tasks are
 * registered statically and a list element cannot be named, so the loop is here. Everything lands in
 * one [outputDir] for the same reason — `generated.sources` must point at a registered task output —
 * and the documents stay apart by package instead.
 */
@OptIn(ExperimentalPathApi::class)
@TaskAction
public fun generateClient(
    @Input specs: List<SpecSettings>,
    @Output outputDir: Path,
) {
    specs.validate()

    // Hoisted out of the loop on purpose: run per spec, the second one erases the first.
    outputDir.deleteRecursively()
    outputDir.createDirectories()

    specs.forEach { it.generateInto(outputDir) }
}

/**
 * Everything that can be known to be wrong before a byte is written.
 *
 * Checked up front, and for every spec, so a five-document module reports all its mistakes in one
 * build rather than one per run.
 */
internal fun List<SpecSettings>.validate() {
    require(isNotEmpty()) {
        "openapi: the plugin is enabled but `specs` is empty; " +
            "list at least one document, or remove the plugin from this module"
    }

    forEach { spec ->
        if (!spec.spec.isRegularFile()) {
            error("openapi: spec file not found: ${spec.spec}")
        }
        require(spec.packageName.isNotBlank()) {
            "openapi: packageName must not be blank (for ${spec.spec.fileName})"
        }
    }

    // Two documents in one package overwrite each other file for file, and `writeAllTo` cannot see
    // it: its duplicate check covers one document's own files, not two documents' in sequence. So
    // the only symptom would be a class that silently belongs to whichever spec was listed last.
    groupBy { it.packageName }
        .filterValues { it.size > 1 }
        .forEach { (packageName, clashing) ->
            error(
                "openapi: $packageName is the packageName of ${clashing.size} specs " +
                    "(${clashing.joinToString { "${it.spec.fileName}" }}). " +
                    "Give each document a package of its own — they would otherwise overwrite each other.",
            )
        }
}

/** One document, generated. */
private fun SpecSettings.generateInto(outputDir: Path) {
    val naming = InterfaceNaming(prefix = interfacePrefix, suffix = interfaceSuffix)
    val model = OpenApiParser(groupBy.toGrouping(), naming).parse(spec)
    val files = client.emitter(models).emit(model, EmitOptions(packageName))
    files.writeAllTo(outputDir)

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
    println("openapi: generated $surface ${model.models.size} model(s) from ${spec.fileName} into $packageName")
}
