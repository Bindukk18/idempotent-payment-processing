package com.lab.idempotent.payments.api;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

@JsonPropertyOrder({"customerId", "amount", "currency"})
public record PaymentRequest(
		@NotBlank String customerId,
		@NotNull @Positive Long amount,
		@NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) {
}
