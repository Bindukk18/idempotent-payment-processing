package com.lab.idempotent.payments.idempotency;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import jakarta.persistence.EntityExistsException;

public final class DuplicateKey {

	private DuplicateKey() {
	}

	public static boolean of(Throwable error) {
		for (Throwable current = error; current != null; current = current.getCause()) {
			if (current instanceof DataIntegrityViolationException
					|| current instanceof EntityExistsException
					|| current instanceof ConstraintViolationException) {
				return true;
			}
			if (current instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
				return true;
			}
			String message = current.getMessage();
			if (message != null
					&& (message.contains("idempotency_records_pkey") || message.contains("SQLState: 23505"))) {
				return true;
			}
		}
		return false;
	}
}
