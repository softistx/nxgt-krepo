package com.strange.graphix.http

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Apollo's embeddable build. `_latest` rather than a pinned version — what Apollo documents. */
private const val SANDBOX_SCRIPT =
    "https://embeddable-sandbox.cdn.apollographql.com/_latest/embeddable-sandbox.umd.production.min.js"

/**
 * An Apollo Sandbox page, for whichever integration wants to serve one. A complete HTML document,
 * not a fragment: nothing here renders it into a layout.
 *
 * [endpoint] is the GraphQL URL the sandbox opens with. **Left empty it is resolved in the
 * browser**, from `window.location.origin` and [graphqlPath] — which is the only way that survives a
 * reverse proxy, https, or a container publishing a different port than the one the server bound.
 * Set it to pin an absolute URL instead.
 *
 * The page needs the schema, so it needs introspection: an engine built with `introspection(false)`
 * serves this page fine and the sandbox's schema panel stays empty.
 */
fun apolloSandboxPage(
    graphqlPath: String,
    endpoint: String = "",
    title: String = "Apollo Sandbox",
): String =
    """
    <!doctype html>
    <html lang="en">
      <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>${title.escapeHtml()}</title>
      </head>
      <body style="margin:0">
        <div id="sandbox" style="position:absolute;top:0;right:0;bottom:0;left:0"></div>
        <script src="$SANDBOX_SCRIPT"></script>
        <script>
          const configured = ${endpoint.jsString()};
          new window.EmbeddedSandbox({
            target: "#sandbox",
            initialEndpoint: configured || new URL(${graphqlPath.jsString()}, window.location.origin).href,
            hideCookieToggle: true,
          });
        </script>
      </body>
    </html>
    """.trimIndent()

/**
 * The value as a JavaScript string literal. A JSON string is one, and this is how the module
 * already escapes strings — so a path holding a quote cannot break out of the script.
 */
private fun String.jsString(): String = Json.encodeToString(String.serializer(), this).replace("</", "<\\/")

private fun String.escapeHtml(): String = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
