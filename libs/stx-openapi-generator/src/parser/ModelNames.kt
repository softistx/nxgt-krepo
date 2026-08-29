package com.strange.openapi.parser

import io.swagger.v3.oas.models.OpenAPI

/**
 * The one answer to "what is this component schema called in Kotlin?".
 *
 * A declaration is named once, in `parseModels`, and referred to from four other places — a
 * property's type, a union's member list, a promoted inline schema, a request or response body.
 * Each derived the name independently with [Naming.pascal], which was harmless while the derivation
 * was the only rule. `x-kotlin-name` is the first rule a document can change, and a single missed
 * call site would emit `ModelRef` naming a class nobody generates.
 *
 * So every site asks here instead, the same way every site asks [schemaKindOf] whether a schema
 * becomes a declaration at all.
 */
internal fun OpenAPI.modelNameOf(componentName: String): String =
    components
        ?.schemas
        ?.get(componentName)
        ?.extensions
        .kotlinName("schema $componentName")
        ?: Naming.pascal(componentName)
