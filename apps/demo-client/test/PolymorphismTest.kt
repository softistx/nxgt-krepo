package com.strange.demo.client

import com.strange.demo.api.startDemoServer
import com.strange.demo.client.api.NotificationsApi
import com.strange.demo.client.api.model.DeliveryAttempt
import com.strange.demo.client.api.model.EmailPayload
import com.strange.demo.client.api.model.GroupRecipient
import com.strange.demo.client.api.model.NotificationChannel
import com.strange.demo.client.api.model.NotificationRequest
import com.strange.demo.client.api.model.NotificationStatus
import com.strange.demo.client.api.model.PushPayload
import com.strange.demo.client.api.model.SmsPayload
import com.strange.demo.client.api.model.UserRecipient
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

/**
 * The composition slice, driven end to end: the server writes these shapes with hand-written
 * kotlinx models and this client reads them with generated ones.
 *
 * Rendering the generated source cannot prove any of this. A discriminated union has to come back
 * as the right subtype, a union with *no* discriminator has to be told apart by its keys alone, and
 * a tolerant enum only means something against a server that sends a value this client's document
 * never listed — which `/notifications/legacy` does on purpose.
 */
class PolymorphismTest :
    FeatureSpec({
        lateinit var server: AutoCloseable
        lateinit var client: DemoClient

        beforeSpec {
            val started = startDemoServer(port = 0)
            server = started
            client = DemoClient(started.baseUrl)
        }

        afterSpec {
            client.close()
            server.close()
        }

        feature("a union the document gives a discriminator") {
            scenario("the variant sent is the variant that comes back") {
                val queued =
                    client.notifications.queueNotification(
                        NotificationRequest(
                            channel = NotificationChannel.BY_EMAIL,
                            recipient = UserRecipient(userId = "u-1", email = "someone@example.com"),
                            payload = EmailPayload(subject = "hello", body = "there"),
                        ),
                    )

                queued.status shouldBe NotificationStatus.QUEUED
                queued.payload.kind shouldBe "email"
                queued.payload.shouldBeInstanceOf<EmailPayload>().subject shouldBe "hello"

                client.notifications.findNotification(queued.id) shouldBe queued
            }

            scenario("a second variant of the same union is not confused with the first") {
                val queued =
                    client.notifications.queueNotification(
                        NotificationRequest(
                            channel = NotificationChannel.BY_PUSH,
                            recipient = UserRecipient(userId = "u-2"),
                            payload = PushPayload(title = "ping", badge = 3),
                        ),
                    )

                queued.payload.shouldBeInstanceOf<PushPayload>().badge shouldBe 3
            }
        }

        feature("a union the document gives no discriminator") {
            scenario("each variant is told apart by the keys it carries, in both directions") {
                val toGroup =
                    client.notifications.queueNotification(
                        NotificationRequest(
                            channel = NotificationChannel.BY_SMS,
                            recipient = GroupRecipient(groupId = "g-1", size = 12),
                            payload = SmsPayload(text = "everyone"),
                        ),
                    )

                toGroup.recipient.shouldBeInstanceOf<GroupRecipient>().size shouldBe 12
                client.notifications
                    .findNotification(toGroup.id)
                    .recipient
                    .shouldBeInstanceOf<GroupRecipient>()
                    .groupId shouldBe "g-1"
            }
        }

        feature("the modern formats and the document's own words") {
            scenario("a uuid and a date survive the round trip as themselves") {
                val traceId = Uuid.parse("6f5b3a58-6a3f-4c1e-9f0a-5f7f5c2d1b44")
                val queued =
                    client.notifications.queueNotification(
                        NotificationRequest(
                            channel = NotificationChannel.BY_EMAIL,
                            recipient = UserRecipient(userId = "u-3"),
                            payload = EmailPayload(subject = "later", body = "not yet"),
                            traceId = traceId,
                            scheduledOn = LocalDate(2026, 9, 1),
                        ),
                    )

                queued.traceId shouldBe traceId
                queued.scheduledOn shouldBe LocalDate(2026, 9, 1)
            }
        }

        feature("a server that knows more than this client's document") {
            scenario("an unlisted enum value decodes to the fallback instead of throwing") {
                // The server serves `carrier-pigeon` here, which openapi.yaml does not list. Without
                // a fallback entry this call fails outright and the whole response is lost.
                val legacy = client.notifications.findLegacyNotification()

                legacy.channel shouldBe NotificationChannel.UNKNOWN
                legacy.status shouldBe NotificationStatus.SENT
                legacy.payload.shouldBeInstanceOf<EmailPayload>().subject shouldBe "by pigeon"
            }
        }

        feature("an enum outside a JSON body") {
            scenario("a query parameter goes out as its wire value, not its Kotlin name") {
                client.notifications.queueNotification(
                    NotificationRequest(
                        channel = NotificationChannel.BY_SMS,
                        recipient = UserRecipient(userId = "u-4"),
                        payload = SmsPayload(text = "filtered"),
                    ),
                )

                val smsOnly = client.notifications.findNotifications(channel = NotificationChannel.BY_SMS)
                smsOnly.map { it.channel }.toSet() shouldBe setOf(NotificationChannel.BY_SMS)
                smsOnly.size shouldBe 2
            }
        }
        feature("what the document said about becoming Kotlin") {
            scenario("a value class is a real type here and a bare string on the wire") {
                // The server knows nothing of NotificationId: it stores a String and reads the path
                // segment as one. If the wrapper reached the wire, neither call would resolve.
                val queued =
                    client.notifications.queueNotification(
                        NotificationRequest(
                            channel = NotificationChannel.BY_EMAIL,
                            recipient = UserRecipient(userId = "u-9"),
                            payload = EmailPayload(subject = "typed", body = "id"),
                        ),
                    )

                val fetched = client.notifications.findNotification(queued.id)
                fetched.id shouldBe queued.id
                fetched.id.value
                    .toIntOrNull()
                    .shouldNotBeNull()
            }

            scenario("an enum's Kotlin names are the document's, its wire values are untouched") {
                NotificationChannel.BY_SMS.toString() shouldBe "sms"
                NotificationChannel.BY_EMAIL.wireValue shouldBe "email"
            }

            scenario("a renamed inline schema is what the property holds") {
                val queued =
                    client.notifications.queueNotification(
                        NotificationRequest(
                            channel = NotificationChannel.BY_PUSH,
                            recipient = UserRecipient(userId = "u-10"),
                            payload = PushPayload(title = "named"),
                        ),
                    )

                // `delivery` would be `NotificationDelivery` without x-kotlin-name; the type here is
                // the assertion, and it is checked at compile time.
                val delivery: DeliveryAttempt? = queued.delivery
                delivery.shouldNotBeNull().attempts shouldBe 0
            }

            scenario("an operation the document keeps internal is not on the interface at all") {
                NotificationsApi::class.java.methods.none { it.name == "purgeNotifications" } shouldBe true
            }
        }
    })
