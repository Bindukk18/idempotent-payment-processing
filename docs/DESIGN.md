# Design notes

## Why the uniqueness constraint is the lock

Two concurrent `POST /payments` with the same `Idempotency-Key` must not both
call the provider. An application `if (!exists) insert` has a race: both
threads can observe absence. PostgreSQL `PRIMARY KEY (idempotency_key)`
serializes the insert. One row is created in `PROCESSING`. Losers receive a
constraint violation, re-read, and stop.

Claim insert uses `EntityManager.persist`, not Spring Data `save()`.
An assigned `@Id` makes `save()` `merge()` an existing row, which would
overwrite `COMPLETED` instead of hitting the uniqueness constraint.

## Transaction boundaries

```text
TX1  INSERT PROCESSING  COMMIT
         |
         v
     provider.charge(idempotencyKey, ...)   // no connection held
         |
         v
TX2  INSERT payment + UPDATE COMPLETED/FAILED + persist response  COMMIT
```

Do not hold a transaction open across the provider call. Connections would
stay checked out for 100–300 ms of simulated latency (seconds or minutes on a
real network). That exhausts the pool, extends lock duration, and couples
throughput to someone else's API.

## Duplicate-key reread (SQLState 23505)

PostgreSQL aborts a transaction that hits unique-violation `23505`. A SELECT
on that same connection would fail with “current transaction is aborted.”

v0.1 never does that. The INSERT runs in TX1 (`REQUIRES_NEW`). Spring rolls
that transaction back before the exception reaches `PaymentService`. The
caller of `process()` is not in a database transaction. The reread is
`IdempotencyCompletionStore.require()` in a **new** read-only transaction.

## In-progress duplicates

Losers of the insert race that still see `PROCESSING` get **HTTP 425**
`IDEMPOTENCY_KEY_IN_PROGRESS`. That is an explicit API design choice so a
concurrent retry is not mistaken for a completed payment (we do not invent a
201 body that does not exist yet). After `COMPLETED`, the same key+body
replays the stored 201 body with zero additional provider calls.

## Fingerprint

Canonical string: `customerId + U+001F + amount + U+001F + currency`, then
SHA-256 hex. Amount is an integer minor-unit value so `2500` is stable.
JSON serialization is not used for hashing.

## Crash window

If the process dies after a successful provider charge and before TX2, the
row stays `PROCESSING` and the charge may already have happened. Retrying
could call the provider again **unless the provider also keys off
`Idempotency-Key`**. This lab demonstrates passing that key through. It does
not add reconciliation or outbox recovery.

Exactly-once in this repo means: **at most one fake provider invocation per
successfully claimed key**, and **replay of a persisted HTTP response**.
v0.1 does **not** provide exactly-once semantics across arbitrary external
systems, including the crash window between provider success and TX2.

## Database

Tests and `spring-boot:run` use **embedded PostgreSQL** (Zonky), not H2, so
uniqueness and concurrent-insert behavior are PostgreSQL's. This is still a
single-node lab database, not a production cluster.
