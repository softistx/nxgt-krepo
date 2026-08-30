package com.strange.spring

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AnnotationTypeFilter
import kotlin.reflect.full.primaryConstructor

/**
 * The metadata an IDE completes `stx.*` from is written by hand, so something has to prove it still
 * describes the code.
 *
 * It is hand-written because it cannot be generated here: `spring-boot-configuration-processor` is a
 * *Java* annotation processor, the Kotlin Toolchain has no kapt, and its
 * `settings.java.annotationProcessing` runs javac over Java sources only — so the processor never
 * sees a Kotlin `@ConfigurationProperties` class. Without this spec the file would rot the first
 * time someone renamed a property, and the only symptom would be autocompletion quietly missing an
 * entry, which nobody reports.
 */
class ConfigurationMetadataTest :
    StringSpec({
        val metadata = readMetadata()
        val groups = metadata.names("groups")
        val properties = metadata.names("properties")

        // Scanned once: the classpath scan is the slow part of this spec and its answer cannot
        // change between specs.
        val declaring = propertyClasses()

        "there is something to check" {
            // Guards the two specs below: a scan that finds nothing would pass them both vacuously,
            // which is exactly what a renamed package would cause.
            declaring.isNotEmpty() shouldBe true
        }

        "every @ConfigurationProperties class in this module is documented" {
            declaring.forEach { type ->
                withClue(type.name) {
                    groups shouldContainAll listOf(type.prefix)
                    properties shouldContainAll type.keys()
                }
            }
        }

        "nothing is documented that the code no longer declares" {
            val declared = declaring.flatMap { it.keys() }.toSet()
            (properties - declared) shouldBe emptySet()
        }

        "every entry says what it is, where it comes from, and what it defaults to" {
            metadata["properties"]!!.jsonArray.map { it.jsonObject }.forEach { entry ->
                withClue(entry["name"]!!.jsonPrimitive.content) {
                    entry["type"] shouldNotBe null
                    entry["description"] shouldNotBe null
                    entry["sourceType"] shouldNotBe null
                    entry["defaultValue"] shouldNotBe null
                }
            }
        }

        "every key is under the stx namespace" {
            properties.filterNot { it.startsWith("stx.") } shouldBe emptyList()
        }
    })

private const val RESOURCE = "/META-INF/additional-spring-configuration-metadata.json"

private fun readMetadata(): JsonObject {
    val stream =
        checkNotNull(ConfigurationMetadataTest::class.java.getResourceAsStream(RESOURCE)) {
            "$RESOURCE is not on the classpath"
        }
    return Json.parseToJsonElement(stream.use { it.readBytes() }.decodeToString()).jsonObject
}

/** The `name` of every entry in one top-level section. */
private fun JsonObject.names(section: String): List<String> =
    this[section]?.jsonArray.orEmpty().map { it.jsonObject["name"]!!.jsonPrimitive.content }

private val Class<*>.prefix: String
    get() = checkNotNull(getAnnotation(ConfigurationProperties::class.java)) { "$name is not @ConfigurationProperties" }.prefix

/**
 * The property keys a class declares, spelled the way a `.properties` file spells them.
 *
 * Read off the primary constructor rather than the members, because that is what binds: a computed
 * `val` in the body is not configuration and has no business in the metadata.
 */
private fun Class<*>.keys(): List<String> =
    kotlin.primaryConstructor
        ?.parameters
        .orEmpty()
        .mapNotNull { it.name }
        .map { "$prefix.${it.kebab()}" }

/** `includeDebugMessage` is `include-debug-message` in a properties file. */
private fun String.kebab(): String = replace(Regex("([a-z0-9])([A-Z])"), "$1-$2").lowercase()

/**
 * The module's own `@ConfigurationProperties` classes, found by scanning rather than listed.
 *
 * A list is the thing that goes stale, which is what this spec exists to prevent.
 */
private fun propertyClasses(): List<Class<*>> =
    ClassPathScanningCandidateComponentProvider(false)
        .apply { addIncludeFilter(AnnotationTypeFilter(ConfigurationProperties::class.java)) }
        .findCandidateComponents("com.strange.spring")
        .map { Class.forName(checkNotNull(it.beanClassName)) }
