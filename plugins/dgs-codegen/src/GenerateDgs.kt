package com.softistx.dgs.plugin

import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.io.File
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.walk

internal fun DgsLanguage.toDgsName(): String =
    when (this) {
        DgsLanguage.Kotlin -> "KOTLIN"
        DgsLanguage.Java -> "JAVA"
    }

/**
 * Schema files DGS will parse. Directories are walked; `.gqls` is included so a Graphix
 * split document under resources/graphql generates without renaming.
 */
internal fun resolveSchemaFiles(
    schemaPaths: List<Path>,
    moduleRoot: Path,
): Set<File> {
    val roots = schemaPaths.ifEmpty { listOf(moduleRoot.resolve("resources/graphql")) }
    return roots.flatMap { it.schemaFiles() }.toSet()
}

@OptIn(ExperimentalPathApi::class)
private fun Path.schemaFiles(): List<File> =
    when {
        !exists() -> emptyList()
        isRegularFile() -> listOf(toFile())
        isDirectory() -> walk().filter { it.isRegularFile() && it.isSchemaFile() }.map { it.toFile() }.toList()
        else -> emptyList()
    }

private fun Path.isSchemaFile(): Boolean = extension in SCHEMA_EXTENSIONS

private val SCHEMA_EXTENSIONS = setOf("graphqls", "gqls")

internal fun DgsCodegenSettings.validate(schemaFiles: Set<File>) {
    require(packageName.isNotBlank()) { "dgs-codegen: packageName must not be blank" }
    require(schemaFiles.isNotEmpty()) {
        "dgs-codegen: no schema files found; set `schemaPaths` to a .graphqls file or directory, " +
            "or put SDL under resources/graphql/"
    }
}

/**
 * Generates DGS types from the module's GraphQL schema.
 *
 * One task, one output directory: `plugin.yaml` cannot fan out, and `generated.sources` has to
 * name a registered task output.
 */
@OptIn(ExperimentalPathApi::class)
@TaskAction
public fun generateDgs(
    @Input settings: DgsCodegenSettings,
    @Input moduleRoot: Path,
    @Output outputDir: Path,
) {
    val schemaFiles = resolveSchemaFiles(settings.schemaPaths, moduleRoot)
    settings.validate(schemaFiles)

    outputDir.deleteRecursively()
    outputDir.createDirectories()

    runDgsCodeGen(settings, schemaFiles, outputDir)
    println("dgs-codegen: generated ${schemaFiles.size} schema file(s) into ${settings.packageName}")
}
