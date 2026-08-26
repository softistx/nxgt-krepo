package com.strange.openapi.plugin

import org.jetbrains.amper.plugins.Configurable

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

    /** Models only, in whichever style [OpenApiSettings.models] names. */
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
 * Mirrors `com.strange.openapi.Grouping`: configurable types must be declared in the plugin's
 * own source directory, so an enum from a dependency module cannot be reused here.
 */
public enum class GroupBy { Tag, Path, None }

/**
 * Settings for the `openapi` plugin.
 *
 * Every setting has a default, so `openapi: enabled` alone is a working configuration for a
 * module with an `openapi.yaml` in its root.
 */
@Configurable
public interface OpenApiSettings {
    /** Path to the OpenAPI document, relative to the module root. */
    public val specFile: String get() = "openapi.yaml"

    /** Package for the generated API interfaces; models go in `<packageName>.model`. */
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
