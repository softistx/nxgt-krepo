package com.softistx.openapi.parser

import com.softistx.openapi.UnionDiscriminator
import com.softistx.openapi.UnionSubtype
import com.softistx.openapi.UnionType
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema

/**
 * Turning a `oneOf`/`anyOf` into a [UnionType].
 *
 * `anyOf` is generated identically to `oneOf`, and that is a real narrowing rather than a shorthand:
 * `anyOf` means *at least* one branch validates, so a payload legal against two of them loses the
 * second. Said in the generated KDoc, not only in the README.
 */
internal fun OpenAPI.unionTypeOf(
    schema: Schema<*>,
    name: String,
    where: String,
): UnionType {
    val members = checkNotNull(unionMembersOf(schema)) { "$where is not a generatable union" }
    val discriminator = schema.discriminator?.propertyName
    val tags = if (discriminator == null) emptyMap() else discriminatorTags(schema, members, where)
    val subtypes =
        members.map { member ->
            UnionSubtype(
                name = modelNameOf(member),
                wireValue = tags[member],
                distinguishingKeys = if (discriminator == null) distinguishingKeys(member, members) else emptyList(),
            )
        }
    if (discriminator == null) requireTellableApart(subtypes, name, where)
    return UnionType(
        name = name,
        subtypes = subtypes,
        discriminator =
            discriminator?.let {
                UnionDiscriminator(name = Naming.propertyName(it), wireName = it)
            },
        // Only a discriminated union can be tolerant: with no tag there is nothing to carry.
        fallback = discriminator?.let { fallbackName(name, subtypes.map { subtype -> subtype.name }) },
    )
}

/**
 * The component names this union is over, or null when it is not one this generator can express.
 *
 * A sealed hierarchy needs its members to implement an interface, and a scalar cannot. So a union
 * over anything but object schemas is not a union here — it stays raw JSON, which is honest, rather
 * than a sealed type nothing could ever deserialize into.
 */
internal fun OpenAPI.unionMembersOf(schema: Schema<*>): List<String>? {
    val branches = schema.typedBranches()
    if (branches.size < 2) return null
    return branches.map { branch ->
        val ref = branch.`$ref` ?: return null
        val name = ref.substringAfterLast('/')
        val target = components?.schemas?.get(name) ?: return null
        if (target.properties.isNullOrEmpty() && target.allOf.isNullOrEmpty()) return null
        name
    }
}

/**
 * Which wire value selects which member.
 *
 * `discriminator.mapping` states it explicitly; anything unmapped uses its own component name, which
 * is the implicit mapping OpenAPI defines. A mapping naming a schema outside the `oneOf` is a
 * document bug and fails here rather than producing a tag nothing can decode.
 */
private fun discriminatorTags(
    schema: Schema<*>,
    members: List<String>,
    where: String,
): Map<String, String> {
    val tags = members.associateWith { it }.toMutableMap()
    schema.discriminator?.mapping.orEmpty().forEach { (wireValue, target) ->
        val member = target.substringAfterLast('/')
        if (member !in members) {
            throw OpenApiParseException(
                "$where: discriminator maps '$wireValue' to '$member', which is not one of its members ($members)",
            )
        }
        tags[member] = wireValue
    }
    val collisions = tags.entries.groupBy { it.value }.filterValues { it.size > 1 }
    if (collisions.isNotEmpty()) {
        throw OpenApiParseException(
            collisions.entries.joinToString(
                prefix = "$where: two members share a discriminator value: ",
                separator = "; ",
            ) { (value, entries) -> "'$value' (${entries.joinToString(", ") { it.key }})" },
        )
    }
    return tags
}

/** Wire names this member declares and no sibling does — what tells it apart with no discriminator. */
private fun OpenAPI.distinguishingKeys(
    member: String,
    members: List<String>,
): List<String> {
    val own = propertyNamesOf(member)
    val siblings = members.filter { it != member }.flatMap { propertyNamesOf(it) }.toSet()
    val unique = own - siblings
    // Required-only would be safer, but a member whose every unique property is optional still has
    // to be reachable; ordering by set size at emit time is what keeps subsets decodable.
    return unique.sorted()
}

private fun OpenAPI.propertyNamesOf(
    member: String,
    seen: Set<String> = emptySet(),
): Set<String> {
    if (member in seen) return emptySet()
    val schema = components?.schemas?.get(member) ?: return emptySet()
    val own = schema.properties.orEmpty().keys
    val inherited =
        schema.allOf.orEmpty().flatMap { branch ->
            branch.`$ref`?.let { propertyNamesOf(it.substringAfterLast('/'), seen + member) }
                ?: branch.properties.orEmpty().keys
        }
    return own + inherited
}

private fun requireTellableApart(
    subtypes: List<UnionSubtype>,
    name: String,
    where: String,
) {
    val indistinguishable = subtypes.filter { it.distinguishingKeys.isEmpty() }
    if (indistinguishable.size <= 1) return
    throw OpenApiParseException(
        "$where: $name declares no discriminator, and ${indistinguishable.joinToString(", ") { it.name }} " +
            "have no property that tells them apart. Add a discriminator, or give them distinct properties.",
    )
}

private fun fallbackName(
    union: String,
    memberNames: List<String>,
): String {
    val taken = memberNames.toSet()
    return generateSequence("Unknown$union") { "${it}_" }.first { it !in taken }
}
