package com.sethu.claims.api;

import com.sethu.claims.application.OutboxPublisher;
import com.sethu.claims.integration.ClientRegistryClient;
import com.sethu.claims.integration.PaymentSystemClient;
import com.sethu.claims.integration.PolicyManagerClient;
import com.sethu.claims.repository.ClaimRepository;
import com.sethu.claims.repository.IdempotencyRecordRepository;
import com.sethu.claims.repository.OutboxEventRepository;
import com.sethu.claims.support.PostgresIntegrationSupport;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class ClaimHttpIntegrationTest extends PostgresIntegrationSupport {

    @LocalServerPort int port;
    @Autowired ClaimRepository claimRepository;
    @Autowired IdempotencyRecordRepository idempotencyRepository;
    @Autowired OutboxEventRepository outboxRepository;

    @MockitoBean ClientRegistryClient clientRegistryClient;
    @MockitoBean PolicyManagerClient policyManagerClient;
    @MockitoBean PaymentSystemClient paymentSystemClient;
    @MockitoBean OutboxPublisher outboxPublisher;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "";
        outboxRepository.deleteAll();
        idempotencyRepository.deleteAll();
        claimRepository.deleteAll();
    }

    @Test
    void acceptsAValidClaimWithTheDocumentedResponseContract() {
        given()
                .contentType("application/json")
                .header("Idempotency-Key", "http-key-1")
                .body(validRequest("WEB-HTTP-1"))
        .when()
                .post("/api/v1/claims")
        .then()
                .statusCode(HttpStatus.ACCEPTED.value())
                .header("Location", notNullValue())
                .body("claimId", notNullValue())
                .body("externalReference", equalTo("WEB-HTTP-1"))
                .body("status", equalTo("RECEIVED"))
                .body("priority", equalTo("CRITICAL"));
    }

    @Test
    void mapsValidationFailureToBadRequest() {
        given()
                .contentType("application/json")
                .header("Idempotency-Key", "http-key-invalid")
                .body(validRequest("WEB-HTTP-INVALID").replace("250000.00", "0"))
        .when()
                .post("/api/v1/claims")
        .then()
                .statusCode(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void mapsIdempotencyConflictToProblemJson() {
        given().contentType("application/json")
                .header("Idempotency-Key", "http-key-conflict")
                .body(validRequest("WEB-HTTP-A"))
                .post("/api/v1/claims")
                .then().statusCode(HttpStatus.ACCEPTED.value());

        given()
                .contentType("application/json")
                .header("Idempotency-Key", "http-key-conflict")
                .body(validRequest("WEB-HTTP-B"))
        .when()
                .post("/api/v1/claims")
        .then()
                .statusCode(HttpStatus.CONFLICT.value())
                .contentType("application/problem+json")
                .body("code", equalTo("CLAIM_IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void mapsUnknownClaimToNotFound() {
        given()
        .when()
                .get("/api/v1/claims/{claimId}", UUID.randomUUID())
        .then()
                .statusCode(HttpStatus.NOT_FOUND.value())
                .body("code", equalTo("CLAIM_NOT_FOUND"));
    }

    @Disabled("Security is not implemented yet; the internal callback currently has no authentication")
    @Test
    void internalPaymentCallbackRequiresServiceAuthentication() {
        given()
                .contentType("application/json")
                .body("{}")
        .when()
                .post("/internal/v1/payment-status-events")
        .then()
                .statusCode(HttpStatus.UNAUTHORIZED.value());
    }

    private String validRequest(String reference) {
        return """
                {
                  "externalReference": "%s",
                  "claimType": "DEATH",
                  "clientId": "CLIENT-10542",
                  "policyNumber": "POL-847563",
                  "incidentDate": "2026-08-31",
                  "amount": {"currency": "ZAR", "value": 250000.00}
                }
                """.formatted(reference);
    }
}
