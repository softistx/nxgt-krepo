package com.softistx.jpa.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/**
 * An entity with Bean Validation constraints and nothing telling Hibernate to apply them.
 *
 * Applying them is the default, and the absence here is the fixture: if the validator were not on
 * the classpath, every scenario would still write and read exactly as it does now.
 */
@Entity
@Table(name = "validated")
class Validated(
    @Id var id: Long = 0,
    @field:NotNull
    @field:Size(min = 2, max = 10)
    var name: String? = null,
)
