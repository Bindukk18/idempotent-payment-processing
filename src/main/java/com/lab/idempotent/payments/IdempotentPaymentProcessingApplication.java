package com.lab.idempotent.payments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class IdempotentPaymentProcessingApplication {

	public static void main(String[] args) {
		SpringApplication.run(IdempotentPaymentProcessingApplication.class, args);
	}
}
