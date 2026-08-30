package com.strange.graphix.schema

import com.strange.graphix.GraphixException
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/** One `.graphqls` / `.gqls` file, path relative to the scan root for error messages. */
internal data class SchemaFile(
    val path: String,
    val source: String,
)

internal val DefaultSchemaLocations = listOf("classpath:graphql/")
internal val DefaultSchemaExtensions = listOf(".graphqls", ".gqls")

/**
 * Loads GraphQL schema documents from [locations], recursively. Same idea as Spring GraphQL's
 * `classpath:graphql/` tree — several files merge, `extend type` included.
 *
 * [locations] are `classpath:` / `classpath*:` directories (jars included) or `file:` paths.
 */
internal fun loadSchemaFiles(
    locations: List<String>,
    extensions: List<String> = DefaultSchemaExtensions,
    classLoader: ClassLoader = Thread.currentThread().contextClassLoader,
): List<SchemaFile> {
    if (locations.isEmpty()) return emptyList()
    val ext = extensions.map { if (it.startsWith(".")) it else ".$it" }
    return locations
        .flatMap { location -> scan(location, ext, classLoader) }
        .distinctBy { it.path }
        .sortedBy { it.path }
}

private fun scan(
    location: String,
    extensions: List<String>,
    classLoader: ClassLoader,
): List<SchemaFile> {
    val (kind, base) = parseLocation(location)
    return when (kind) {
        LocationKind.File -> {
            val dir = Path.of(base)
            if (!dir.isDirectory()) return emptyList()
            walkDirectory(dir, extensions)
        }

        LocationKind.Classpath -> {
            classpathEntries().flatMap { root ->
                if (root.isDirectory()) {
                    val dir = root.resolve(base)
                    if (dir.isDirectory()) walkDirectory(dir, extensions) else emptyList()
                } else {
                    walkJar(root, base, extensions)
                }
            } +
                classLoader.getResources(base).toList().flatMap { url ->
                    when (url.protocol) {
                        "file" -> {
                            val dir = Path.of(url.toURI())
                            if (dir.isDirectory()) walkDirectory(dir, extensions) else emptyList()
                        }

                        else -> {
                            emptyList()
                        }
                    }
                }
        }
    }
}

private enum class LocationKind { Classpath, File }

private fun parseLocation(location: String): Pair<LocationKind, String> {
    val trimmed = location.trim()
    val (kind, rest) =
        when {
            trimmed.startsWith("classpath*:") -> LocationKind.Classpath to trimmed.removePrefix("classpath*:")
            trimmed.startsWith("classpath:") -> LocationKind.Classpath to trimmed.removePrefix("classpath:")
            trimmed.startsWith("file:") -> LocationKind.File to trimmed.removePrefix("file:")
            else -> LocationKind.Classpath to trimmed
        }
    val raw = rest.trim()
    val base =
        if (kind == LocationKind.File) {
            raw.removeSuffix("/")
        } else {
            raw
                .removePrefix("/")
                .removeSuffix("/**/*.graphqls")
                .removeSuffix("/**/*.gqls")
                .removeSuffix("/**/")
                .removeSuffix("/**")
                .removeSuffix("/")
        }
    if (base.isEmpty()) {
        throw GraphixException("a GraphQL schema location needs a directory: '$location'")
    }
    return kind to base
}

private fun walkDirectory(
    dir: Path,
    extensions: List<String>,
): List<SchemaFile> =
    Files.walk(dir).use { stream ->
        stream
            .filter { it.isRegularFile() && extensions.any { ext -> it.name.endsWith(ext, ignoreCase = true) } }
            .map { SchemaFile(dir.relativize(it).toString().replace('\\', '/'), Files.readString(it)) }
            .toList()
    }

private fun walkJar(
    jar: Path,
    base: String,
    extensions: List<String>,
): List<SchemaFile> {
    if (!jar.name.endsWith(".jar", ignoreCase = true)) return emptyList()
    val prefix = "$base/"
    return JarFile(jar.toFile()).use { file ->
        file
            .entries()
            .asSequence()
            .filter { !it.isDirectory && it.name.startsWith(prefix) && extensions.any { ext -> it.name.endsWith(ext, ignoreCase = true) } }
            .map { SchemaFile(it.name.removePrefix(prefix), file.getInputStream(it).reader().readText()) }
            .toList()
    }
}

private fun classpathEntries(): List<Path> =
    System
        .getProperty("java.class.path")
        .split(File.pathSeparator)
        .map { Path.of(it) }
        .filter { Files.exists(it) }
