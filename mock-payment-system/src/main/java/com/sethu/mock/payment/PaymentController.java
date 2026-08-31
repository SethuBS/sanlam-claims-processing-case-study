package com.sethu.mock.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@RestController
@RequestMapping("/api/v1/payment-requests")
public class PaymentController {

    private final ConcurrentMap<String, PaymentResponse> payments = new ConcurrentHashMap<>();

    @Value("${payment.behaviour:SUCCESS}")
    private String behaviour;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PaymentResponse create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PaymentRequest request
    ) {
        return payments.computeIfAbsent(idempotencyKey, key -> new PaymentResponse(
                "PAY-" + UUID.randomUUID(),
                "ACCEPTED"
        ));
    }

    public record PaymentRequest(UUID claimId, BigDecimal amount, String currency) {
    }

    public record PaymentResponse(String paymentReference, String status) {
    }
}
