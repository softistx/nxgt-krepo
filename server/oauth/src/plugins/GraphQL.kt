package com.softistx.oauth.plugins

import com.softistx.graphix.GraphixBuilder
import com.softistx.oauth.controllers.PermissionController

fun GraphixBuilder.registerGraphQLOperations() {
    val permission = PermissionController()
    resolvers(permission)
}
