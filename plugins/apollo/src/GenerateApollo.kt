package com.strange.apollo.plugin

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

private val SCHEMA_EXTENSIONS = setOf("graphqls", "gqls", "json")
private val DOCUMENT_EXTENSIONS = setOf("graphql")

internal fun resolveSchemaFiles(
    schemaPaths: List<Path>,
    moduleRoot: Path,
): List<File> {
    val roots = schemaPaths.ifEmpty { listOf(moduleRoot.resolve("resources/graphql")) }
    return roots.flatMap { it.files(SCHEMA_EXTENSIONS) }
}

internal fun resolveDocumentFiles(
    srcDir: List<Path>,
    moduleRoot: Path,
): List<File> {
    val roots = srcDir.ifEmpty { listOf(moduleRoot.resolve("resources/graphql/documents")) }
    return roots.flatMap { it.files(DOCUMENT_EXTENSIONS) }
}

@OptIn(ExperimentalPathApi::class)
private fun Path.files(extensions: Set<String>): List<File> =
    when {
        !exists() -> {
            emptyList()
        }

        isRegularFile() -> {
            if (extension in extensions) listOf(toFile()) else emptyList()
        }

        isDirectory() -> {
            walk()
                .filter { it.isRegularFile() && it.extension in extensions }
                .map { it.toFile() }
                .toList()
        }

        else -> {
            emptyList()
        }
    }

internal fun ApolloSettings.validate(
    schemaFiles: List<File>,
    documentFiles: List<File>,
) {
    require(packageName.isNotBlank()) { "apollo: packageName must not be blank" }
    require(schemaFiles.isNotEmpty()) {
        "apollo: no schema files found; set `schemaPaths` to a .graphqls file or directory, " +
            "or put SDL under resources/graphql/"
    }
    require(documentFiles.isNotEmpty()) {
        "apollo: no operation documents found; set `srcDir` to a .graphql file or directory, " +
            "or put documents under resources/graphql/documents/"
    }
}

/**
 * Generates Apollo Kotlin models from the module's schema and operation documents.
 */
@OptIn(ExperimentalPathApi::class)
@TaskAction
public fun generateApollo(
    @Input settings: ApolloSettings,
    @Input moduleRoot: Path,
    @Output outputDir: Path,
) {
    val schemaFiles = resolveSchemaFiles(settings.schemaPaths, moduleRoot)
    val documentFiles = resolveDocumentFiles(settings.srcDir, moduleRoot)
    settings.validate(schemaFiles, documentFiles)

    outputDir.deleteRecursively()
    outputDir.createDirectories()

    runApolloCodeGen(settings, schemaFiles, documentFiles, outputDir)
    println(
        "apollo: generated ${documentFiles.size} document(s) from ${schemaFiles.size} schema file(s) " +
            "into ${settings.packageName}",
    )
}
