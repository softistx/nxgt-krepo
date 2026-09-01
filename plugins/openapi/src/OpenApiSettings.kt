package com.softistx.openapi.plugin

import org.jetbrains.amper.plugins.Configurable
import java.nio.file.Path

/**
 * What to generate from the document.
 *
 * The spec's schemas are always generated; this decides whether an API surface joins them, and in
 * which client's shape. Each choice implies what the consuming module needs on its classpath —
 * see the plugin README.
 */
public enum class ClientKind {
    /** `@GET`/`@POST` interfaces for ktorfit-ksp to implement, with kotlinx.serialization models. */
    Ktorfit,

    /** `@HttpExchange` interfaces for Spring's `HttpServiceProxyFactory`, with Jackson models. */
    Spring,

    /** Models only, in whichever style [SpecSettings.models] names. */
    None,
}

/**
 * Which serialization library the generated models target.
 *
 * [Auto] follows the client, which is almost always what you want. Naming one explicitly matters
 * for `client: None`, and for a Spring client whose `WebClient` is configured with
 * kotlinx.serialization codecs rather than Jackson.
 */
public enum class ModelKind {
    /** Follow the client: Jackson for Spring, kotlinx.serialization for Ktorfit and for no client. */
    Auto,

    /** kotlinx.serialization: `@Serializable`, `kotlin.time.Instant`, `JsonObject`. */
    Kotlinx,

    /** Jackson 3: `@JsonProperty` where names differ, `java.time.Instant`, a plain `Map`. */
    Jackson,
}

/**
 * How operations are split into interfaces.
 *
 * Mirrors `com.softistx.openapi.Grouping`: configurable types must be declared in the plugin's
 * own source directory, so an enum from a dependency module cannot be reused here.
 */
public enum class GroupBy { Tag, Path, None }

/**
 * Settings for the `openapi` plugin.
 *
 * One entry in [specs] per document. A module consuming three upstream APIs generates all three
 * here, into three packages, rather than being split across three modules by the build.
 *
 * ```yaml
 * plugins:
 *   openapi:
 *     enabled: true
 *     specs:
 *       - spec: openapi/api-docs.yaml
 *         packageName: com.example.orders.api
 *         client: Spring
 * ```
 */
@Configurable
public interface OpenApiSettings {
    /**
     * The documents to generate from, one entry each.
     *
     * Empty is a failure rather than a no-op: a plugin turned on and generating nothing is the
     * kind of quiet nothing this repo fails on elsewhere.
     */
    public val specs: List<SpecSettings> get() = emptyList()
}

/**
 * One document, and what to make of it.
 *
 * Every generated file lands under [packageName], so two entries must not share one — the writer
 * only detects duplicate names within a single document, and two documents in one package would
 * overwrite each other file for file. [generateClient] refuses that before it writes anything.
 */
@Configurable
public interface SpecSettings {
    /**
     * The OpenAPI document, relative to the module root — `../shared/api.yaml` reaches outside it.
     *
     * Typed as a `Path` rather than a `String` because the frontend resolves it against the module
     * directory and hands the task an absolute path; a `String` would need `${module.rootDir}/`
     * pasted in front of it, and there is no way to interpolate a list element. The consequence is
     * that it takes no default — the schema processor refuses defaults for `Path` — which is right
     * for the one setting that has no sensible guess.
     */
    public val spec: Path

    /** Package for the generated API interfaces; models go in `<packageName>.models`. */
    public val packageName: String get() = "generated.api"

    /** What to generate: a Ktorfit client, a Spring client, or models alone. */
    public val client: ClientKind get() = ClientKind.Ktorfit

    /** How operations are split into interfaces: by OpenAPI tag, by first path segment, or not at all. */
    public val groupBy: GroupBy get() = GroupBy.Tag

    /**
     * Which serialization library the models target. [ModelKind.Auto] follows the client.
     *
     * A Ktorfit client is always kotlinx.serialization; asking for [ModelKind.Jackson] alongside
     * one fails the build rather than quietly generating models it cannot deserialize.
     */
    public val models: ModelKind get() = ModelKind.Auto

    /** Prepended to every generated interface name — `"I"` gives `ICategoriesApi`. */
    public val interfacePrefix: String get() = ""

    /** Appended to every generated interface name — `"Client"` gives `CategoriesClient`. */
    public val interfaceSuffix: String get() = "Api"
}
