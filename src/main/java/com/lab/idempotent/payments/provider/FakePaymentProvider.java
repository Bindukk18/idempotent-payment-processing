package com.lab.idempotent.payments.provider;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fake gateway. {@code callCount} proves duplicate invocations; it is not the
 * idempotency mechanism. The same Idempotency-Key is passed through to model
 * the production pattern of provider-side idempotency keys.
 */
@Component
public class FakePaymentProvider {

	private final AtomicInteger callCount = new AtomicInteger();
	private final AtomicBoolean failNext = new AtomicBoolean();
	private final long delayMs;

	public FakePaymentProvider(@Value("${payments.provider.delay-ms:150}") long delayMs) {
		this.delayMs = delayMs;
	}

	public ProviderCharge charge(String idempotencyKey, String customerId, long amount, String currency) {
		callCount.incrementAndGet();
		sleep();
		if (failNext.compareAndSet(true, false)) {
			throw new ProviderException("fake provider declined charge for key " + idempotencyKey);
		}
		String paymentId = "pay-" + UUID.randomUUID();
		return new ProviderCharge(paymentId, "prov-" + idempotencyKey);
	}

	public int getCallCount() {
		return callCount.get();
	}

	public void failNext() {
		failNext.set(true);
	}

	private void sleep() {
		if (delayMs <= 0) {
			return;
		}
		try {
			Thread.sleep(delayMs);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new ProviderException("provider call interrupted");
		}
	}
}
