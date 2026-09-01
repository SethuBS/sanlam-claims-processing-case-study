package com.sanlam.claims.support;

import java.time.Duration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

public abstract class PostgresIntegrationSupport
{

    private static final String POSTGRES_IMAGE = environmentValue("TEST_POSTGRES_IMAGE", "postgres:16-alpine");
    private static final Duration POSTGRES_STARTUP_TIMEOUT = Duration
            .parse(environmentValue("TEST_POSTGRES_STARTUP_TIMEOUT", "PT2M"));

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName("claims_test").withUsername("claims").withPassword("claims")
            .withStartupTimeout(POSTGRES_STARTUP_TIMEOUT);

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry)
    {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static String environmentValue(String name, String defaultValue)
    {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
