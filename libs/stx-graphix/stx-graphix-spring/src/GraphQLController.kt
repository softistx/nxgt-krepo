package com.strange.graphix.spring

/**
 * A Spring bean whose `@Query` / `@Mutation` / `@Subscription` functions become GraphQL roots.
 *
 * Not a `@Component`: the application (or its scan) still creates the bean. This module collects
 * every bean that carries the annotation when `stx.graphix.enabled` is true.
 *
 * Constructor parameters are ordinary Spring injection — `OrderService`, a repository. That is
 * not [com.strange.graphix.schema.GraphQLContext]. Per-request values go on
 * [com.strange.graphix.Graphix.execute]'s context map.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class GraphQLController
