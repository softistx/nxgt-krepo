package com.softistx.spring

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
 * `docs/spring-configuration.md` documents every `stx.*` key, and nothing it no longer has.
 *
 * The second hand-written file over the same data, and it rots the same way: `ConfigurationMetadataTest`
 * keeps the IDE's metadata honest, and this keeps the page a person reads honest. Both failure modes are
 * silent — a key with no entry is autocompletion quietly missing one and a reader quietly not finding it,
 * and neither gets reported as a bug.
 *
 * Worth stating why this is a spec rather than a review habit: while writing that page I checked it by
 * hand three times and got two different wrong answers, because the checking script — not the page — was
 * wrong. A check nobody runs the same way twice is not a check.
 */
class ConfigurationDocsTest :
    StringSpec({
        val documented = documentedKeys(groups())

        "the reference page was found" {
            // Guards the specs below: a page this cannot locate would pass them both vacuously.
            documented.isNotEmpty() shouldBe true
        }

        "every stx.* key is on the page" {
            withClue("add the key to docs/spring-configuration.md in the change that reads it") {
                (metadataKeys() - documented).sorted() shouldBe emptyList()
            }
        }

        "the page documents nothing the code no longer declares" {
            (documented - metadataKeys()).sorted() shouldBe emptyList()
        }
    })

private const val PAGE = "docs/spring-configuration.md"

private const val RESOURCE = "/META-INF/additional-spring-configuration-metadata.json"

/** Every key the hand-written metadata declares. */
private fun metadataKeys(): Set<String> = metadataNames("properties")

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

/**
 * Every key the page spells **for this module**, read off its tables: a `### `stx.group`` heading,
 * then one row per key.
 *
 * Parsed rather than listed, for the same reason `ConfigurationMetadataTest` scans for
 * `@ConfigurationProperties` classes rather than naming them — a list is the thing that goes stale, and
 * a staleness check that goes stale is worse than none.
 *
 * [owned] is what keeps this honest across modules. One page documents every `stx.*` key an
 * application can set, and `stx-graphix-spring` declares some of them — so a spec here that read the
 * whole page would report that module's keys as undocumented, which is how this one was red on
 * `develop` from the moment the Graphix section was added. Each module checks the groups it declares,
 * and a group nobody declares is on nobody's page.
 */
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

/** The groups this module's metadata declares — the half of the page it is answerable for. */
private fun groups(): Set<String> = metadataNames("groups")

private val HEADING = Regex("""### `(stx[a-z0-9.]*)`""")

private val ROW = Regex("""^\| `([a-z][a-z0-9-]*)` \|""")

/**
 * The checkout this spec is running inside, found by walking up to `project.yaml`.
 *
 * The working directory of a test run is not something this module gets to decide, so the root is
 * located rather than assumed — and null when it cannot be, which the first spec turns into a failure
 * naming the page instead of a `NoSuchFileException` naming a path nobody recognises.
 */
private fun repositoryRoot(): Path? =
    generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .firstOrNull { it.resolve("project.yaml").exists() }
