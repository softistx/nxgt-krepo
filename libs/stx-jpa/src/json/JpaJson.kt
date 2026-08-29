package com.strange.jpa.json

import com.strange.common.serialization.lenientJson
import kotlinx.serialization.json.Json

/**
 * What a JSON column is written and read with, unless `JpaConfig.json` says otherwise.
 *
 * The same `lenientJson` every other library here re-exports — `redisJson`, `kafkaJson`, `amqpJson` —
 * with one flag changed, and the reason is the difference between a column and a blob.
 *
 * **A stored document is read by SQL, not only by the class that wrote it.** kotlinx omits a property
 * that happens to equal its default, so `where payload->>'currency' = 'EUR'` silently misses every
 * row whose currency was the default — the key is not there to match. A cached Redis value is only
 * ever decoded by the same class and never has that problem; a `jsonb` column is queried, indexed and
 * read by other tools, so it is written in full.
 *
 * `ignoreUnknownKeys` comes from [lenientJson] and matters here for the reason it always does: a
 * document written by an older version of a class has to keep reading after a field is added.
 *
 * It governs only the *opaque* form. An `@Embeddable` marked `@JdbcTypeCode(SqlTypes.JSON)` is
 * written by Hibernate from its own mapping model and never reaches this.
 */
val jpaJson: Json =
    Json(lenientJson) {
        encodeDefaults = true
    }
