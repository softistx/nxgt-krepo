package com.strange.mongo.gridfs

import com.strange.mongo.MongoTestCluster
import com.strange.mongo.withTransaction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.count
import org.bson.Document
import org.bson.types.ObjectId
import java.util.concurrent.atomic.AtomicInteger

private val buckets = AtomicInteger()

/** A bucket in a database of its own, dropped when [block] returns. */
private suspend fun withBucket(block: suspend (GridFsBucket) -> Unit) {
    MongoTestCluster.reactiveClient().use { reactive ->
        val database = reactive.getDatabase("stx-mongo-gridfs-${buckets.incrementAndGet()}")
        try {
            block(GridFsBucket.of(database, "uploads"))
        } finally {
            MongoTestCluster.client().use { it.getDatabase(database.name).drop() }
        }
    }
}

/**
 * One file per scenario, and one of them deliberately larger than a chunk — 255 KB is the default,
 * and a download that reads only the first buffer passes every test written with a small file.
 */
class GridFsBucketTest :
    FeatureSpec({

        val small = "the quick brown fox".toByteArray()
        val large = ByteArray(700_000) { (it % 251).toByte() }

        feature("a round trip").config(enabled = MongoTestCluster.available) {
            scenario("what went in comes back") {
                withBucket { files ->
                    val id = files.upload("notes.txt", small)

                    files.download(id) shouldBe small
                    files.bucketName shouldBe "uploads"
                }
            }

            scenario("a file larger than one chunk comes back whole") {
                withBucket { files ->
                    val id = files.upload("large.bin", large)

                    files.findById(id)?.length shouldBe large.size.toLong()
                    files.download(id)?.size shouldBe large.size
                    files.download(id) shouldBe large
                }
            }

            scenario("metadata is stored with the file") {
                withBucket { files ->
                    val id = files.upload("notes.txt", small, Document("contentType", "text/plain"))

                    files.findById(id)?.metadata?.getString("contentType") shouldBe "text/plain"
                }
            }
        }

        feature("a filename is not a key").config(enabled = MongoTestCluster.available) {
            scenario("uploading twice keeps both, and the name resolves to the newest") {
                withBucket { files ->
                    files.upload("notes.txt", "first".toByteArray())
                    files.upload("notes.txt", "second".toByteArray())

                    files.find().count() shouldBe 2
                    files.download("notes.txt")?.decodeToString() shouldBe "second"
                    files.download("notes.txt", revision = 0)?.decodeToString() shouldBe "first"
                }
            }
        }

        feature("a file that is not there").config(enabled = MongoTestCluster.available) {
            scenario("downloading it is null, not an exception") {
                withBucket { files ->
                    files.download(ObjectId()) shouldBe null
                    files.download("absent.txt") shouldBe null
                    files.findById(ObjectId()) shouldBe null
                }
            }
        }

        feature("removing and renaming").config(enabled = MongoTestCluster.available) {
            scenario("a deleted file is gone; a renamed one is the same file") {
                withBucket { files ->
                    val id = files.upload("notes.txt", small)

                    files.rename(id, "renamed.txt")
                    files.findByFilename("renamed.txt")?.objectId shouldBe id
                    files.download("renamed.txt") shouldBe small

                    files.delete(id)
                    files.download(id) shouldBe null
                }
            }
        }

        feature("an upload inside a transaction").config(enabled = MongoTestCluster.available) {
            scenario("it is rolled back with everything else — once the bucket is warm") {
                withBucket { files ->
                    /* The first upload into a bucket creates its indexes, and an index cannot be
                       created inside a transaction. Warming it outside is the price of that. */
                    files.upload("warm.txt", small)

                    MongoTestCluster.client().use { client ->
                        shouldThrow<IllegalStateException> {
                            client.withTransaction { session ->
                                files.upload("rolled-back.txt", small, session = session)
                                error("no")
                            }
                        }
                    }

                    files.findByFilename("rolled-back.txt") shouldBe null
                    files.findByFilename("warm.txt") shouldNotBe null
                }
            }
        }
    })
