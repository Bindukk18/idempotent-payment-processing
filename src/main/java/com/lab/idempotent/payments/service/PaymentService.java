package com.lab.idempotent.payments.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.idempotent.payments.api.HttpReplay;
import com.lab.idempotent.payments.api.PaymentApiException;
import com.lab.idempotent.payments.api.PaymentRequest;
import com.lab.idempotent.payments.api.PaymentResponse;
import com.lab.idempotent.payments.fingerprint.RequestFingerprint;
import com.lab.idempotent.payments.idempotency.DuplicateKey;
import com.lab.idempotent.payments.idempotency.IdempotencyClaimStore;
import com.lab.idempotent.payments.idempotency.IdempotencyCompletionStore;
import com.lab.idempotent.payments.idempotency.IdempotencyRecord;
import com.lab.idempotent.payments.idempotency.IdempotencyStatus;
import com.lab.idempotent.payments.provider.FakePaymentProvider;
import com.lab.idempotent.payments.provider.ProviderCharge;
import com.lab.idempotent.payments.provider.ProviderException;

@Service
public class PaymentService {

	private final IdempotencyClaimStore claimStore;
	private final IdempotencyCompletionStore completionStore;
	private final FakePaymentProvider provider;
	private final ObjectMapper objectMapper;

	public PaymentService(
			IdempotencyClaimStore claimStore,
			IdempotencyCompletionStore completionStore,
			FakePaymentProvider provider,
			ObjectMapper objectMapper) {
		this.claimStore = claimStore;
		this.completionStore = completionStore;
		this.provider = provider;
		this.objectMapper = objectMapper;
	}

	public HttpReplay process(String idempotencyKey, PaymentRequest request) {
		String fingerprint = RequestFingerprint.of(request.customerId(), request.amount(), request.currency());
		boolean claimed = tryClaim(idempotencyKey, fingerprint);
		if (!claimed) {
			return replayOrReject(idempotencyKey, fingerprint);
		}
		try {
			ProviderCharge charge =
					provider.charge(idempotencyKey, request.customerId(), request.amount(), request.currency());
			PaymentResponse response = new PaymentResponse(
					charge.paymentId(),
					"COMPLETED",
					request.customerId(),
					request.amount(),
					request.currency());
			String body = writeJson(response);
			return completionStore.complete(
					idempotencyKey,
					charge.paymentId(),
					request.customerId(),
					request.amount(),
					request.currency(),
					charge.providerReference(),
					HttpStatus.CREATED.value(),
					body);
		} catch (ProviderException ex) {
			String body = writeJson(new ErrorBody("PROVIDER_FAILURE", ex.getMessage()));
			return completionStore.fail(idempotencyKey, HttpStatus.BAD_GATEWAY.value(), body);
		}
	}

	private boolean tryClaim(String idempotencyKey, String fingerprint) {
		try {
			claimStore.insertProcessing(idempotencyKey, fingerprint);
			return true;
		} catch (RuntimeException ex) {
			if (DuplicateKey.of(ex)) {
				return false;
			}
			throw ex;
		}
	}

	private HttpReplay replayOrReject(String idempotencyKey, String fingerprint) {
		IdempotencyRecord existing = completionStore.require(idempotencyKey);
		if (!existing.getRequestFingerprint().equals(fingerprint)) {
			throw new PaymentApiException(
					HttpStatus.CONFLICT,
					"IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST",
					"Idempotency-Key was already used with a different payment request");
		}
		if (existing.getStatus() == IdempotencyStatus.PROCESSING) {
			throw new PaymentApiException(
					HttpStatus.TOO_EARLY,
					"IDEMPOTENCY_KEY_IN_PROGRESS",
					"A request with this Idempotency-Key is still PROCESSING. Retry after it completes.");
		}
		return new HttpReplay(HttpStatus.valueOf(existing.getResponseStatus()), existing.getResponseBody());
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("cannot serialize payment response", ex);
		}
	}

	private record ErrorBody(String error, String message) {
	}
}
