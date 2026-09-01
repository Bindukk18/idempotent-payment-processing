package com.lab.idempotent.payments.api;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"paymentId", "status", "customerId", "amount", "currency"})
public record PaymentResponse(
		String paymentId,
		String status,
		String customerId,
		long amount,
		String currency) {
}
