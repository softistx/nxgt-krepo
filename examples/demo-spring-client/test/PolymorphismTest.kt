package com.strange.demo.spring

import com.strange.demo.api.DemoServer
import com.strange.demo.api.startDemoServer
import com.strange.demo.spring.api.apis.NotificationsApi
import com.strange.demo.spring.api.models.EmailPayload
import com.strange.demo.spring.api.models.GroupRecipient
import com.strange.demo.spring.api.models.NotificationChannel
import com.strange.demo.spring.api.models.NotificationRequest
import com.strange.demo.spring.api.models.NotificationStatus
import com.strange.demo.spring.api.models.SmsPayload
import com.strange.demo.spring.api.models.UserRecipient
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.LocalDate
import java.util.UUID

/**
 * The same composition slice as the Ktorfit test, read and written with Jackson instead.
 *
 * This is where the two styles are held to one wire format. The server writes with
 * kotlinx.serialization; if Jackson's discriminated union, deduced union or tolerant enum disagreed
 * with kotlinx's about what those bytes look like, one of these two suites would fail rather than
 * the difference shipping. The Kotlin types differ on purpose — `java.util.UUID` and
 * `java.time.LocalDate` here, `kotlin.uuid.Uuid` and `kotlinx.datetime.LocalDate` there — and the
 * bytes still have to match.
 */
class PolymorphismTest :
    FeatureSpec({

        lateinit var server: DemoServer
        lateinit var client: SpringDemoClient

        beforeSpec {
            server = startDemoServer(port = 0)
            client = SpringDemoClient(server.baseUrl)
        }

        afterSpec { server.close() }

        feature("a union the document gives a discriminator") {
            scenario("Jackson selects the same variant kotlinx wrote") {
                val queued =
                    client.notifications.queueNotification(
                        NotificationRequest(
                            channel = NotificationChannel.BY_EMAIL,
                            recipient = UserRecipient(userId = "u-1"),
                            payload = EmailPayload(subject = "hello", body = "there"),
                        ),
                    )

                queued.status shouldBe NotificationStatus.QUEUED
                queued.payload.kind shouldBe "email"
                queued.payload.shouldBeInstanceOf<EmailPayload>().body shouldBe "there"

                client.notifications
                    .findNotification(queued.id)
                    .payload
                    .shouldBeInstanceOf<EmailPayload>()
            }

            scenario("the discriminator is written once, so the server can read it back") {
                // Jackson's As.PROPERTY would emit `kind` twice — once as the type tag and once as
                // the declared property — and kotlinx rejects a duplicate key outright.
                val queued =
                    client.notifications.queueNotification(
                        NotificationRequest(
                            channel = NotificationChannel.BY_SMS,
                            recipient = UserRecipient(userId = "u-2"),
                            payload = SmsPayload(text = "once"),
                        ),
                    )

                queued.payload.shouldBeInstanceOf<SmsPayload>().text shouldBe "once"
            }
        }

        feature("a union the document gives no discriminator") {
            scenario("deduction picks the variant by the keys present") {
                val queued =
                    client.notifications.queueNotification(
                        NotificationRequest(
                            channel = NotificationChannel.BY_SMS,
                            recipient = GroupRecipient(groupId = "g-1", size = 4),
                            payload = SmsPayload(text = "everyone"),
                        ),
                    )

                queued.recipient.shouldBeInstanceOf<GroupRecipient>().size shouldBe 4
            }
        }

        feature("the modern formats") {
            scenario("a uuid and a date arrive as java.time and java.util types") {
                val traceId = UUID.fromString("6f5b3a58-6a3f-4c1e-9f0a-5f7f5c2d1b44")
                val queued =
                    client.notifications.queueNotification(
                        NotificationRequest(
                            channel = NotificationChannel.BY_EMAIL,
                            recipient = UserRecipient(userId = "u-3"),
                            payload = EmailPayload(subject = "later", body = "not yet"),
                            traceId = traceId,
                            scheduledOn = LocalDate.of(2026, 9, 1),
                        ),
                    )

                queued.traceId shouldBe traceId
                queued.scheduledOn shouldBe LocalDate.of(2026, 9, 1)
            }
        }

        feature("a server that knows more than this client's document") {
            scenario("an unlisted enum value decodes to the fallback here too") {
                client.notifications.findLegacyNotification().channel shouldBe NotificationChannel.UNKNOWN
            }
        }

        feature("an enum outside a JSON body") {
            scenario("a query parameter goes out as its wire value") {
                client.notifications
                    .findNotifications(channel = NotificationChannel.BY_SMS)
                    .map { it.channel }
                    .toSet() shouldBe
                    setOf(NotificationChannel.BY_SMS)
            }
        }
        feature("what the document said about becoming Kotlin") {
            scenario("a value class survives a path parameter through Spring's own conversion") {
                // Spring formats an argument through its ConversionService, which is where the
                // generated enums needed help. A value class over String is erased before it gets
                // there — asserted here rather than assumed.
                val queued =
                    client.notifications.queueNotification(
                        NotificationRequest(
                            channel = NotificationChannel.BY_EMAIL,
                            recipient = UserRecipient(userId = "u-9"),
                            payload = EmailPayload(subject = "typed", body = "id"),
                        ),
                    )

                client.notifications.findNotification(queued.id).id shouldBe queued.id
            }

            scenario("an enum's Kotlin names are the document's, its wire values are untouched") {
                NotificationChannel.BY_SMS.toString() shouldBe "sms"
            }

            scenario("an operation the document keeps internal is not on the interface at all") {
                NotificationsApi::class.java.methods.none { it.name == "purgeNotifications" } shouldBe true
            }
        }
    })
