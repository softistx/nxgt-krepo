package com.strange.openapi.emit

import com.squareup.kotlinpoet.FileSpec
import com.strange.openapi.ApiModel

/**
 * Turns an [com.strange.openapi.ApiModel] into Kotlin source files. One implementation per output style.
 *
 * Every implementation emits the spec's component schemas as data classes; what varies is
 * whether it also emits an API surface for them, and in which client's shape.
 */
public interface SourceEmitter {
    public fun emit(
        model: ApiModel,
        options: EmitOptions,
    ): List<FileSpec>
}

public data class EmitOptions(
    /** Package for the generated API interfaces. */
    val packageName: String,
) {
    /** Models live in a sub-package so an interface and a schema cannot collide on a name. */
    val modelPackage: String get() = "$packageName.model"
}
