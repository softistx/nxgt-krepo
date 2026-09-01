package com.softistx.dgs.plugin

import com.netflix.graphql.dgs.codegen.CodeGen
import com.netflix.graphql.dgs.codegen.CodeGenConfig
import com.netflix.graphql.dgs.codegen.Language
import java.io.File
import java.nio.file.Path

internal fun runDgsCodeGen(
    settings: DgsCodegenSettings,
    schemaFiles: Set<File>,
    outputDir: Path,
) {
    CodeGen(
        CodeGenConfig(
            schemaFiles = schemaFiles,
            outputDir = outputDir,
            writeToFiles = true,
            packageName = settings.packageName,
            language = Language.valueOf(settings.language.toDgsName()),
            generateClientApi = settings.generateClient,
            generateDataTypes = settings.generateDataTypes,
            generateInterfaces = settings.generateInterfaces,
            generateCustomAnnotations = settings.generateCustomAnnotations,
            typeMapping = settings.typeMapping,
            includeImports = settings.includeImports,
        ),
    ).generate()
}
