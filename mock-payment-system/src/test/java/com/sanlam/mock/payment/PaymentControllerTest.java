package com.sanlam.mock.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanlam.mock.payment.dto.request.PaymentRequest;
import com.sanlam.mock.payment.dto.response.PaymentResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentControllerTest
{

    @ParameterizedTest
    @ValueSource(strings = {"SUCCESS", "BUSINESS_REJECTION", "TIMEOUT", "DUPLICATE_CALLBACK", "OUT_OF_ORDER_CALLBACK",
            "AMBIGUOUS_PAYMENT_RESPONSE"})
    void supportedNonRejectionBehavioursKeepAStableIdempotentPayment(String behaviour)
    {
        PaymentController controller = controller(behaviour);
        UUID claimId = UUID.randomUUID();
        PaymentRequest request = new PaymentRequest(claimId, BigDecimal.TEN, "ZAR");

        PaymentResponse first = controller.create("claim-key", request);
        PaymentResponse duplicate = controller.create("claim-key", request);

        assertThat(duplicate.paymentReference()).isEqualTo(first.paymentReference());
        assertThat(controller.findByClaim(claimId).paymentReference()).isEqualTo(first.paymentReference());
    }

    @Test
    void temporaryFailureIsExplicit()
    {
        PaymentRequest request = new PaymentRequest(UUID.randomUUID(), BigDecimal.TEN, "ZAR");

        assertThatThrownBy(() -> controller("TEMPORARY_FAILURE").create("temporary", request))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("503");
    }

    private PaymentController controller(String behaviour)
    {
        return new PaymentController(RestClient.builder(), new ObjectMapper().findAndRegisterModules(),
                "http://127.0.0.1:1/internal/v1/payment-status-events", "test-callback-secret-12345", 60_000, 1,
                behaviour);
    }
}
