package com.sethu.claims.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Component
public class PolicyManagerClient {

    private final RestClient client;

    public PolicyManagerClient(
            RestClient.Builder builder,
            @Value("${clients.policy-manager.base-url}") String baseUrl
    ) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    public EligibilityResult check(String policyNumber, BigDecimal amount) {
        return client.post()
                .uri("/api/v1/claim-eligibility-checks")
                .body(new EligibilityRequest(policyNumber, amount))
                .retrieve()
                .body(EligibilityResult.class);
    }

    public record EligibilityRequest(String policyNumber, BigDecimal amount) {
    }

    public record EligibilityResult(
            String validationReference,
            boolean eligible,
            boolean manualReviewRequired,
            String ruleVersion,
            String[] reasonCodes
    ) {
    }
}
