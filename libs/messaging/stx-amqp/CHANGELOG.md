# stx-amqp

## 0.3.0

### Minor Changes

- [#321](https://github.com/softistx/nxgt-krepo/pull/321) [`18abc6f`](https://github.com/softistx/nxgt-krepo/commit/18abc6ff525567c44b66049f578785f0cc675da4) Thanks [@SteveGT96](https://github.com/SteveGT96)! - BREAKING: a prefetch of `0` or above 64 now throws, and an `OnDelivery` consumer can now fail with
  `AmqpBufferFullException`.
  
  `ConsumerOptions` now refuses a `prefetch` above `ConsumerOptions.MAX_PREFETCH` (64) and a prefetch of `0`, with an error that names the limit. Deliveries land in a 128-message buffer (twice the cap, headroom for connection recovery), and a full buffer parks a thread of the connection's shared delivery pool: with as many parked callbacks as the pool has threads, every other consumer on the connection starves (`DeliveryThreadsTest`). Lower an explicit prefetch to 64 or less.
  
  An auto-ack consumer (`AckStrategy.OnDelivery`) no longer blocks the delivery thread when its buffer is full: the flow fails with the new `AmqpBufferFullException`, naming the queue and the buffer size. Collect faster, or acknowledge manually or after the handler so that prefetch applies.

### Patch Changes

- [#321](https://github.com/softistx/nxgt-krepo/pull/321) [`18abc6f`](https://github.com/softistx/nxgt-krepo/commit/18abc6ff525567c44b66049f578785f0cc675da4) Thanks [@SteveGT96](https://github.com/SteveGT96)! - Documentation only: a provided `instance` is not closed by the plugin, but Ktor's DI still closes it at application stop. The KDoc and the `stx-ktor` README now say when that bites and how to replace `onShutdown` in `install(DI)`.

- [#275](https://github.com/softistx/nxgt-krepo/pull/275) [`bfb34b5`](https://github.com/softistx/nxgt-krepo/commit/bfb34b55103af682e56c5333ef1f169eefb3fdd8) Thanks [@SteveGT96](https://github.com/SteveGT96)! - No change to the API or behaviour: `AmqpPublisher`'s confirm settling and `AmqpConsumer`'s message handling moved into files of their own.
- Updated dependencies [[`18abc6f`](https://github.com/softistx/nxgt-krepo/commit/18abc6ff525567c44b66049f578785f0cc675da4)]:
  - stx-ktor@0.3.1

## 0.2.2

### Patch Changes

- [#262](https://github.com/softistx/nxgt-krepo/pull/262) [`e7a0481`](https://github.com/softistx/nxgt-krepo/commit/e7a0481579ce8626699bd8091dda570c4741fc9f) Thanks [@SteveGT96](https://github.com/SteveGT96)! - A publish whose channel is lost now fails with `AmqpClosedException` instead of hanging, a consumer's flow ends when its channel or connection shuts down, `Undecodable.Fail` stops the flow instead of killing the channel, `concurrency` above one takes the permit before launching a coroutine per delivery, and an acknowledgement lost to a shutdown or a closed channel after the handler returned is logged instead of ending `process` and `processWithRetry`, since the broker redelivers the message after recovery.

- [#250](https://github.com/softistx/nxgt-krepo/pull/250) [`a532490`](https://github.com/softistx/nxgt-krepo/commit/a532490c89e008572840e11ec4e1350b69056fd6) Thanks [@SteveGT96](https://github.com/SteveGT96)! - The server dependencies move to their latest patch and minor releases: Ktor 3.6.0, the MongoDB driver 5.13.0, Kafka clients 4.4.0, the RabbitMQ client 5.37.0, Hibernate Validator 9.1.5 and KotlinPoet 2.4.0. Lettuce stays at 7.7 and kotlinx.serialization at the toolchain's 1.11.0.

- [#250](https://github.com/softistx/nxgt-krepo/pull/250) [`a532490`](https://github.com/softistx/nxgt-krepo/commit/a532490c89e008572840e11ec4e1350b69056fd6) Thanks [@SteveGT96](https://github.com/SteveGT96)! - kotlin-reflect moves to 2.4.20, matching the Kotlin stdlib the toolchain ships, and classgraph (classpath scanning in stx-jpa) moves from 4.8 to 6.0.0, a major release of that dependency that needs JDK 25, so a consumer that calls classgraph 4 APIs directly has to move too.
- Updated dependencies [[`e7a0481`](https://github.com/softistx/nxgt-krepo/commit/e7a0481579ce8626699bd8091dda570c4741fc9f), [`a532490`](https://github.com/softistx/nxgt-krepo/commit/a532490c89e008572840e11ec4e1350b69056fd6), [`e7a0481`](https://github.com/softistx/nxgt-krepo/commit/e7a0481579ce8626699bd8091dda570c4741fc9f), [`e7a0481`](https://github.com/softistx/nxgt-krepo/commit/e7a0481579ce8626699bd8091dda570c4741fc9f)]:
  - stx-common@0.3.0
  - stx-ktor@0.3.0

<!-- Everything below the title is written by Changesets, newest first; this note is a footer
     because anything between the title and the first section would be pushed down by every
     release. -->

---

This is the release line for `io.github.softistx:stx-amqp`, `io.github.softistx:stx-amqp-ktor` and `io.github.softistx:stx-amqp-spring` — the **stx-amqp** family. They carry one version between them and ship together.

Everything through **0.2.1** was released in lockstep with every other library in this repository,
under one shared version and one shared changelog. That history is in
[the root `CHANGELOG.md`](../../../CHANGELOG.md); this file starts where it stops.
