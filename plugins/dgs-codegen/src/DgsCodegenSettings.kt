package com.strange.dgs.plugin

import org.jetbrains.amper.plugins.Configurable
import java.nio.file.Path

/**
 * Target language for DGS codegen.
 *
 * Mirrors `com.netflix.graphql.dgs.codegen.Language`: configurable types must be declared in the
 * plugin's own source directory, so that enum cannot be reused here.
 */
public enum class DgsLanguage {
    Kotlin,
    Java,
}

/**
 * Settings for the `dgs-codegen` plugin.
 *
 * Wraps Netflix DGS `graphql-dgs-codegen-core`. Empty [schemaPaths] scans
 * `resources/graphql/` under the module root — the same default Graphix uses for SDL.
 *
 * ```yaml
 * plugins:
 *   dgs-codegen:
 *     enabled: true
 *     packageName: com.example.codegen
 *     typeMapping:
 *       DateTime: kotlin.time.Instant
 * ```
 */
@Configurable
public interface DgsCodegenSettings {
    /**
     * Schema files or directories, relative to the module root.
     *
     * Directories are walked for `.graphqls` and `.gqls`. Empty uses
     * `resources/graphql/` under the module. Operation `.graphql` files are Apollo's.
     */
    public val schemaPaths: List<Path> get() = emptyList()

    /** Base package of generated types; data types land in `<packageName>.types`. */
    public val packageName: String get() = "generated.dgs"

    /** Kotlin (default, this repo) or Java. */
    public val language: DgsLanguage get() = DgsLanguage.Kotlin

    /**
     * DGS type-safe query API (`generateClientApi`). Off by default: Apollo Kotlin owns
     * client documents in this repo. Turn on only for a DGS Java/Kotlin client.
     */
    public val generateClient: Boolean get() = false

    /** Input objects, types, enums. The usual server-side reason to run this plugin. */
    public val generateDataTypes: Boolean get() = true

    /** Generate interface types for GraphQL interfaces. */
    public val generateInterfaces: Boolean get() = false

    /** Honour `@annotation` SDL directives as Kotlin/Java annotations. Needs [includeImports]. */
    public val generateCustomAnnotations: Boolean get() = false

    /**
     * GraphQL type name → existing Kotlin/Java FQN. Those types are not generated; the
     * schema still names them.
     */
    public val typeMapping: Map<String, String> get() = emptyMap()

    /**
     * Annotation short name → package, used when [generateCustomAnnotations] is on.
     * `validation` → `jakarta.validation.constraints` is the usual one.
     */
    public val includeImports: Map<String, String> get() = emptyMap()
}
