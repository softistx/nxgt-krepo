package com.strange.mongo.gridfs

import com.mongodb.reactivestreams.client.gridfs.GridFSDownloadPublisher
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.asPublisher
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.reactivestreams.Publisher
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/** A publisher whose value nobody wants — `drop`, `rename`, `delete`. Awaiting is the point. */
internal suspend fun Publisher<Void>.await() {
    awaitFirstOrNull()
}

/**
 * Every byte of the file, not the first buffer of it.
 *
 * A download publishes one `ByteBuffer` per chunk — 255 KB by default — so taking the first one
 * silently truncates anything bigger to its first chunk, and the caller gets a file that is the
 * right shape and the wrong length. Collecting the flow is the whole fix.
 */
internal suspend fun GridFSDownloadPublisher.readBytes(): ByteArray {
    val bytes = ByteArrayOutputStream()
    asFlow().collect { buffer ->
        val chunk = ByteArray(buffer.remaining())
        buffer.get(chunk)
        bytes.write(chunk)
    }
    return bytes.toByteArray()
}

/** The bytes as something `uploadFromPublisher` will take: one buffer, published once. */
internal fun ByteArray.asPublisher(): Publisher<ByteBuffer> = flowOf(ByteBuffer.wrap(this)).asPublisher()
