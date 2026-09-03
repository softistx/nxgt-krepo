package com.softistx.graphix.schema

import com.softistx.graphix.GraphixException
import com.softistx.graphix.Loader
import com.softistx.graphix.execute.RegisteredLoader
import com.softistx.graphix.execute.loadBatchMapping
import kotlinx.serialization.json.Json
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

internal fun collectDeclaredLoaders(instances: List<Any>): List<RegisteredLoader> {
    val result = mutableListOf<RegisteredLoader>()
    val seen = mutableSetOf<String>()
    instances.distinct().forEach { instance ->
        instance.loaderProperties().forEach { (name, declared) ->
            val loader = declared.named(name)
            if (loader.name.isEmpty()) {
                throw GraphixException("a DataLoader on ${instance::class.qualifiedName} needs a name")
            }
            if (!seen.add(loader.name)) {
                throw GraphixException("duplicate DataLoader '${loader.name}'")
            }
            result += loader.toRegisteredLoader()
        }
    }
    return result
}

/**
 * The `dataLoader { }` properties on this instance, by property name. Read through the getter and
 * not off the declared type: a property's *value* is what registration uses, and a getter that
 * throws is a property this is not interested in rather than a failed schema build.
 */
internal fun Any.loaderProperties(): List<Pair<String, Loader<*, *>>> =
    this::class.memberProperties.mapNotNull { property ->
        val value =
            try {
                @Suppress("UNCHECKED_CAST")
                val typed = property as KProperty1<Any, *>
                typed.getter.isAccessible = true
                typed.get(this)
            } catch (_: Exception) {
                null
            }
        (value as? Loader<*, *>)?.let { property.name to it }
    }

/** Whether this instance declares any `dataLoader { }` — a class may hold only those. */
internal fun Any.declaresLoader(): Boolean = loaderProperties().isNotEmpty()

internal fun Loader<*, *>.toRegisteredLoader(): RegisteredLoader =
    RegisteredLoader(name) { keys, _, environment ->
        val dfe =
            environment
                ?: error("Loader batch needs a DataFetchingEnvironment")

        @Suppress("UNCHECKED_CAST")
        val typed = this as Loader<Any, Any>
        typed.batch(keys.toList(), dfe)
    }

internal fun TypeFieldMeta.toRegisteredLoader(json: Json): RegisteredLoader =
    RegisteredLoader(loaderName) { keys, context, environment ->
        loadBatchMapping(this, keys, context, environment, json)
    }
