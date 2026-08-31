package com.strange.apollo.plugin

import org.jetbrains.amper.plugins.Configurable
import java.nio.file.Path

/**
 * Settings for the `apollo` plugin.
 *
 * Wraps Apollo Kotlin `apollo-compiler`. The Gradle plugin cannot run here; this is the same
 * compiler it calls. Use it for a Compose / Android / KMP client, and on a server to generate
 * typed operation documents for HTTP tests — `OPERATION_DOCUMENT` is the string
 * `HttpGraphQlTester` / Graphix POST.
 *
 * ```yaml
 * plugins:
 *   apollo:
 *     enabled: true
 *     packageName: com.example.apollo
 *     generateDataBuilders: true
 *     mapScalar:
 *       DateTime: kotlin.time.Instant
 * ```
 */
@Configurable
public interface ApolloSettings {
    /**
     * Schema files or directories, relative to the module root.
     *
     * Directories are walked for `.graphqls`, `.gqls` and `.json`. Empty uses
     * `resources/graphql/` under the module. Operation `.graphql` files in that tree are not
     * schemas — they belong in [srcDir].
     */
    public val schemaPaths: List<Path> get() = emptyList()

    /**
     * Operation and fragment documents (`.graphql`), files or directories.
     *
     * Empty uses `resources/graphql/documents/` under the module.
     */
    public val srcDir: List<Path> get() = emptyList()

    /** Package for generated operations. Types and fragments sit in subpackages. */
    public val packageName: String get() = "generated.apollo"

    /**
     * GraphQL scalar → Kotlin FQN. The adapter is looked up at runtime in
     * `CustomScalarAdapters` unless you also set [mapScalarAdapters].
     */
    public val mapScalar: Map<String, String> get() = emptyMap()

    /**
     * GraphQL scalar → adapter expression (`com.example.InstantAdapter` or
     * `com.example.InstantAdapter()`), compiled into the models.
     */
    public val mapScalarAdapters: Map<String, String> get() = emptyMap()

    /**
     * Test data builders for the operation models. On for server-side HTTP tests that
     * construct a response; off for a production client.
     */
    public val generateDataBuilders: Boolean get() = false

    /**
     * Concrete fragment classes. Needed to build a fragment outside an operation;
     * most clients read fragment data off the operation model.
     */
    public val generateFragmentImplementations: Boolean get() = false

    /**
     * Operation variables as `Optional`. Off matches `nxgt-graphql`: a required-looking
     * Kotlin parameter instead of `Optional.present(...)`.
     */
    public val generateOptionalOperationVariables: Boolean get() = true

    /** Suffix generated operation class names with Query/Mutation/Subscription. */
    public val useSemanticNaming: Boolean get() = true
}
