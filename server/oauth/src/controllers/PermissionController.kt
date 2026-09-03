package com.softistx.oauth.controllers

import com.softistx.graphix.koin.GraphixResolver
import com.softistx.graphix.schema.Argument
import com.softistx.graphix.schema.MutationMapping
import com.softistx.graphix.schema.QueryMapping
import com.softistx.oauth.graphql.types.CreatePermissionInput
import com.softistx.oauth.models.Permission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import org.koin.core.annotation.Singleton

@Singleton
class PermissionController : GraphixResolver {
    @QueryMapping
    fun permissions(): Flow<Permission> =
        listOf(
            Permission("1", "read", "Read permission"),
            Permission("2", "write", "Write permission"),
            Permission("3", "delete", "Delete permission"),
        ).asFlow()

    @MutationMapping
    suspend fun createPermission(
        @Argument input: CreatePermissionInput,
    ): Permission = Permission("4", input.name, input.description)
}
