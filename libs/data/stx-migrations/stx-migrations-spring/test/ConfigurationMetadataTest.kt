package com.softistx.migrations.spring

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

class ConfigurationMetadataTest :
    StringSpec({
        val metadata = readMetadata()
        val groups = metadata.names("groups")
        val properties = metadata.names("properties")
        val declaring = propertyClasses()

        "there is something to check" {
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

private fun JsonObject.names(section: String): List<String> =
    this[section]?.jsonArray.orEmpty().map { it.jsonObject["name"]!!.jsonPrimitive.content }

private val Class<*>.prefix: String
    get() = checkNotNull(getAnnotation(ConfigurationProperties::class.java)) { "$name is not @ConfigurationProperties" }.prefix

private fun Class<*>.keys(): List<String> =
    kotlin.primaryConstructor
        ?.parameters
        .orEmpty()
        .mapNotNull { it.name }
        .map { "$prefix.${it.kebab()}" }

private fun String.kebab(): String = replace(Regex("([a-z0-9])([A-Z])"), "$1-$2").lowercase()

private fun propertyClasses(): List<Class<*>> =
    ClassPathScanningCandidateComponentProvider(false)
        .apply { addIncludeFilter(AnnotationTypeFilter(ConfigurationProperties::class.java)) }
        .findCandidateComponents("com.softistx.migrations.spring")
        .map { Class.forName(checkNotNull(it.beanClassName)) }
