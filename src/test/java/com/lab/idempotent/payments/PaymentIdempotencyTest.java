package com.lab.idempotent.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.idempotent.payments.api.PaymentRequest;
import com.lab.idempotent.payments.fingerprint.RequestFingerprint;
import com.lab.idempotent.payments.idempotency.IdempotencyRecord;
import com.lab.idempotent.payments.idempotency.IdempotencyRepository;
import com.lab.idempotent.payments.idempotency.IdempotencyStatus;
import com.lab.idempotent.payments.provider.FakePaymentProvider;

@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "payments.provider.delay-ms=200")
class PaymentIdempotencyTest {

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private FakePaymentProvider provider;

	@Autowired
	private IdempotencyRepository idempotencyRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void firstRequestProcessesNormally() throws Exception {
		int before = provider.getCallCount();
		String key = newKey();
		ResponseEntity<String> response = post(key, body("cust-123", 2500, "INR"));
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		JsonNode json = objectMapper.readTree(response.getBody());
		assertThat(json.get("paymentId").asText()).startsWith("pay-");
		assertThat(json.get("status").asText()).isEqualTo("COMPLETED");
		assertThat(provider.getCallCount()).isEqualTo(before + 1);
	}

	@Test
	void duplicateSameKeyAndBodyReturnsPersistedResponse() throws Exception {
		String key = newKey();
		PaymentRequest payload = body("cust-dup", 2500, "INR");
		ResponseEntity<String> first = post(key, payload);
		int afterFirst = provider.getCallCount();
		ResponseEntity<String> second = post(key, payload);
		assertThat(second.getStatusCode()).isEqualTo(first.getStatusCode());
		assertThat(second.getBody()).isEqualTo(first.getBody());
		assertThat(objectMapper.readTree(second.getBody()).get("paymentId"))
				.isEqualTo(objectMapper.readTree(first.getBody()).get("paymentId"));
		assertThat(provider.getCallCount()).isEqualTo(afterFirst);
	}

	@Test
	void sameKeyDifferentBodyConflicts() {
		String key = newKey();
		post(key, body("cust-a", 2500, "INR"));
		int afterFirst = provider.getCallCount();
		ResponseEntity<String> conflict = post(key, body("cust-a", 9999, "INR"));
		assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(conflict.getBody()).contains("IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST");
		assertThat(provider.getCallCount()).isEqualTo(afterFirst);
	}

	@Test
	void twoConcurrentSameKeyRequestsCallProviderOnce() throws Exception {
		assertSingleProviderCall(2);
	}

	@Test
	void manyConcurrentSameKeyRequestsCallProviderOnce() throws Exception {
		assertSingleProviderCall(16);
	}

