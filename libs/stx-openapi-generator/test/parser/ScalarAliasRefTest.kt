package com.strange.openapi.parser

import com.strange.openapi.ObjectType
import com.strange.openapi.ParamKind
import com.strange.openapi.TypeRef
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

private val SPEC =
    """
openapi: 3.1.0
info: { title: T, version: 1.0.0 }
paths:
  /uploads:
    post:
      operationId: upload
      requestBody:
        content:
          multipart/form-data:
            schema: { ${'$'}ref: '#/components/schemas/UploadRequest' }
      responses:
        '200':
          content:
            application/json:
              schema: { ${'$'}ref: '#/components/schemas/Search' }
components:
  schemas:
    Upload: { type: string, format: binary, description: File to upload }
    FreeForm: { type: object }
    UploadRequest:
      type: object
      properties:
        file: { ${'$'}ref: '#/components/schemas/Upload' }
    Search:
      type: object
      properties:
        filter: { ${'$'}ref: '#/components/schemas/FreeForm' }
    """.trimIndent()

/**
 * A `$ref` does not imply a model class. These are the cases where following one has to land on
 * something other than a `ModelRef`, or the generator invents classes the spec never declared.
 */
class ScalarAliasRefTest :
    FeatureSpec({

        val model = OpenApiParser().parse(SPEC)

        feature("refs that resolve to a scalar") {
            scenario("a ref to a binary scalar becomes a binary part, not a phantom model class") {
                val part =
                    model.groups
                        .single()
                        .operations
                        .single()
                        .parameters
                        .single()
                part.kind shouldBe ParamKind.Part
                part.type shouldBe TypeRef.BinaryRef
            }

            scenario("a ref to a property-less object becomes raw JSON") {
                val search = model.models.filterIsInstance<ObjectType>().first { it.name == "Search" }
                search.fields.single().type shouldBe TypeRef.JsonObjectRef
            }
        }

        feature("which schemas become models") {
            scenario("only object schemas are emitted as models") {
                model.models.map { it.name } shouldContainExactly listOf("Search", "UploadRequest")
            }
        }
    })
