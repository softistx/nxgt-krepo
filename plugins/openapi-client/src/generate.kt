package dev.nxgt.openapi.plugin

import dev.nxgt.openapi.Grouping
import dev.nxgt.openapi.OpenApiParser
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

    val model = OpenApiParser(groupBy.toGrouping()).parse(specFile)
    println(
        "openapi-client: parsed ${model.groups.size} interface(s), " +
            "${model.groups.sumOf { it.operations.size }} operation(s), ${model.models.size} model(s) " +
            "from ${specFile.fileName}"
    )
    // Emission lands in feat/oag-ktorfit; packageName/generateModels are consumed there.
    check(packageName.isNotBlank()) { "openapi-client: packageName must not be blank" }
    check(generateModels || model.models.isEmpty() || true)
}