	@Test
	void differentKeysAreProcessedIndependently() throws Exception {
		int before = provider.getCallCount();
		ResponseEntity<String> one = post(newKey(), body("cust-x", 100, "INR"));
		ResponseEntity<String> two = post(newKey(), body("cust-y", 100, "INR"));
		assertThat(one.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(two.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(objectMapper.readTree(one.getBody()).get("paymentId"))
				.isNotEqualTo(objectMapper.readTree(two.getBody()).get("paymentId"));
		assertThat(provider.getCallCount()).isEqualTo(before + 2);
	}

	@Test
	void completedResponseSurvivesRepositoryReload() throws Exception {
		String key = newKey();
		ResponseEntity<String> created = post(key, body("cust-reload", 2500, "INR"));
		IdempotencyRecord reloaded = idempotencyRepository.findById(key).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
		assertThat(reloaded.getResponseStatus()).isEqualTo(201);
		assertThat(reloaded.getResponseBody()).isEqualTo(created.getBody());
		assertThat(reloaded.getPaymentId()).isEqualTo(objectMapper.readTree(created.getBody()).get("paymentId").asText());
	}

	@Test
	void missingIdempotencyKeyIsRejected() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> response =
				rest.postForEntity("/payments", new HttpEntity<>("{\"customerId\":\"cust-123\",\"amount\":1,\"currency\":\"INR\"}", headers), String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("MISSING_IDEMPOTENCY_KEY");
	}

	@Test
	void blankIdempotencyKeyIsRejected() {
		ResponseEntity<String> response = post("  ", body("cust-123", 1, "INR"));
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("MISSING_IDEMPOTENCY_KEY");
	}

	@Test
	void fingerprintIsStableAndSensitiveToFields() {
		String a = RequestFingerprint.of("cust-123", 2500, "INR");
		String b = RequestFingerprint.of("cust-123", 2500, "INR");
		assertThat(a).isEqualTo(b);
		assertThat(a).hasSize(64);
		assertThat(RequestFingerprint.of("cust-999", 2500, "INR")).isNotEqualTo(a);
		assertThat(RequestFingerprint.of("cust-123", 2501, "INR")).isNotEqualTo(a);
		assertThat(RequestFingerprint.of("cust-123", 2500, "USD")).isNotEqualTo(a);
	}

	@Test
	void uniquenessConstraintParticipatesInConcurrencyCorrectness() {
		Integer pkCount = jdbcTemplate.queryForObject(
				"""
				SELECT COUNT(*) FROM information_schema.table_constraints
				WHERE table_name = 'idempotency_records'
				  AND constraint_type = 'PRIMARY KEY'
				""",
				Integer.class);
		assertThat(pkCount).isEqualTo(1);

		jdbcTemplate.update(
				"""
				INSERT INTO idempotency_records
				  (idempotency_key, request_fingerprint, status, created_at, updated_at)
				VALUES ('constraint-probe', 'fp', 'PROCESSING', NOW(), NOW())
				""");
		assertThatThrownBy(() -> jdbcTemplate.update(
						"""
						INSERT INTO idempotency_records
						  (idempotency_key, request_fingerprint, status, created_at, updated_at)
						VALUES ('constraint-probe', 'fp-other', 'PROCESSING', NOW(), NOW())
						"""))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void providerFailureTransitionsToFailedAndReplays() {
		String key = newKey();
		provider.failNext();
		int before = provider.getCallCount();
		ResponseEntity<String> failed = post(key, body("cust-fail", 2500, "INR"));
		assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
		assertThat(failed.getBody()).contains("PROVIDER_FAILURE");
		IdempotencyRecord record = idempotencyRepository.findById(key).orElseThrow();
		assertThat(record.getStatus()).isEqualTo(IdempotencyStatus.FAILED);
		ResponseEntity<String> replay = post(key, body("cust-fail", 2500, "INR"));
		assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
		assertThat(replay.getBody()).isEqualTo(failed.getBody());
		assertThat(provider.getCallCount()).isEqualTo(before + 1);
	}

	@Test
	void concurrentLoserSeesProcessingThenCompletedReplay() throws Exception {
		String key = newKey();
		PaymentRequest payload = body("cust-race", 2500, "INR");
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			List<Future<ResponseEntity<String>>> futures = pool.invokeAll(List.of(
					() -> post(key, payload),
					() -> post(key, payload)));
			List<ResponseEntity<String>> responses = new ArrayList<>();
			for (Future<ResponseEntity<String>> future : futures) {
				responses.add(future.get(10, TimeUnit.SECONDS));
			}
			assertThat(responses.stream().map(ResponseEntity::getStatusCode))
					.contains(HttpStatus.CREATED)
					.containsAnyOf(HttpStatus.CREATED, HttpStatus.TOO_EARLY);
			ResponseEntity<String> after = post(key, payload);
			assertThat(after.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		} finally {
			pool.shutdownNow();
		}
	}

	private void assertSingleProviderCall(int callers) throws Exception {
		String key = newKey();
		PaymentRequest payload = body("cust-concurrent", 2500, "INR");
		int before = provider.getCallCount();
		ExecutorService pool = Executors.newFixedThreadPool(callers);
		try {
			List<Callable<ResponseEntity<String>>> tasks = new ArrayList<>();
			for (int i = 0; i < callers; i++) {
				tasks.add(() -> post(key, payload));
			}
			List<Future<ResponseEntity<String>>> futures = pool.invokeAll(tasks);
			int created = 0;
			int inProgress = 0;
			String paymentId = null;
			for (Future<ResponseEntity<String>> future : futures) {
				ResponseEntity<String> response = future.get(15, TimeUnit.SECONDS);
				if (response.getStatusCode() == HttpStatus.CREATED) {
					created++;
					paymentId = objectMapper.readTree(response.getBody()).get("paymentId").asText();
				} else if (response.getStatusCode() == HttpStatus.TOO_EARLY) {
					inProgress++;
					assertThat(response.getBody()).contains("IDEMPOTENCY_KEY_IN_PROGRESS");
				}
			}
			assertThat(created).isGreaterThanOrEqualTo(1);
			assertThat(created + inProgress).isEqualTo(callers);
			assertThat(provider.getCallCount()).isEqualTo(before + 1);
			ResponseEntity<String> replay = post(key, payload);
			assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.CREATED);
			assertThat(objectMapper.readTree(replay.getBody()).get("paymentId").asText()).isEqualTo(paymentId);
			assertThat(provider.getCallCount()).isEqualTo(before + 1);
			assertThat(jdbcTemplate.queryForObject(
							"SELECT COUNT(*) FROM payments WHERE payment_id = ?", Integer.class, paymentId))
					.isEqualTo(1);
		} finally {
			pool.shutdownNow();
		}
	}

	private ResponseEntity<String> post(String idempotencyKey, PaymentRequest body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("Idempotency-Key", idempotencyKey);
		return rest.postForEntity("/payments", new HttpEntity<>(body, headers), String.class);
	}

	private static PaymentRequest body(String customerId, long amount, String currency) {
		return new PaymentRequest(customerId, amount, currency);
	}

	private static String newKey() {
		return "key-" + UUID.randomUUID();
	}
}
