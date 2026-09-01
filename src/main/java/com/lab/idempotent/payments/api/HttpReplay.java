package com.lab.idempotent.payments.api;

import org.springframework.http.HttpStatus;

public record HttpReplay(HttpStatus status, String body) {
}
