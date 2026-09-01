package com.lab.idempotent.payments.payment;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "payments")
public class Payment {

	@Id
	@Column(name = "payment_id", nullable = false, length = 64)
	private String paymentId;

	@Column(name = "customer_id", nullable = false, length = 128)
	private String customerId;

	@Column(name = "amount", nullable = false)
	private long amount;

	@Column(name = "currency", nullable = false, length = 3)
	private String currency;

	@Column(name = "provider_reference", nullable = false, length = 128)
	private String providerReference;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Payment() {
	}

	public Payment(
			String paymentId,
			String customerId,
			long amount,
			String currency,
			String providerReference,
			Instant createdAt) {
		this.paymentId = paymentId;
		this.customerId = customerId;
		this.amount = amount;
		this.currency = currency;
		this.providerReference = providerReference;
		this.createdAt = createdAt;
	}

	public String getPaymentId() {
		return paymentId;
	}
}
