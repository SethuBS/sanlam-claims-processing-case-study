package com.sethu.claims.api;

import com.sethu.claims.application.OutboxPublisher;
import com.sethu.claims.domain.Claim;
import com.sethu.claims.domain.ClaimStatus;
import com.sethu.claims.domain.ClaimType;
import com.sethu.claims.integration.ClientRegistryClient;
import com.sethu.claims.integration.PaymentSystemClient;
import com.sethu.claims.integration.PolicyManagerClient;
import com.sethu.claims.repository.ClaimRepository;
import com.sethu.claims.repository.IdempotencyRecordRepository;
import com.sethu.claims.repository.OutboxEventRepository;
import com.sethu.claims.repository.ProcessedPaymentEventRepository;
import com.sethu.claims.support.PostgresIntegrationSupport;
import com.sethu.claims.security.PaymentCallbackAuthenticator;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "payment.reconciliation.enabled=false"
)
@Testcontainers(disabledWithoutDocker = true)
class ClaimHttpIntegrationTest extends PostgresIntegrationSupport {

    @LocalServerPort int port;
    @Autowired ClaimRepository claimRepository;
    @Autowired IdempotencyRecordRepository idempotencyRepository;
    @Autowired OutboxEventRepository outboxRepository;
    @Autowired ProcessedPaymentEventRepository processedPaymentEventRepository;
    @Autowired PaymentCallbackAuthenticator callbackAuthenticator;

    @MockitoBean ClientRegistryClient clientRegistryClient;
    @MockitoBean PolicyManagerClient policyManagerClient;
    @MockitoBean PaymentSystemClient paymentSystemClient;
    @MockitoBean OutboxPublisher outboxPublisher;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "";
        outboxRepository.deleteAll();
        processedPaymentEventRepository.deleteAll();
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

    @Test
    void internalPaymentCallbackRequiresServiceAuthentication() {
        given()
                .contentType("application/json")
                .body("{}")
        .when()
                .post("/internal/v1/payment-status-events")
        .then()
                .statusCode(HttpStatus.UNAUTHORIZED.value())
                .body("code", equalTo("PAYMENT_CALLBACK_UNAUTHENTICATED"));
    }

    @Test
    void authenticPaymentCallbackIsAcceptedAndApplied() {
        Claim claim = Claim.receive(
                UUID.randomUUID(), "WEB-SIGNED-CALLBACK", "CLIENT-1", "POL-1",
                ClaimType.STANDARD, LocalDate.of(2026, 8, 31),
                BigDecimal.TEN, "ZAR", Instant.now()
        );
        claim.moveTo(ClaimStatus.VALIDATING, Instant.now());
        claim.moveTo(ClaimStatus.APPROVED, Instant.now());
        claim.markPaymentPending("PAY-SIGNED", Instant.now());
        claimRepository.saveAndFlush(claim);

        String payload = """
                {"eventId":"%s","claimId":"%s","paymentReference":"PAY-SIGNED",\
                "status":"COMPLETED","occurredAt":"2026-09-01T08:00:00Z"}
                """.formatted(UUID.randomUUID(), claim.getId()).replace("\n", "");
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        given()
                .contentType("application/json")
                .header("X-Callback-Timestamp", timestamp)
                .header("X-Callback-Signature", callbackAuthenticator.signature(timestamp, payload))
                .body(payload)
        .when()
                .post("/internal/v1/payment-status-events")
        .then()
                .statusCode(HttpStatus.ACCEPTED.value());

        org.assertj.core.api.Assertions.assertThat(
                claimRepository.findById(claim.getId()).orElseThrow().getStatus()
        ).isEqualTo(ClaimStatus.PAID);
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
