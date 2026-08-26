package dev.nxgt.openapi.plugin

import org.jetbrains.amper.plugins.Configurable

/** Settings for the `openapi-client` plugin, configurable per module in `module.yaml`. */
@Configurable
public interface OpenApiClientSettings {
    /** Path to the OpenAPI document, relative to the module root. */
    public val specFile: String get() = "openapi.yaml"

    /** Package for the generated API interfaces; models go in `<packageName>.model`. */
    public val packageName: String get() = "generated.api"
}
