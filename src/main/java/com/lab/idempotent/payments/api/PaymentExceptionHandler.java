package com.lab.idempotent.payments.api;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PaymentExceptionHandler {

	@ExceptionHandler(PaymentApiException.class)
	public ResponseEntity<Map<String, String>> handleApi(PaymentApiException ex) {
		return ResponseEntity.status(ex.getStatus())
				.body(Map.of("error", ex.getErrorCode(), "message", ex.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(Map.of("error", "INVALID_PAYMENT_REQUEST", "message", "Request body failed validation"));
	}
}
