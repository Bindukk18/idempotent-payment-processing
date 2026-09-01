package com.lab.idempotent.payments.provider;

public record ProviderCharge(String paymentId, String providerReference) {
}
