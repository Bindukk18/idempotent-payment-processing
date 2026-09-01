package com.lab.idempotent.payments.provider;

public class ProviderException extends RuntimeException {

	public ProviderException(String message) {
		super(message);
	}
}
