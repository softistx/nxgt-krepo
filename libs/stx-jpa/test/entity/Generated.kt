package com.softistx.jpa.entity

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** `@GeneratedValue` with no strategy — a sequence, on Postgres. */
@Entity
@Table(name = "auto_ids")
class AutoId(
    @Id @GeneratedValue var id: Long = 0,
    var name: String = "",
)

/** A sequence, named. */
@Entity
@Table(name = "sequence_ids")
class SequenceId(
    @Id @GeneratedValue(strategy = GenerationType.SEQUENCE) var id: Long = 0,
    var name: String = "",
)

/** A Postgres `identity` column. */
@Entity
@Table(name = "identity_ids")
class IdentityId(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    var name: String = "",
)

/** What `Jpa.connect` refuses: a Kotlin `Uuid` where the identifier is. */
@OptIn(ExperimentalUuidApi::class)
@Entity
@Table(name = "uuid_ids")
class UuidId(
    @Id var id: Uuid = Uuid.NIL,
    var name: String = "",
)

/** The same key as `java.util.UUID`, which Hibernate generates on its own. */
@Entity
@Table(name = "java_uuid_ids")
class JavaUuidId(
    @Id @GeneratedValue(strategy = GenerationType.UUID) var id: UUID? = null,
    var name: String = "",
)
