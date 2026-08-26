package com.strange.openapi.parser

import io.swagger.v3.oas.models.media.Schema

/**
 * Whether this schema becomes a declaration of its own in the model package.
 *
 * The one definition of that question. [typeOf] uses it to decide whether a `$ref` names a class or
 * resolves through to an underlying type, and `parseModels` uses it to decide what to emit — and if
 * the two ever disagree, a `$ref` starts naming a class nobody generates.
 */
internal fun Schema<*>.isModelled(): Boolean = !properties.isNullOrEmpty() || hasGeneratableEnum()
