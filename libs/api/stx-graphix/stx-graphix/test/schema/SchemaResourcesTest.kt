package com.softistx.graphix.schema

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldContainExactly
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.outputStream

class SchemaResourcesTest :
    FeatureSpec({
        feature("a schema directory the class loader finds inside a jar") {
            // The shape of a Spring Boot executable jar, which is what `./kotlin package -f
            // executable-jar` writes: `java.class.path` names the outer jar only, and the schema sits
            // under `BOOT-INF/classes/`, reachable through the class loader and through nothing else.
            // A jar the class loader holds and `java.class.path` does not name is the same situation
            // without the loader — measured: examples/graphix-shop's jar started with no schema at all.
            scenario("its documents are loaded, relative to the location") {
                val jar = tempdir().toPath().resolve("schema.jar")
                jar.writeJar(
                    directories = listOf("graphql/", "graphql/nested/"),
                    files =
                        mapOf(
                            "graphql/schema.graphqls" to "type Query { hello: String! }",
                            "graphql/nested/book.gqls" to "type Book { title: String! }",
                            "graphql/notes.txt" to "not a schema",
                        ),
                )

                val files =
                    URLClassLoader(arrayOf(jar.toUri().toURL()), null).use { loader ->
                        listOf("classpath:graphql/").loadSchemaFiles(classLoader = loader)
                    }

                files.map { it.path } shouldContainExactly listOf("nested/book.gqls", "schema.graphqls")
                files.map { it.source } shouldContainExactly
                    listOf("type Book { title: String! }", "type Query { hello: String! }")
            }
        }
    })

/**
 * Writes a jar of [directories] and [files]. The directory entries matter: `getResources("graphql")`
 * finds a directory inside a jar only through its entry, and an executable jar has them.
 */
private fun Path.writeJar(
    directories: List<String>,
    files: Map<String, String>,
) {
    JarOutputStream(outputStream()).use { out ->
        directories.forEach { out.putNextEntry(JarEntry(it)).also { out.closeEntry() } }
        files.forEach { (name, text) ->
            out.putNextEntry(JarEntry(name))
            out.write(text.toByteArray())
            out.closeEntry()
        }
    }
}
