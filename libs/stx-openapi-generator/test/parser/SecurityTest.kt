package com.softistx.openapi.parser

import com.softistx.openapi.SecurityKind
import com.softistx.openapi.SecurityRequirement
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private fun spec(body: String) =
    """
openapi: 3.0.1
info: { title: T, version: 1.0.0 }
$body
    """.trimIndent()

private val WITH_SECURITY =
    spec(
        """
security:
  - Bearer: []
paths:
  /me:
    get:
      tags: [users]
      operationId: findMe
      responses: { '200': { description: ok } }
  /sign-in:
    post:
      tags: [users]
      operationId: signIn
      security: []
      responses: { '200': { description: ok } }
  /admin:
    get:
      tags: [users]
      operationId: listAdmins
      security:
        - ApiKey: []
        - Basic: []
      responses: { '200': { description: ok } }
  /reports:
    get:
      tags: [users]
      operationId: listReports
      security:
        - OAuth: [read:reports]
      responses: { '200': { description: ok } }
components:
  securitySchemes:
    Bearer: { type: http, scheme: bearer, bearerFormat: JWT }
    Basic: { type: http, scheme: basic }
    ApiKey: { type: apiKey, in: header, name: X-Api-Key }
    Query: { type: apiKey, in: query, name: token }
    OAuth:
      type: oauth2
      flows:
        clientCredentials: { tokenUrl: https://example.test/token, scopes: { 'read:reports': r } }
        """.trimIndent(),
    )

/**
 * `examples/demo-api/openapi.yaml` declares `security: - Bearer: []` at its root and overrides it with
 * `security: []` on its sign-in operations, and until this the parser read none of it — a generated
 * client that could not authenticate against the API its own document describes.
 *
 * The rule worth a test is OpenAPI's override, which is not a merge.
 */
class SecurityTest :
    FeatureSpec({

        val model = OpenApiParser().parse(WITH_SECURITY)
        val operations =
            model.groups
                .single()
                .operations
                .associateBy { it.name }

        feature("the schemes a document declares") {

            scenario("every scheme is carried, whether or not an operation requires it") {
                model.securitySchemes.map { it.name } shouldContainExactly
                    listOf("Bearer", "Basic", "ApiKey", "Query", "OAuth")
            }

            scenario("an http scheme is read from its scheme name, not its type alone") {
                model.securitySchemes.single { it.name == "Bearer" }.kind shouldBe SecurityKind.HttpBearer
                model.securitySchemes.single { it.name == "Basic" }.kind shouldBe SecurityKind.HttpBasic
            }

            scenario("each scheme carries the Kotlin name of the slot a client offers for it") {
                model.securitySchemes.map { it.propertyName } shouldContainExactly
                    listOf("bearer", "basic", "apiKey", "query", "oAuth")
            }

            scenario("an apiKey carries where it goes and what it is called") {
                val header = model.securitySchemes.single { it.name == "ApiKey" }
                header.kind shouldBe SecurityKind.ApiKeyHeader
                header.parameterName shouldBe "X-Api-Key"
                val query = model.securitySchemes.single { it.name == "Query" }
                query.kind shouldBe SecurityKind.ApiKeyQuery
                query.parameterName shouldBe "token"
            }

            scenario("a flow becomes the token it produces, which goes in the bearer header") {
                model.securitySchemes.single { it.name == "OAuth" }.kind shouldBe SecurityKind.OAuthToken
            }
        }

        feature("what an operation requires") {

            scenario("an operation that declares nothing inherits the document root") {
                operations.getValue("findMe").security shouldContainExactly listOf(SecurityRequirement("Bearer"))
            }

            scenario("an operation that declares its own replaces the root outright") {
                operations.getValue("listAdmins").security shouldContainExactly
                    listOf(SecurityRequirement("ApiKey"), SecurityRequirement("Basic"))
            }

            scenario("security: [] means no credential, not the root's") {
                operations.getValue("signIn").security shouldContainExactly emptyList()
            }

            scenario("scopes are carried with the requirement") {
                operations.getValue("listReports").security shouldContainExactly
                    listOf(SecurityRequirement("OAuth", listOf("read:reports")))
            }
        }

        feature("a requirement the document cannot satisfy") {

            scenario("naming a scheme securitySchemes does not declare fails the parse") {
                val message =
                    shouldThrow<OpenApiParseException> {
                        OpenApiParser().parse(WITH_SECURITY.replace("- Bearer: []\npaths:", "- Bearor: []\npaths:"))
                    }.message
                message.shouldNotBeNull() shouldContain "'Bearor'"
                message shouldContain "'Bearer'"
            }
        }
    })
