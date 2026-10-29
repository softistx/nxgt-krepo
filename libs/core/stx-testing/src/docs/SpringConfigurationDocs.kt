package com.softistx.testing.docs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * What a module's hand-written Spring metadata declares, beside what the reference page spells for
 * it — the two halves one module's `ConfigurationDocsTest` compares.
 *
 * One page, `docs/spring-configuration.md`, documents every `stx.*` key an application can set, but
 * its sections belong to different modules, so each module checks only the groups it declares:
 * [documented] is already narrowed to them. A spec that read the whole page would report another
 * module's keys as undocumented.
 *
 * @property declared every key in the `properties` section of the module's metadata.
 * @property groups every group in its `groups` section — the half of the page the module answers for.
 * @property documented every key the page spells under one of those [groups]; empty when the page
 *   cannot be located, which a spec's "the page was found" guard turns into a failure naming the page.
 */
class SpringConfigurationDocs internal constructor(
    val declared: Set<String>,
    val groups: Set<String>,
    val documented: Set<String>,
)

/**
 * Reads the metadata [resourceOwner] can see at `/META-INF/additional-spring-configuration-metadata.json`
 * and the keys [page] spells for its groups.
 *
 * [resourceOwner] is any class of the module under test: the metadata is read from its classpath, so
 * pass one of the module's own. [page] is relative to the repository root, found by walking up from
 * the working directory to `project.yaml` — a test run's working directory is not something the module
 * gets to decide.
 *
 * Parsed rather than listed: a page's `### `stx.group`` heading then one `| `key` |` row per key.
 * A list is the thing that goes stale, and a staleness check that goes stale is worse than none.
 */
fun springConfigurationDocs(
    resourceOwner: Class<*>,
    page: String = "docs/spring-configuration.md",
): SpringConfigurationDocs {
    val metadata = readMetadata(resourceOwner)
    val groups = metadata.names("groups")
    return SpringConfigurationDocs(metadata.names("properties"), groups, documentedKeys(page, groups))
}

private const val RESOURCE = "/META-INF/additional-spring-configuration-metadata.json"

private val HEADING = Regex("""### `(stx[a-z0-9.]*)`""")

private val ROW = Regex("""^\| `([a-z][a-z0-9-]*)` \|""")

private fun readMetadata(owner: Class<*>) =
    Json
        .parseToJsonElement(
            checkNotNull(owner.getResourceAsStream(RESOURCE)) { "$RESOURCE is not on the classpath" }
                .use { it.readBytes() }
                .decodeToString(),
        ).jsonObject

private fun kotlinx.serialization.json.JsonObject.names(section: String): Set<String> =
    getValue(section)
        .jsonArray
        .map {
            it.jsonObject
                .getValue("name")
                .jsonPrimitive.content
        }.toSet()

private fun documentedKeys(
    page: String,
    owned: Set<String>,
): Set<String> {
    val file = repositoryRoot()?.resolve(page)?.takeIf { it.exists() } ?: return emptySet()

    var group: String? = null
    return buildSet {
        file.readText().lineSequence().forEach { line ->
            HEADING.matchEntire(line.trim())?.let { group = it.groupValues[1] }
                ?: group?.takeIf { it in owned }?.let { prefix ->
                    ROW.find(line)?.let { add("$prefix.${it.groupValues[1]}") }
                }
        }
    }
}

private fun repositoryRoot(): Path? =
    generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .firstOrNull { it.resolve("project.yaml").exists() }
