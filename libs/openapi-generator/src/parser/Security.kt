package com.strange.openapi.parser

import com.strange.openapi.SecurityKind
import com.strange.openapi.SecurityRequirement
import com.strange.openapi.SecurityScheme
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation as SwaggerOperation
import io.swagger.v3.oas.models.security.SecurityScheme as SwaggerSecurityScheme

/** Every scheme `components.securitySchemes` declares, in the document's own order. */
internal fun OpenAPI.parseSecuritySchemes(): List<SecurityScheme> =
    components
        ?.securitySchemes
        .orEmpty()
        .map { (name, scheme) ->
            SecurityScheme(
                name = name,
                propertyName = Naming.camel(name),
                kind = kindOf(scheme),
                parameterName = scheme.name.takeIf { scheme.type == SwaggerSecurityScheme.Type.APIKEY },
                description = scheme.description,
            )
        }

/**
 * What an operation actually requires, resolved against the document root.
 *
 * OpenAPI's rule is override, not merge: an operation that declares `security` replaces the root's
 * list outright, and `security: []` replaces it with nothing. Both of those arrive here as an empty
 * list, so no consumer has to know which of the two the document wrote — `examples/demo-api/openapi.yaml`
 * uses `security: []` on its sign-in and sign-up operations, and what a caller needs to know is only
 * that they take no credential.
 *
 * Resolving here rather than in an emitter is what keeps the rule in one place; an emitter that
 * re-derived it would be a second chance to get the override backwards.
 */
internal fun OpenAPI.parseSecurity(
    operation: SwaggerOperation,
    where: String,
): List<SecurityRequirement> {
    val declared = operation.security ?: security ?: return emptyList()
    val known = components?.securitySchemes.orEmpty().keys
    return declared.flatMap { requirement ->
        requirement.map { (name, scopes) ->
            if (name !in known) {
                throw OpenApiParseException(
                    "$where requires security scheme '$name', which components.securitySchemes does not " +
                        "declare" + known.suggestion(),
                )
            }
            SecurityRequirement(name, scopes.orEmpty())
        }
    }
}

private fun Set<String>.suggestion(): String = if (isEmpty()) "" else " (it declares ${joinToString(", ") { "'$it'" }})"

private fun kindOf(scheme: SwaggerSecurityScheme): SecurityKind =
    when (scheme.type) {
        SwaggerSecurityScheme.Type.HTTP -> {
            when (scheme.scheme?.lowercase()) {
                "bearer" -> SecurityKind.HttpBearer
                "basic" -> SecurityKind.HttpBasic
                else -> SecurityKind.Unsupported
            }
        }

        SwaggerSecurityScheme.Type.APIKEY -> {
            when (scheme.`in`) {
                SwaggerSecurityScheme.In.HEADER -> SecurityKind.ApiKeyHeader
                SwaggerSecurityScheme.In.QUERY -> SecurityKind.ApiKeyQuery
                SwaggerSecurityScheme.In.COOKIE -> SecurityKind.ApiKeyCookie
                else -> SecurityKind.Unsupported
            }
        }

        // A flow is not something this generator can run, but what a flow produces is a token, and
        // a token goes in the same header a bearer scheme uses.
        SwaggerSecurityScheme.Type.OAUTH2, SwaggerSecurityScheme.Type.OPENIDCONNECT -> {
            SecurityKind.OAuthToken
        }

        else -> {
            SecurityKind.Unsupported
        }
    }
