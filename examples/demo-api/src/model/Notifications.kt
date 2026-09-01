@file:OptIn(ExperimentalSerializationApi::class)

package com.softistx.demo.api.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.uuid.Uuid

/**
 * The polymorphic, enum-carrying slice of `openapi.yaml`.
 *
 * Written by hand like the rest of this server, and deliberately *not* the same code the generator
 * produces: the point of the end-to-end test is that two independent readings of one document agree
 * on the bytes. Where the two sides are written differently — the discriminator comes from
 * `@SerialName` here and from a declared property there — that difference is the thing being tested.
 */
@Serializable
public enum class NotificationChannel(
    /** The same string `@SerialName` puts on the wire, for the query filter to compare against. */
    public val wire: String,
) {
    @SerialName("email")
    EMAIL("email"),

    @SerialName("sms")
    SMS("sms"),

    @SerialName("push")
    PUSH("push"),

    /**
     * A channel this server supports and the document does not mention, standing in for a server
     * deployed ahead of the spec its clients were generated from. Only `/notifications/legacy`
     * serves it.
     */
    @SerialName("carrier-pigeon")
    CARRIER_PIGEON("carrier-pigeon"),
}

@Serializable
public enum class NotificationStatus {
    @SerialName("queued")
    QUEUED,

    @SerialName("sent")
    SENT,

    @SerialName("failed")
    FAILED,
}

/** Told apart by a tag the document declares; kotlinx writes it from `@SerialName`. */
@Serializable
@JsonClassDiscriminator("kind")
public sealed interface NotificationPayload

@Serializable
@SerialName("email")
public data class EmailPayload(
    public val subject: String,
    public val body: String,
) : NotificationPayload

@Serializable
@SerialName("sms")
public data class SmsPayload(
    public val text: String,
) : NotificationPayload

@Serializable
@SerialName("push")
public data class PushPayload(
    public val title: String,
    public val badge: Int? = null,
) : NotificationPayload

/** Told apart by the keys it carries, because the document declares no discriminator. */
@Serializable(with = RecipientSerializer::class)
public sealed interface Recipient

@Serializable
public data class UserRecipient(
    public val userId: String,
    public val email: String? = null,
) : Recipient

@Serializable
public data class GroupRecipient(
    public val groupId: String,
    public val size: Int? = null,
) : Recipient

public object RecipientSerializer : JsonContentPolymorphicSerializer<Recipient>(Recipient::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Recipient> {
        val keys = element.jsonObject.keys
        return when {
            "userId" in keys -> UserRecipient.serializer()
            "groupId" in keys -> GroupRecipient.serializer()
            else -> throw SerializationException("no Recipient variant matches $keys")
        }
    }
}

@Serializable
public data class NotificationDelivery(
    public val attempts: Int? = null,
    public val lastError: String? = null,
)

@Serializable
public data class NotificationRequest(
    public val channel: NotificationChannel,
    public val recipient: Recipient,
    public val payload: NotificationPayload,
    public val traceId: Uuid? = null,
    public val scheduledOn: LocalDate? = null,
    public val legacyRef: String? = null,
)

@Serializable
public data class Notification(
    public val id: String,
    public val channel: NotificationChannel,
    public val status: NotificationStatus,
    public val recipient: Recipient,
    public val payload: NotificationPayload,
    public val traceId: Uuid? = null,
    public val scheduledOn: LocalDate? = null,
    public val legacyRef: String? = null,
    public val delivery: NotificationDelivery? = null,
    public val metadata: AuditMetadata,
)
