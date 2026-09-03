package com.softistx.oauth.controllers

import com.softistx.graphix.koin.GraphixResolver
import com.softistx.graphix.schema.*
import com.softistx.oauth.graphql.types.AuditMetadata
import com.softistx.oauth.graphql.types.CreatePermissionInput
import com.softistx.oauth.graphql.types.EventType
import com.softistx.oauth.graphql.types.PermissionEvent
import com.softistx.oauth.models.Permission
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asFlow
import org.koin.core.annotation.Singleton

@Singleton
class PermissionController : GraphixResolver {
    private val subject = MutableSharedFlow<PermissionEvent>()

    @QueryMapping
    suspend fun permissions(): List<Permission> =
        listOf(
            Permission("1", "read", "Read permission"),
            Permission("2", "write", "Write permission"),
            Permission("3", "delete", "Delete permission"),
        )

    @BatchMapping
    suspend fun metadata(permissions: List<Permission>): Map<Permission, AuditMetadata> =
        permissions.associateWith { it.metadata.copy(createdBy = "system", lastModifiedBy = "system") }

    @MutationMapping
    suspend fun createPermission(
        @Argument input: CreatePermissionInput,
    ): Permission =
        Permission("4", input.name, input.description).also {
            subject.emit(
                PermissionEvent(
                    it.id,
                    com.softistx.oauth.graphql.types
                        .Permission(it.id, it.name, it.description, it.metadata),
                    EventType.CREATED,
                ),
            )
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @SubscriptionMapping
    fun permissionChanged() = subject.asFlow()
}
