package com.sethu.claims.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class PaymentSystemClient {

    private final RestClient client;

    public PaymentSystemClient(
            RestClient.Builder builder,
            @Value("${clients.payment-system.base-url}") String baseUrl
    ) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    public PaymentAccepted createPayment(
            UUID claimId,
            BigDecimal amount,
            String currency
    ) {
        String key = "claim:%s:payment:v1".formatted(claimId);

        return client.post()
                .uri("/api/v1/payment-requests")
                .header("Idempotency-Key", key)
                .body(new PaymentRequest(claimId, amount, currency))
                .retrieve()
                .body(PaymentAccepted.class);
    }

    public record PaymentRequest(UUID claimId, BigDecimal amount, String currency) {
    }

    public record PaymentAccepted(
            String paymentReference,
            String status
    ) {
    }
}
