package com.lab.idempotent.payments.config;

import java.io.IOException;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * Lab database: embedded PostgreSQL so uniqueness/concurrency is real Postgres,
 * without requiring a separately running Docker daemon for {@code ./mvnw test}.
 */
@Configuration
public class EmbeddedPostgresConfig {

	@Bean(destroyMethod = "close")
	public EmbeddedPostgres embeddedPostgres() throws IOException {
		return EmbeddedPostgres.builder().setPort(0).start();
	}

	@Bean
	public DataSource dataSource(EmbeddedPostgres embeddedPostgres) {
		return embeddedPostgres.getPostgresDatabase();
	}
}
