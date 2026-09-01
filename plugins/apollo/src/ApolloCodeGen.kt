@file:OptIn(ApolloExperimental::class)

package com.softistx.apollo.plugin

import com.apollographql.apollo.annotations.ApolloExperimental
import com.apollographql.apollo.compiler.ApolloCompiler
import com.apollographql.apollo.compiler.CodegenSchemaOptions
import com.apollographql.apollo.compiler.InputFile
import com.apollographql.apollo.compiler.UsedCoordinates
import com.apollographql.apollo.compiler.buildCodegenOptions
import com.apollographql.apollo.compiler.buildIrOptions
import com.apollographql.apollo.compiler.codegen.plus
import com.apollographql.apollo.compiler.codegen.writeTo
import java.io.File
import java.nio.file.Path

private fun List<File>.asInputFiles(): List<InputFile> = map { InputFile(it, it.name) }

internal fun runApolloCodeGen(
    settings: ApolloSettings,
    schemaFiles: List<File>,
    documentFiles: List<File>,
    outputDir: Path,
) {
    val codegenSchema =
        ApolloCompiler.buildCodegenSchema(
            schemaFiles = schemaFiles.asInputFiles(),
            logger = null,
            codegenSchemaOptions =
                CodegenSchemaOptions(
                    scalarTypeMapping = settings.mapScalar,
                    scalarAdapterMapping = settings.mapScalarAdapters,
                    generateDataBuilders = settings.generateDataBuilders,
                ),
            foreignSchemas = emptyList(),
            schemaTransform = null,
        )
    val irOperations =
        ApolloCompiler.buildIrOperations(
            codegenSchema = codegenSchema,
            executableFiles = documentFiles.asInputFiles(),
            upstreamCodegenModels = emptyList(),
            upstreamFragmentDefinitions = emptyList(),
            options =
                buildIrOptions(
                    generateOptionalOperationVariables = settings.generateOptionalOperationVariables,
                ),
            documentTransform = null,
            logger = null,
        )
    val codegenOptions =
        buildCodegenOptions(
            packageName = settings.packageName,
            useSemanticNaming = settings.useSemanticNaming,
            generateFragmentImplementations = settings.generateFragmentImplementations,
        )
    val operations =
        ApolloCompiler.buildSchemaAndOperationsSourcesFromIr(
            codegenSchema = codegenSchema,
            irOperations = irOperations,
            downstreamUsedCoordinates = UsedCoordinates(),
            upstreamCodegenMetadata = emptyList(),
            codegenOptions = codegenOptions,
            layout = null,
            operationIdsGenerator = null,
            irOperationsTransform = null,
            javaOutputTransform = null,
            kotlinOutputTransform = null,
            operationManifestFile = null,
        )
    val output =
        if (settings.generateDataBuilders) {
            operations.plus(
                ApolloCompiler.buildDataBuilders(
                    codegenSchema = codegenSchema,
                    usedCoordinates = irOperations.usedCoordinates,
                    codegenOptions = codegenOptions,
                    layout = null,
                    upstreamCodegenMetadata = listOf(operations.codegenMetadata),
                ),
            )
        } else {
            operations
        }
    output.writeTo(
        directory = outputDir.toFile(),
        deleteDirectoryFirst = true,
        codegenSymbolsFile = null,
    )
}
