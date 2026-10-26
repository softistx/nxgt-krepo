---
"stx-amqp": patch
---

A publish whose channel is lost now fails with `AmqpClosedException` instead of hanging, a consumer's flow ends when its channel or connection shuts down, `Undecodable.Fail` stops the flow instead of killing the channel, `concurrency` above one takes the permit before launching a coroutine per delivery, and an acknowledgement lost to a shutdown or a closed channel after the handler returned is logged instead of ending `process` and `processWithRetry`, since the broker redelivers the message after recovery.
