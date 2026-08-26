package dev.nxgt.openapi.plugin

import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively

/** Generates a typed HTTP client from an OpenAPI document. */
@OptIn(ExperimentalPathApi::class)
@TaskAction
public fun generateClient(
    @Input specFile: Path,
    @Output outputDir: Path,
    packageName: String,
) {
    outputDir.deleteRecursively()
    outputDir.createDirectories()
    // Emission lands in feat/oag-ktorfit.
    println("openapi-client: spec=$specFile package=$packageName")
}
