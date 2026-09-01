package com.softistx.graphix.execute

/**
 * Context key for the [kotlinx.coroutines.CoroutineScope] [com.softistx.graphix.Graphix.execute]
 * installs. A resolver does not ask for this — it is how `suspend` fetchers find their scope.
 */
internal object OperationScope
