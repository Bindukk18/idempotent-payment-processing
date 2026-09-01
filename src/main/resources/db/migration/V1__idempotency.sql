-- Idempotency ownership is a PRIMARY KEY uniqueness constraint on idempotency_key.
-- Concurrent claimers race this constraint; losers re-read the winner's row.
-- Do not treat a SELECT-then-INSERT in application code as the lock.

CREATE TABLE payments (
    payment_id VARCHAR(64) PRIMARY KEY,
    customer_id VARCHAR(128) NOT NULL,
    amount BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    provider_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE idempotency_records (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    request_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    payment_id VARCHAR(64),
    response_status INTEGER,
    response_body TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_idempotency_payment
        FOREIGN KEY (payment_id) REFERENCES payments (payment_id)
);

CREATE INDEX idx_idempotency_records_status ON idempotency_records (status);
