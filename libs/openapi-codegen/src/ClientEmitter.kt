package dev.nxgt.openapi

import com.squareup.kotlinpoet.FileSpec

/** Turns a [ClientModel] into Kotlin source files. One implementation per client style. */
public interface ClientEmitter {
    public fun emit(model: ClientModel, options: EmitOptions): List<FileSpec>
}

public data class EmitOptions(
    /** Package for the generated API interfaces. */
    val packageName: String,
    /** Whether to emit `@Serializable` data classes for the spec's component schemas. */
    val generateModels: Boolean = true,
) {
    /** Models live in a sub-package so interface and model names cannot collide. */
    val modelPackage: String get() = "$packageName.model"
}
