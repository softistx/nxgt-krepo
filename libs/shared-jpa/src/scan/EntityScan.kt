package com.strange.jpa.scan

import com.strange.jpa.JpaMappingException
import io.github.classgraph.ClassGraph
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.MappedSuperclass
import kotlin.reflect.KClass

/**
 * Every mapped class in [packages], found by reading the classpath.
 *
 * ```kotlin
 * val jpa = Jpa.scan(config, "com.acme.orders.domain")
 * ```
 *
 * **Naming the classes is still the safer thing to do.** A list breaks the build when a class moves;
 * a scan finds nothing and starts perfectly, and the first query is where you learn about it. That
 * is why this throws rather than returning an empty list, and why `Jpa.connect` — which takes the
 * list — stays the primary way in.
 *
 * `@MappedSuperclass` and `@Embeddable` are collected as well as `@Entity`, though Hibernate does not
 * need them: registering an entity maps its superclass and its embeddables already, which
 * `MappedClassesTest` shows. They are here for the split-package case, where the entity is in one
 * scanned package and the embeddable it uses is in another.
 *
 * The cost is a classpath walk at startup — tens of milliseconds for an application's own packages,
 * and much more if [packages] is broad enough to include a dependency. Scan what you own.
 */
fun scanEntities(packages: List<String>): List<KClass<*>> {
    if (packages.isEmpty()) throw JpaMappingException("scanning needs at least one package name")

    val found =
        scan(packages) { result ->
            listOf(Entity::class, MappedSuperclass::class, Embeddable::class)
                .flatMap { annotation -> result.getClassesWithAnnotation(annotation.java).loadClasses() }
                .distinct()
                .map { it.kotlin }
        }

    if (found.none { it.java.isAnnotationPresent(Entity::class.java) }) {
        throw JpaMappingException(
            "no @Entity class in ${packages.joinToString()} — a scan that finds nothing is a mapping " +
                "that fails on the first query rather than at startup, so it fails here instead",
        )
    }
    return found
}

/** The same, spelled for the common case. */
fun scanEntities(vararg packages: String): List<KClass<*>> = scanEntities(packages.toList())

/**
 * Every `@Converter` in [packages].
 *
 * A programmatic bootstrap finds no converter on its own — `addAnnotatedClass` does not look for one
 * — so an application that maps its own types has to name them. This is the way not to. Unlike
 * [scanEntities] it is content to find none: converters are an addition, not the mapping itself.
 */
@Suppress("UNCHECKED_CAST")
fun scanConverters(packages: List<String>): List<KClass<out AttributeConverter<*, *>>> {
    if (packages.isEmpty()) throw JpaMappingException("scanning needs at least one package name")

    return scan(packages) { result ->
        result
            .getClassesWithAnnotation(Converter::class.java)
            .loadClasses()
            .filter { AttributeConverter::class.java.isAssignableFrom(it) }
            .map { it.kotlin as KClass<out AttributeConverter<*, *>> }
    }
}

/** The same, spelled for the common case. */
fun scanConverters(vararg packages: String): List<KClass<out AttributeConverter<*, *>>> = scanConverters(packages.toList())

private fun <T> scan(
    packages: List<String>,
    read: (io.github.classgraph.ScanResult) -> T,
): T =
    ClassGraph()
        .enableAnnotationInfo()
        .acceptPackages(*packages.toTypedArray())
        .scan()
        .use(read)
