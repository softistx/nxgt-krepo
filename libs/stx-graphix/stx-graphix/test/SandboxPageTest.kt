package com.strange.graphix

import com.strange.graphix.http.apolloSandboxPage
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

/** The Apollo Sandbox page both HTTP integrations serve. */
class SandboxPageTest :
    FeatureSpec({
        feature("the page") {
            scenario("is a whole HTML document that loads Apollo's embeddable build") {
                val page = apolloSandboxPage("/graphql")

                page shouldContain "<!doctype html>"
                page shouldContain "<title>Apollo Sandbox</title>"
                page shouldContain """<div id="sandbox""""
                page shouldContain "embeddable-sandbox.cdn.apollographql.com"
                page shouldContain "new window.EmbeddedSandbox"
                page shouldContain "hideCookieToggle: true"
            }

            scenario("resolves the endpoint in the browser when none is configured") {
                val page = apolloSandboxPage("/graphql")

                page shouldContain """const configured = "";"""
                page shouldContain """new URL("/graphql", window.location.origin).href"""
            }

            scenario("uses a configured endpoint verbatim instead") {
                val page = apolloSandboxPage("/graphql", endpoint = "https://api.example.test/graphql")

                page shouldContain """const configured = "https://api.example.test/graphql";"""
            }

            scenario("a title is escaped rather than reopening the document") {
                val page = apolloSandboxPage("/graphql", title = "<script>x</script>")

                page shouldContain "&lt;script&gt;"
                page shouldNotContain "<title><script>"
            }

            scenario("a path holding a quote cannot break out of the script") {
                val page = apolloSandboxPage("""/gr"aph'ql""")

                page shouldContain """new URL("/gr\"aph'ql", window.location.origin)"""
            }

            scenario("a closing script tag in a value cannot end the script early") {
                val page = apolloSandboxPage("/graphql", endpoint = "</script><b>")

                page shouldNotContain "</script><b>"
                page shouldContain """<\/script>"""
            }
        }
    })
