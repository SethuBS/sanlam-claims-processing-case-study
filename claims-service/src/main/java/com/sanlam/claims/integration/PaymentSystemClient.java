package com.sanlam.claims.integration;

import com.sanlam.claims.dto.request.PaymentRequest;
import com.sanlam.claims.dto.response.PaymentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class PaymentSystemClient
{

    private final RestClient client;

    public PaymentSystemClient(RestClient.Builder builder, @Value("${clients.payment-system.base-url}") String baseUrl)
    {
        this.client = builder.baseUrl(baseUrl).build();
    }

    public PaymentResponse createPayment(UUID claimId, BigDecimal amount, String currency)
    {
        String key = "claim:%s:payment:v1".formatted(claimId);

        return client.post().uri("/api/v1/payment-requests").header("Idempotency-Key", key)
                .body(new PaymentRequest(claimId, amount, currency)).retrieve().body(PaymentResponse.class);
    }

    public PaymentResponse getPayment(UUID claimId)
    {
        return client.get().uri("/api/v1/payments/by-claim/{claimId}", claimId).retrieve().body(PaymentResponse.class);
    }
}
