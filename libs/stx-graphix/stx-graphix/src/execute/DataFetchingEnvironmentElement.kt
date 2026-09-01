package com.softistx.graphix.execute

import graphql.schema.DataFetchingEnvironment
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Carries this field's DFE so [com.softistx.graphix.Loader.load] can reach the per-operation DataLoader. */
internal class DataFetchingEnvironmentElement(
    val environment: DataFetchingEnvironment,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<DataFetchingEnvironmentElement>
}
