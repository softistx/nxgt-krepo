---
"stx-amqp": patch
---

A publish whose channel is lost now fails with `AmqpClosedException` instead of hanging, a consumer's flow ends when its channel or connection shuts down, `Undecodable.Fail` stops the flow instead of killing the channel, and `concurrency` above one no longer launches a coroutine per delivery.
