package dev.nxgt.openapi.plugin

import org.jetbrains.amper.plugins.Configurable

/** Which client style to generate. */
public enum class ClientKind { Ktorfit, Spring }

/**
 * How operations are split into interfaces.
 *
 * Mirrors `dev.nxgt.openapi.Grouping`: configurable types must be declared in the plugin's
 * own source directory, so an enum from a dependency module cannot be reused here.
 */
public enum class GroupBy { Tag, Path, None }

/**
 * Settings for the `openapi-client` plugin.
 *
 * Every setting has a default, so `openapi-client: enabled` alone is a working configuration
 * for a module with an `openapi.yaml` in its root.
 */
@Configurable
public interface OpenApiClientSettings {
    /** Path to the OpenAPI document, relative to the module root. */
    public val specFile: String get() = "openapi.yaml"

    /** Package for the generated API interfaces; models go in `<packageName>.model`. */
    public val packageName: String get() = "generated.api"

    /** Client style. `Spring` is not implemented yet and fails the build if selected. */
    public val client: ClientKind get() = ClientKind.Ktorfit

    /** How operations are split into interfaces: by OpenAPI tag, by first path segment, or not at all. */
    public val groupBy: GroupBy get() = GroupBy.Tag

    /** Whether to generate `@Serializable` data classes for the spec's component schemas. */
    public val generateModels: Boolean get() = true
}
