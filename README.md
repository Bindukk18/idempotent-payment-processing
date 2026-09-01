# Idempotent Payment Processing

A Spring Boot engineering lab exploring DB-backed idempotency,
concurrent retries, deterministic response replay, and the
unavoidable crash window around external payment side effects.

## Problem

Clients retry `POST /payments` after timeouts, gateway retries, user double-submit,
or a crash mid-request.

Without a durable ownership record, each retry can call the payment provider
again and charge the customer twice.

`ConcurrentHashMap` / `synchronized` only protect one JVM. The same
`Idempotency-Key` can arrive on another replica after a restart or load-balancer
retry. In this lab, ownership is enforced through a PostgreSQL uniqueness
constraint, so it survives process restarts and works across service replicas.

## Architecture

![Idempotent Payment Processing Architecture](docs/architecture.png)

The lock is `PRIMARY KEY (idempotency_key)` on `idempotency_records`.

TX1 inserts `PROCESSING` and **commits**. The fake provider is called **outside**
any database transaction. On provider success, TX2 persists the payment,
serialized HTTP response, and marks the idempotency record `COMPLETED`. On a
definite provider failure, TX2 stores the failure response and marks the
idempotency record `FAILED`.

HTTP **425** `IDEMPOTENCY_KEY_IN_PROGRESS` is an API design choice: a concurrent
loser must not receive a fabricated 201 while the winner is still in the
provider call.

Implementation detail (fingerprint, 23505 reread after rollback, persist vs
merge): [docs/DESIGN.md](docs/DESIGN.md).

## Core guarantees

Demonstrated in this lab (PostgreSQL uniqueness, not an in-memory map):

- one ownership winner for the same `Idempotency-Key`
- provider called once for concurrent same-key requests
- deterministic replay of the stored HTTP status and body after `COMPLETED` / `FAILED`
- same key + different fingerprinted request → **409** `IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST`
- concurrent insert collisions are PostgreSQL `23505`, then a reread in a **new** transaction

Explicitly **not** guaranteed:

- exactly-once semantics across arbitrary external systems
- recovery if the process dies while the row is still `PROCESSING`
- reconciliation against the provider
- a `PROCESSING` lease or sweeper
- a real payment gateway (fake provider only)
- distributed messaging

## Crash window

```text
TX1 commits PROCESSING
     ↓
provider succeeds
     ↓
<<< process may crash here >>>
     ↓
TX2 persists payment + COMPLETED
```

If the process dies in that window, the local row stays `PROCESSING` and the
external charge may already have happened. Local state alone cannot tell which
is true.

In a production integration, provider-side idempotency can prevent a retry
from creating a second external charge. This lab forwards the same
`Idempotency-Key` to the fake provider to demonstrate propagation of that
contract; forwarding the key does not by itself make a provider idempotent.
Reconciliation solves the separate problem of determining whether the external
side effect actually happened when local state is uncertain. v0.1 does not
claim exactly-once semantics.

## Quick Start

Requires Java 21. Tests and `spring-boot:run` start embedded PostgreSQL
(not H2).

```bash
java -version
./mvnw test
./mvnw spring-boot:run
```

```bash
curl -sS -D - http://localhost:8080/payments \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: abc123' \
  -d '{"customerId":"cust-123","amount":2500,"currency":"INR"}'
```

First request: **201**, a `paymentId`, provider counter +1. Same key and
fingerprinted fields: **201**, same `paymentId`, counter unchanged. Same key,
different fingerprinted request: **409**. Concurrent same key during
`PROCESSING`: **425**, then retry for replay.
