package com.lab.idempotent.payments.idempotency;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Transaction 1: INSERT PROCESSING and COMMIT before any provider call.
 * Uses persist() so an assigned idempotency_key is not merged onto an existing row.
 */
@Component
public class IdempotencyClaimStore {

	@PersistenceContext
	private EntityManager entityManager;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void insertProcessing(String idempotencyKey, String fingerprint) {
		entityManager.persist(new IdempotencyRecord(idempotencyKey, fingerprint, Instant.now()));
		entityManager.flush();
	}
}
