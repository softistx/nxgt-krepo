package com.softistx.openapi.emit

import com.softistx.openapi.ApiModel
import com.softistx.openapi.Operation
import com.softistx.openapi.SecurityKind

/**
 * The model with every alternative no client can satisfy taken out of the operations' `security`.
 *
 * An alternative is satisfiable when every scheme it names has a credential slot. One that names an
 * unsupported scheme — `digest`, `negotiate` — is dropped rather than failing the emit, because the
 * document offers the caller another way in: an operation that accepts `[{Digest}, {Bearer}]` is
 * perfectly callable with a bearer token, and refusing the whole document would be refusing an API
 * the client can in fact use. The generated client then never considers the dropped alternative:
 * it is not chosen, and it is not named in the error a client raises when it holds no credential.
 *
 * What is still refused is an operation left with **no** satisfiable alternative: there the client
 * has nothing it could send, and a silent `security` of nothing would read as "open". The anonymous
 * alternative `{}` counts as satisfiable, so `[{Digest}, {}]` is emitted as anonymous-only.
 * The failure names the operation and every scheme in the way.
 */
internal fun ApiModel.withSatisfiableSecurity(): ApiModel {
    val unsupported = securitySchemes.filter { it.kind == SecurityKind.Unsupported }.map { it.name }.toSet()
    if (unsupported.isEmpty()) return this
    val offenders = mutableListOf<String>()
    val kept =
        groups.map { group ->
            group.operations
                .map { operation ->
                    val usable = operation.security.filter { alternative -> alternative.none { it.scheme in unsupported } }
                    if (operation.security.isNotEmpty() && usable.isEmpty()) {
                        val blocking =
                            operation.security
                                .flatten()
                                .map { it.scheme }
                                .filter { it in unsupported }
                                .distinct()
                        offenders += "${group.name}.${operation.name} requires ${blocking.joinToString(", ") { "'$it'" }}"
                    }
                    operation.copy(security = usable)
                }.let { group.copy(operations = it) }
        }
    if (offenders.isNotEmpty()) {
        throw EmitException(
            offenders.joinToString(
                prefix = "no generated client can satisfy these security schemes: ",
                separator = "; ",
            ) + ". Their credential is the answer to a server challenge rather than a value a caller holds, " +
                "and these operations offer no other alternative.",
        )
    }
    return copy(groups = kept)
}

/** The character `@ApiOperation.security` joins the schemes of one alternative with. */
internal const val ALTERNATIVE_SEPARATOR: Char = ','

/**
 * An operation's alternatives as the strings `@ApiOperation(security = [...])` carries: one element
 * per alternative, its scheme names joined by [ALTERNATIVE_SEPARATOR], and `""` for the anonymous
 * alternative `{}`. An annotation cannot hold an array of arrays, hence the encoding.
 */
internal fun Operation.encodedSecurity(): List<String> =
    security.map { alternative ->
        alternative.joinToString(ALTERNATIVE_SEPARATOR.toString()) { requirement ->
            if (ALTERNATIVE_SEPARATOR in requirement.scheme) {
                throw EmitException(
                    "$name requires security scheme '${requirement.scheme}', whose name contains " +
                        "'$ALTERNATIVE_SEPARATOR', the character the generated annotation joins an alternative's " +
                        "schemes with. Rename the scheme.",
                )
            }
            requirement.scheme
        }
    }
