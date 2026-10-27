---
"stx-amqp": minor
---

`ConsumerOptions` now refuses a `prefetch` above `ConsumerOptions.MAX_PREFETCH` (64) and a prefetch of `0`, with an error that names the limit. Deliveries land in a 64-message buffer, and a full buffer parks a thread of the connection's shared delivery pool: with as many parked callbacks as the pool has threads, every other consumer on the connection starves (`DeliveryThreadsTest`). Lower an explicit prefetch to 64 or less.

An auto-ack consumer (`AckStrategy.OnDelivery`) no longer blocks the delivery thread when its buffer is full: the flow fails with the new `AmqpBufferFullException`, naming the queue and the buffer size. Collect faster, or acknowledge manually or after the handler so that prefetch applies.
