---
"stx-jpa": patch
"stx-migrations": patch
---

`jpa.connection { }` now borrows, runs and closes on one Vert.x context, the migration ledger no longer leaves a record `RUNNING` when its description is longer than the column, `findPage(limit)` refuses a limit above `MAX_PAGE_SIZE` instead of overflowing on `limit + 1`, and the runtime POMs move to Hibernate Reactive 4.5.12, Hibernate ORM 7.4.13 and Vert.x 5.1.10.
