package com.softistx.openapi.emit

import com.squareup.kotlinpoet.FileSpec
import java.nio.file.Path

/**
 * Writes an emitter's output, refusing to let one file quietly overwrite another.
 *
 * `FileSpec.writeTo` truncates whatever is already at the path, so two specs sharing a package and
 * name lose one of them without a word. The parser rejects the name collisions it can see, but an
 * emitter that invents a name — a model package clashing with an interface, say — would slip past
 * it, and this is the point where that becomes visible.
 */
public fun List<FileSpec>.writeAllTo(outputDir: Path) {
    val duplicates =
        groupBy { "${it.packageName}.${it.name}" }
            .filterValues { it.size > 1 }
            .keys
    if (duplicates.isNotEmpty()) {
        throw EmitException(
            "refusing to generate: ${duplicates.sorted().joinToString(", ")} " +
                "would each be written more than once, and the last would win",
        )
    }
    forEach { it.writeTo(outputDir) }
}
