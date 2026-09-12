package com.softistx.jpa.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table

/*
 * The two association shapes nothing exercised, plus the write behaviour every application relies on
 * and no spec here pinned: cascade and orphan removal.
 *
 * `Shop.kt` covers `@ManyToOne` and `@OneToMany`. These are the rest.
 */

@Entity
@Table(name = "accounts")
class Account(
    @Id var id: Long = 0,
    var email: String = "",
    @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "profile_id")
    var profile: Profile? = null,
)

@Entity
@Table(name = "profiles")
class Profile(
    @Id var id: Long = 0,
    var displayName: String = "",
)

@Entity
@Table(name = "students")
class Student(
    @Id var id: Long = 0,
    var name: String = "",
    @ManyToMany
    @JoinTable(
        name = "student_courses",
        joinColumns = [JoinColumn(name = "student_id")],
        inverseJoinColumns = [JoinColumn(name = "course_id")],
    )
    var courses: MutableSet<Course> = mutableSetOf(),
)

@Entity
@Table(name = "courses")
class Course(
    @Id var id: Long = 0,
    var title: String = "",
)

/** A parent that owns its children outright — the shape `cascade` and `orphanRemoval` exist for. */
@Entity
@Table(name = "hampers")
class Hamper(
    @Id var id: Long = 0,
    @OneToMany(mappedBy = "hamper", cascade = [CascadeType.ALL], orphanRemoval = true)
    var items: MutableList<HamperItem> = mutableListOf(),
)

@Entity
@Table(name = "hamper_items")
class HamperItem(
    @Id var id: Long = 0,
    var sku: String = "",
    @ManyToOne(fetch = FetchType.LAZY) var hamper: Hamper? = null,
)
