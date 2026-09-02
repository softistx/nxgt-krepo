package com.softistx.oauth.controllers

import com.softistx.graphix.schema.Argument
import com.softistx.graphix.schema.MutationMapping
import com.softistx.graphix.schema.QueryMapping
import com.softistx.oauth.models.Permission

class PermissionController {
    @QueryMapping
    suspend fun permissions(): List<Permission> =
        listOf(
            Permission("1", "read", "Read permission"),
            Permission("2", "write", "Write permission"),
            Permission("3", "delete", "Delete permission"),
        )

    @MutationMapping
    suspend fun createPermission(
        @Argument input: Permission,
    ): Permission = Permission("4", input.name, input.description)
}
