package com.strange.graphix.fixture

import com.strange.graphix.schema.Argument
import com.strange.graphix.schema.GraphQLName
import com.strange.graphix.schema.GraphQLUnion
import com.strange.graphix.schema.QueryMapping
import com.strange.graphix.schema.SchemaMapping
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Shared properties, so this is a GraphQL `interface`. */
@Serializable
sealed interface Media {
    val id: String
    val title: String
}

@Serializable
data class Film(
    override val id: String,
    override val title: String,
    val minutes: Int,
) : Media

@Serializable
data class Song(
    override val id: String,
    override val title: String,
    val bpm: Int,
) : Media

/** No shared property, so this can only be a GraphQL `union`. */
@Serializable
sealed interface SearchHit

@Serializable
data class BookHit(
    val title: String,
) : SearchHit

@Serializable
data class AuthorHit(
    val name: String,
) : SearchHit

/** Shared property, but forced to a union. */
@GraphQLUnion
@Serializable
sealed interface Payload {
    val id: String
}

@Serializable
data class TextPayload(
    override val id: String,
    val text: String,
) : Payload

@Serializable
data class BlobPayload(
    override val id: String,
    val bytes: Int,
) : Payload

/** A nested sealed level: `Container` is Kotlin structure, not a member type. */
@Serializable
sealed interface Node

@Serializable
sealed interface Container : Node

@Serializable
data class Folder(
    val name: String,
) : Container

@Serializable
data class Leaf(
    val name: String,
) : Node

@Serializable
sealed interface Choice

@Serializable
data class ById(
    val id: String,
) : Choice

class MediaQueries {
    @QueryMapping
    fun media(): List<Media> = listOf(Film("f1", "Dune", 155), Song("s1", "Ocean", 120))

    @QueryMapping
    fun search(): List<SearchHit> = listOf(BookHit("Dune"), AuthorHit("Frank"))

    @QueryMapping
    fun payload(): Payload = TextPayload("t1", "hello")

    @QueryMapping
    fun tree(): List<Node> = listOf(Folder("src"), Leaf("Main.kt"))
}

class MediaFields {
    @SchemaMapping
    fun slug(media: Media): String = media.title.lowercase().replace(' ', '-')
}

class FilmOverrideFields {
    @SchemaMapping(typeName = "Film", field = "slug")
    fun filmSlug(film: Film): String = "film-${film.id}"
}

/** A type the schema never heard of — erasure is what lets it out of a `List<SearchHit>`. */
@Serializable
data class Stray(
    val what: String,
)

class StrayQueries {
    @Suppress("UNCHECKED_CAST")
    @QueryMapping
    fun search(): List<SearchHit> = listOf(Stray("nope")) as List<SearchHit>
}

/** A member with no properties at all: GraphQL has no empty object type. */
@Serializable
sealed interface Empty

@Serializable
data object Nothing : Empty

@Serializable
data class Something(
    val what: String,
) : Empty

class EmptyQueries {
    @QueryMapping
    fun empty(): Empty = Something("x")
}

/** An implementor renaming a field its interface declares. */
@Serializable
sealed interface Titled {
    val title: String
}

@Serializable
data class Renamed(
    @SerialName("heading") override val title: String,
) : Titled

class RenamedQueries {
    @QueryMapping
    fun titled(): Titled = Renamed("Dune")
}

class ChoiceQueries {
    @QueryMapping
    fun pick(
        @Argument choice: Choice,
    ): String = choice.toString()
}

/** Roots for the SDL fixtures under `classpath:graphix-poly/`. */
class SdlPolyQueries {
    @QueryMapping
    fun search(): List<Media> = listOf(Film("f1", "Dune", 155), Song("s1", "Ocean", 120))

    @QueryMapping
    fun nodes(): List<Media> = listOf(Film("f1", "Dune", 155))
}

class SdlNodeFields {
    @SchemaMapping(typeName = "Node", field = "slug")
    fun slug(media: Media): String = media.title.lowercase()
}

class SdlUnionFields {
    @SchemaMapping(typeName = "SearchResult", field = "slug")
    fun slug(media: Media): String = media.title.lowercase()
}

/** A nested sealed level under an interface: `Ticketed` is the GraphQL interface, `Paper` a member. */
@Serializable
sealed interface Ticketed {
    val code: String
}

@Serializable
sealed interface Paper : Ticketed

@Serializable
data class Boarding(
    override val code: String,
    val seat: String,
) : Paper

@Serializable
data class Digital(
    override val code: String,
    val url: String,
) : Ticketed

class TicketedQueries {
    @QueryMapping
    fun ticketed(): List<Ticketed> = listOf(Boarding("b1", "12A"), Digital("d1", "https://example.test"))
}

/** `@SerialName` renames the property on the wire, so the GraphQL field follows it. */
@Serializable
data class Track(
    @SerialName("track_id") val id: String,
    val title: String,
)

/** The input side of the same rule: the schema advertises the name the decoder reads. */
@Serializable
data class TrackFilter(
    @SerialName("track_id") val id: String,
)

class TrackQueries {
    @QueryMapping
    fun track(): Track = Track("t1", "Ocean")

    @QueryMapping
    fun echo(
        @Argument filter: TrackFilter,
    ): String = filter.id
}

/** An interface whose shared property is renamed, and an implementor that keeps the name. */
@Serializable
sealed interface Stamped {
    @SerialName("stamped_at")
    val at: String
}

@Serializable
data class Receipt(
    @SerialName("stamped_at") override val at: String,
    val total: Long,
) : Stamped

class StampedQueries {
    @QueryMapping
    fun stamped(): Stamped = Receipt("2026-08-31", 1200)
}

/** The same, but the implementor forgets the rename — GraphQL would not match the names. */
@Serializable
sealed interface Slipped {
    @SerialName("slipped_at")
    val at: String
}

@Serializable
data class Missed(
    override val at: String,
) : Slipped

class SlippedQueries {
    @QueryMapping
    fun slipped(): Slipped = Missed("2026-08-31")
}

/** The one thing `@GraphQLName` still names: a type. `@SerialName` cannot — it defaults to the FQCN. */
@Serializable
@GraphQLName("Vinyl")
data class Record(
    val label: String,
)

class RecordQueries {
    @QueryMapping
    fun record(): Record = Record("Blue Note")
}
