package com.strange.graphix.spring

/**
 * A Spring bean whose `@Query` / `@Mutation` functions become GraphQL roots.
 *
 * Not a `@Component`: the application (or its scan) still creates the bean. This module collects
 * every bean that carries the annotation when `stx.graphix.enabled` is true.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class GraphQLController
