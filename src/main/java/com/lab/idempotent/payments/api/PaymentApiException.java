package com.lab.idempotent.payments.api;

import org.springframework.http.HttpStatus;

public class PaymentApiException extends RuntimeException {

	private final HttpStatus status;
	private final String errorCode;

	public PaymentApiException(HttpStatus status, String errorCode, String message) {
		super(message);
		this.status = status;
		this.errorCode = errorCode;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getErrorCode() {
		return errorCode;
	}
}
