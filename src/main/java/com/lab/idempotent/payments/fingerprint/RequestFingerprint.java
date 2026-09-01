package com.lab.idempotent.payments.fingerprint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Deterministic fingerprint over canonical DTO fields, not JSON text.
 * Jackson key order / whitespace must not change the hash.
 */
public final class RequestFingerprint {

	private static final char SEP = '\u001f';

	private RequestFingerprint() {
	}

	public static String of(String customerId, long amount, String currency) {
		String canonical = customerId + SEP + amount + SEP + currency;
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 not available", ex);
		}
	}
}
