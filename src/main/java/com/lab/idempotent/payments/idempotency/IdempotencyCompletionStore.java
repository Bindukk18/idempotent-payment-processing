package com.lab.idempotent.payments.idempotency;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lab.idempotent.payments.api.HttpReplay;
import com.lab.idempotent.payments.payment.Payment;
import com.lab.idempotent.payments.payment.PaymentRepository;

/**
 * Transaction 2: persist payment + serialized response after the provider returns.
 */
@Component
public class IdempotencyCompletionStore {

	private final IdempotencyRepository idempotencyRepository;
	private final PaymentRepository paymentRepository;

	public IdempotencyCompletionStore(
			IdempotencyRepository idempotencyRepository, PaymentRepository paymentRepository) {
		this.idempotencyRepository = idempotencyRepository;
		this.paymentRepository = paymentRepository;
	}

	@Transactional
	public HttpReplay complete(
			String idempotencyKey,
			String paymentId,
			String customerId,
			long amount,
			String currency,
			String providerReference,
			int responseStatus,
			String responseBody) {
		Instant now = Instant.now();
		paymentRepository.saveAndFlush(
				new Payment(paymentId, customerId, amount, currency, providerReference, now));
		IdempotencyRecord record = idempotencyRepository
				.findById(idempotencyKey)
				.orElseThrow(() -> new IllegalStateException("missing idempotency row " + idempotencyKey));
		record.complete(paymentId, responseStatus, responseBody, now);
		idempotencyRepository.saveAndFlush(record);
		return new HttpReplay(HttpStatus.valueOf(responseStatus), responseBody);
	}

	@Transactional
	public HttpReplay fail(String idempotencyKey, int responseStatus, String responseBody) {
		IdempotencyRecord record = idempotencyRepository
				.findById(idempotencyKey)
				.orElseThrow(() -> new IllegalStateException("missing idempotency row " + idempotencyKey));
		record.fail(responseStatus, responseBody, Instant.now());
		idempotencyRepository.saveAndFlush(record);
		return new HttpReplay(HttpStatus.valueOf(responseStatus), responseBody);
	}

	@Transactional(readOnly = true)
	public IdempotencyRecord require(String idempotencyKey) {
		return idempotencyRepository
				.findById(idempotencyKey)
				.orElseThrow(() -> new IllegalStateException("missing idempotency row " + idempotencyKey));
	}
}
