package com.lab.idempotent.payments.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.lab.idempotent.payments.service.PaymentService;

import jakarta.validation.Valid;

@RestController
public class PaymentController {

	public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

	private final PaymentService paymentService;

	public PaymentController(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	@PostMapping(path = "/payments", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> createPayment(
			@RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
			@Valid @RequestBody PaymentRequest request) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new PaymentApiException(
					org.springframework.http.HttpStatus.BAD_REQUEST,
					"MISSING_IDEMPOTENCY_KEY",
					"Header Idempotency-Key is required");
		}
		HttpReplay replay = paymentService.process(idempotencyKey.trim(), request);
		return ResponseEntity.status(replay.status()).contentType(MediaType.APPLICATION_JSON).body(replay.body());
	}
}
