package com.strange.demo.api.routes

import com.strange.demo.api.model.EmailPayload
import com.strange.demo.api.model.Notification
import com.strange.demo.api.model.NotificationChannel
import com.strange.demo.api.model.NotificationDelivery
import com.strange.demo.api.model.NotificationRequest
import com.strange.demo.api.model.NotificationStatus
import com.strange.demo.api.model.UserRecipient
import com.strange.demo.api.model.audit
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/** The `/notifications` part of the spec slice: two unions, two enums, and one undocumented value. */
internal fun Route.notificationRoutes(data: DemoData) {
    route("/notifications") {
        post {
            val body = call.receive<NotificationRequest>()
            val queued =
                data.notifications.put(
                    Notification(
                        id = data.ids.next(),
                        channel = body.channel,
                        status = NotificationStatus.QUEUED,
                        recipient = body.recipient,
                        payload = body.payload,
                        traceId = body.traceId,
                        scheduledOn = body.scheduledOn,
                        legacyRef = body.legacyRef,
                        delivery = NotificationDelivery(attempts = 0),
                        metadata = audit(),
                    ),
                )
            call.respond(HttpStatusCode.Created, queued)
        }

        get {
            val channel = call.request.queryParameters["channel"]
            val all = data.notifications.all()
            call.respond(if (channel == null) all else all.filter { it.channel.wire == channel })
        }

        // Declared before "/{id}" for a human reader's sake; Ktor prefers the literal segment either way.
        get("/legacy") {
            call.respond(
                Notification(
                    id = "legacy",
                    channel = NotificationChannel.CARRIER_PIGEON,
                    status = NotificationStatus.SENT,
                    recipient = UserRecipient(userId = "u-1", email = "someone@example.com"),
                    payload = EmailPayload(subject = "by pigeon", body = "it arrived"),
                    metadata = audit(),
                ),
            )
        }

        get("/{id}") {
            val notification = data.notifications.find(call.id()) ?: return@get call.notFound("notification")
            call.respond(notification)
        }
    }
}
