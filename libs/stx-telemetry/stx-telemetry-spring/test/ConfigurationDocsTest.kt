package com.strange.telemetry.spring

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * `docs/spring-configuration.md` documents every `stx.telemetry` key, and nothing this module no
 * longer has.
 *
 * The page is one page for every `stx.*` key an application can set, but its sections belong to
 * different modules — so **each module checks its own**. `stx-spring-boot`, `stx-graphix-spring` and
 * `stx-workflow-spring` have the twins of this spec, scoped the same way, and a module that arrives
 * without one is a section of the page nobody is checking.
 *
 * The failure mode is silent — a key with no entry is autocompletion quietly missing one and a
 * reader quietly not finding it.
 */
class ConfigurationDocsTest :
    StringSpec({
        val documented = documentedKeys(metadataNames("groups"))

        "the reference page was found" {
            // Guards the specs below: a page this cannot locate would pass them both vacuously.
            documented.isNotEmpty() shouldBe true
        }

        "every stx.telemetry key is on the page" {
            withClue("add the key to docs/spring-configuration.md in the change that reads it") {
                (metadataNames("properties") - documented).sorted() shouldBe emptyList()
            }
        }

        "the page documents nothing this module no longer declares" {
            (documented - metadataNames("properties")).sorted() shouldBe emptyList()
        }
    })

private const val PAGE = "docs/spring-configuration.md"

private const val RESOURCE = "/META-INF/additional-spring-configuration-metadata.json"

/** Every key the page spells under a group this module declares. */
private fun documentedKeys(owned: Set<String>): Set<String> {
    val page = repositoryRoot()?.resolve(PAGE)?.takeIf { it.exists() } ?: return emptySet()

    var group: String? = null
    return buildSet {
        page.readText().lineSequence().forEach { line ->
            HEADING.matchEntire(line.trim())?.let { group = it.groupValues[1] }
                ?: group?.takeIf { it in owned }?.let { prefix ->
                    ROW.find(line)?.let { add("$prefix.${it.groupValues[1]}") }
                }
        }
    }
}

private fun metadataNames(section: String): Set<String> {
    val stream =
        checkNotNull(ConfigurationDocsTest::class.java.getResourceAsStream(RESOURCE)) {
            "$RESOURCE is not on the classpath"
        }
    return Json
        .parseToJsonElement(stream.use { it.readBytes() }.decodeToString())
        .jsonObject[section]!!
        .jsonArray
        .map { it.jsonObject["name"]!!.jsonPrimitive.content }
        .toSet()
}

private val HEADING = Regex("""### `(stx[a-z0-9.]*)`""")

private val ROW = Regex("""^\| `([a-z][a-z0-9-]*)` \|""")

/** The checkout this spec is running inside, found by walking up to `project.yaml`. */
private fun repositoryRoot(): Path? =
    generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .firstOrNull { it.resolve("project.yaml").exists() }
