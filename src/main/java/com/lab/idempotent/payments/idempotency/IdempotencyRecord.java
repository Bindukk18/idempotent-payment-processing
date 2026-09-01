package com.lab.idempotent.payments.idempotency;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {

	@Id
	@Column(name = "idempotency_key", nullable = false, length = 128)
	private String idempotencyKey;

	@Column(name = "request_fingerprint", nullable = false, length = 64)
	private String requestFingerprint;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 32)
	private IdempotencyStatus status;

	@Column(name = "payment_id", length = 64)
	private String paymentId;

	@Column(name = "response_status")
	private Integer responseStatus;

	@Column(name = "response_body")
	private String responseBody;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected IdempotencyRecord() {
	}

	public IdempotencyRecord(String idempotencyKey, String requestFingerprint, Instant now) {
		this.idempotencyKey = idempotencyKey;
		this.requestFingerprint = requestFingerprint;
		this.status = IdempotencyStatus.PROCESSING;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public String getRequestFingerprint() {
		return requestFingerprint;
	}

	public IdempotencyStatus getStatus() {
		return status;
	}

	public String getPaymentId() {
		return paymentId;
	}

	public Integer getResponseStatus() {
		return responseStatus;
	}

	public String getResponseBody() {
		return responseBody;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void complete(String paymentId, int responseStatus, String responseBody, Instant now) {
		this.status = IdempotencyStatus.COMPLETED;
		this.paymentId = paymentId;
		this.responseStatus = responseStatus;
		this.responseBody = responseBody;
		this.updatedAt = now;
	}

	public void fail(int responseStatus, String responseBody, Instant now) {
		this.status = IdempotencyStatus.FAILED;
		this.responseStatus = responseStatus;
		this.responseBody = responseBody;
		this.updatedAt = now;
	}
}
