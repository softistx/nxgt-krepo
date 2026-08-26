package com.strange.openapi.plugin

import com.strange.openapi.EmitOptions
import com.strange.openapi.Grouping
import com.strange.openapi.KtorfitEmitter
import com.strange.openapi.OpenApiParser
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.isRegularFile

private fun GroupBy.toGrouping(): Grouping = when (this) {
    GroupBy.Tag -> Grouping.Tag
    GroupBy.Path -> Grouping.Path
    GroupBy.None -> Grouping.None
}

/** Generates a typed HTTP client from an OpenAPI document. */
@OptIn(ExperimentalPathApi::class)
@TaskAction
public fun generateClient(
    @Input specFile: Path,
    @Output outputDir: Path,
    packageName: String,
    client: ClientKind,
    groupBy: GroupBy,
    generateModels: Boolean,
) {
    if (!specFile.isRegularFile()) {
        error("openapi-client: spec file not found: $specFile")
    }
    if (client != ClientKind.Ktorfit) {
        error("openapi-client: the $client client is not implemented yet; only Ktorfit is supported")
    }

    outputDir.deleteRecursively()
    outputDir.createDirectories()

    require(packageName.isNotBlank()) { "openapi-client: packageName must not be blank" }

    val model = OpenApiParser(groupBy.toGrouping()).parse(specFile)
    val files = KtorfitEmitter().emit(model, EmitOptions(packageName, generateModels))
    files.forEach { it.writeTo(outputDir) }

    println(
        "openapi-client: generated ${model.groups.size} interface(s), " +
            "${model.groups.sumOf { it.operations.size }} operation(s) and " +
            "${if (generateModels) model.models.size else 0} model(s) from ${specFile.fileName}"
    )
}
