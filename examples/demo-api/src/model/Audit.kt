package com.softistx.demo.api.model

import kotlin.time.Clock
import kotlin.time.Instant

/** Stamps the audit block every response carries. A real service would name the caller here. */
public fun audit(now: Instant = Clock.System.now()): AuditMetadata =
    AuditMetadata(createdBy = "demo", createdDate = now, lastModifiedBy = "demo", lastModifiedDate = now)

public fun AuditMetadata.touched(now: Instant = Clock.System.now()): AuditMetadata = copy(lastModifiedBy = "demo", lastModifiedDate = now)
