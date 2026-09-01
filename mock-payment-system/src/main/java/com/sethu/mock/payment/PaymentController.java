package com.sethu.mock.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
public class PaymentController {

    private final ConcurrentMap<String, PaymentRecord> paymentsByKey = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, PaymentRecord> paymentsByClaim = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
    private final RestClient callbackClient;
    private final ObjectMapper objectMapper;
    private final String callbackUrl;
    private final byte[] callbackSecret;
    private final long callbackDelayMs;
    private final long timeoutMs;
    private final String behaviour;

    public PaymentController(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${claims.callback-url}") String callbackUrl,
            @Value("${payment.callback-secret}") String callbackSecret,
            @Value("${payment.callback-delay-ms:750}") long callbackDelayMs,
            @Value("${payment.timeout-ms:5000}") long timeoutMs,
            @Value("${payment.behaviour:SUCCESS}") String behaviour
    ) {
        this.callbackClient = builder.build();
        this.objectMapper = objectMapper;
        this.callbackUrl = callbackUrl;
        this.callbackSecret = callbackSecret.getBytes(StandardCharsets.UTF_8);
        this.callbackDelayMs = callbackDelayMs;
        this.timeoutMs = timeoutMs;
        this.behaviour = behaviour.toUpperCase(Locale.ROOT);
    }

    @PostMapping("/api/v1/payment-requests")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PaymentResponse create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PaymentRequest request
    ) {
        PaymentRecord payment = paymentsByKey.computeIfAbsent(idempotencyKey, key -> {
            PaymentRecord created = new PaymentRecord(
                    request.claimId(), "PAY-" + UUID.randomUUID(), UUID.randomUUID()
            );
            paymentsByClaim.put(request.claimId(), created);
            return created;
        });
        int attempt = attempts.computeIfAbsent(idempotencyKey, key -> new AtomicInteger()).incrementAndGet();

        switch (behaviour) {
            case "BUSINESS_REJECTION" -> {
                payment.status = "FAILED";
                scheduleCallback(payment, callbackDelayMs);
            }
            case "TEMPORARY_FAILURE" -> throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Temporary payment service failure"
            );
            case "TIMEOUT" -> delay(timeoutMs);
            case "AMBIGUOUS_PAYMENT_RESPONSE" -> {
                payment.status = "COMPLETED";
                scheduleCallback(payment, callbackDelayMs);
                if (attempt == 1) {
                    delay(timeoutMs);
                }
            }
            case "DUPLICATE_CALLBACK" -> {
                payment.status = "COMPLETED";
                scheduleCallback(payment, callbackDelayMs);
                scheduleCallback(payment, callbackDelayMs + 250);
            }
            case "OUT_OF_ORDER_CALLBACK" -> {
                payment.status = "COMPLETED";
                scheduleCallback(payment, 0);
                scheduleCallback(payment, callbackDelayMs);
            }
            case "SUCCESS" -> {
                payment.status = "COMPLETED";
                scheduleCallback(payment, callbackDelayMs);
            }
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Unsupported payment behaviour: " + behaviour
            );
        }

        return payment.response();
    }

    @GetMapping("/api/v1/payments/by-claim/{claimId}")
    public PaymentResponse findByClaim(@PathVariable UUID claimId) {
        PaymentRecord payment = paymentsByClaim.get(claimId);
        if (payment == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found");
        }
        return payment.response();
    }

    private void scheduleCallback(PaymentRecord payment, long delayMs) {
        CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS).execute(() -> {
            try {
                PaymentStatusEvent event = new PaymentStatusEvent(
                        payment.eventId,
                        payment.claimId,
                        payment.paymentReference,
                        payment.status,
                        Instant.now()
                );
                String payload = objectMapper.writeValueAsString(event);
                String timestamp = String.valueOf(Instant.now().getEpochSecond());
                callbackClient.post()
                        .uri(callbackUrl)
                        .header("X-Callback-Timestamp", timestamp)
                        .header("X-Callback-Signature", signature(timestamp, payload))
                        .body(payload)
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception ignored) {
                // The claims-side reconciler provides recovery if callback delivery fails.
            }
        });
    }

    private String signature(String timestamp, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(callbackSecret, "HmacSHA256"));
            byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            return "v1=" + HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign payment callback", exception);
        }
    }

    private void delay(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Payment request interrupted");
        }
    }

    private static final class PaymentRecord {
        private final UUID claimId;
        private final String paymentReference;
        private final UUID eventId;
        private volatile String status = "ACCEPTED";

        private PaymentRecord(UUID claimId, String paymentReference, UUID eventId) {
            this.claimId = claimId;
            this.paymentReference = paymentReference;
            this.eventId = eventId;
        }

        private PaymentResponse response() {
            return new PaymentResponse(paymentReference, status);
        }
    }

    public record PaymentRequest(UUID claimId, BigDecimal amount, String currency) {
    }

    public record PaymentResponse(String paymentReference, String status) {
    }

    public record PaymentStatusEvent(
            UUID eventId,
            UUID claimId,
            String paymentReference,
            String status,
            Instant occurredAt
    ) {
    }
}
