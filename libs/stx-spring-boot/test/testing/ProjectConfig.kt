package io.kotest.provided

import com.softistx.spring.testing.SpringProjectConfig

/**
 * The one line an application writes, written here too — because a spec of `SpringProjectConfig` that
 * did not go through Kotest's own lookup would be specifying something other than what applications
 * do. Kotest finds this by name, in this package, and nowhere else.
 */
object ProjectConfig : SpringProjectConfig()
