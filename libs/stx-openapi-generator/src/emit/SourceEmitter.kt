package com.softistx.openapi.emit

import com.softistx.openapi.ApiModel
import com.squareup.kotlinpoet.FileSpec

/**
 * Turns an [com.softistx.openapi.ApiModel] into Kotlin source files. One implementation per output style.
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

/**
 * Where the generated output goes.
 *
 * Nothing is written to [packageName] itself. Everything lands in one of three sub-packages, by
 * what it is rather than by what produced it:
 *
 * ```
 * <packageName>.apis      one interface per group
 * <packageName>.models    one declaration per schema
 * <packageName>.utils     the machinery a client needs and a caller mostly does not
 * ```
 *
 * The split is what stops an interface and a schema colliding on a name — `Tag` the endpoint group
 * and `Tag` the schema are both ordinary things for a document to contain — and it keeps the
 * surface a caller reads apart from the plumbing underneath it. A generated `ApiProxySupport` is
 * not an API.
 *
 * The names are fixed rather than configurable. Nothing about the layout depends on the consuming
 * module, so a setting would only be a second way to arrange the same files.
 */
public data class EmitOptions(
    /** Root of the generated output. No file is written here; each goes in a sub-package below. */
    val packageName: String,
) {
    /** One interface per API group. */
    val apiPackage: String get() = "$packageName.apis"

    /** One declaration per schema — data classes, enums, sealed unions, value classes. */
    val modelPackage: String get() = "$packageName.models"

    /**
     * What a client needs below its interfaces: the operation annotation, the exception hierarchy,
     * and whatever wiring the client style cannot do without.
     */
    val utilPackage: String get() = "$packageName.utils"
}
