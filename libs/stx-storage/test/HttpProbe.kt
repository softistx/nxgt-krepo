package com.strange.storage

import kotlinx.coroutines.future.await
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * A tiny HTTP client for the presigned-URL specs.
 *
 * A presigned URL is only worth anything if something that has never seen the credential can use
 * it, so these tests use one: no MinIO SDK, no signing, just the URL as a browser would get it.
 */
object HttpProbe {
    private val client: HttpClient = HttpClient.newHttpClient()

    suspend fun get(url: String): Response = send(HttpRequest.newBuilder(URI.create(url)).GET().build())

    suspend fun put(
        url: String,
        body: ByteArray,
    ): Response =
        send(
            HttpRequest
                .newBuilder(URI.create(url))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                .build(),
        )

    suspend fun delete(url: String): Response = send(HttpRequest.newBuilder(URI.create(url)).DELETE().build())

    /**
     * A `multipart/form-data` post, hand-rolled because the JDK client has no multipart body.
     *
     * The field order is the part that matters: S3 reads the policy and signature as they arrive
     * and refuses the upload at the first condition that fails, so the file must come last.
     */
    suspend fun postForm(
        url: String,
        fields: Map<String, String>,
        filename: String,
        file: ByteArray,
        contentType: String = "application/octet-stream",
    ): Response {
        val boundary = "----storage-test-boundary"
        val body =
            buildList<ByteArray> {
                fields.forEach { (name, value) ->
                    add(
                        (
                            "--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n"
                        ).toByteArray(),
                    )
                }
                add(
                    (
                        "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$filename\"\r\n" +
                            "Content-Type: $contentType\r\n\r\n"
                    ).toByteArray(),
                )
                add(file)
                add("\r\n--$boundary--\r\n".toByteArray())
            }.reduce { a, b -> a + b }

        return send(
            HttpRequest
                .newBuilder(URI.create(url))
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build(),
        )
    }

    private suspend fun send(request: HttpRequest): Response {
        val response = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).await()
        return Response(response.statusCode(), response.body(), response.headers().map())
    }

    data class Response(
        val status: Int,
        val body: ByteArray,
        val headers: Map<String, List<String>>,
    ) {
        val text: String get() = body.decodeToString()

        fun header(name: String): String? = headers[name.lowercase()]?.firstOrNull()
    }
}
