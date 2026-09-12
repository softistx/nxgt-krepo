package com.softistx.graphix.scalar

import graphql.schema.GraphQLScalarType
import java.util.Locale

/**
 * A BCP 47 language tag — `fr`, `fr-CA`, `zh-Hant-TW`. The same string an `Accept-Language`
 * header carries, which is where the operation's own locale comes from.
 *
 * `Locale.forLanguageTag` answers `und` for anything it cannot read rather than throwing, so the
 * round trip is the validation: a tag that does not survive it was not a tag.
 */
internal val LocaleScalar: GraphQLScalarType =
    scalarType(
        name = "Locale",
        description = "A BCP 47 language tag: fr-CA.",
        specifiedBy = "https://www.rfc-editor.org/rfc/rfc5646",
        coercing =
            StringCoercing("Locale", Locale::class, { it.toLanguageTag() }, { tag ->
                Locale.forLanguageTag(tag).also { if (it.toLanguageTag() == "und") refuse() }
            }),
    )
