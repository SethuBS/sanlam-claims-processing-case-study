package com.sanlam.claims.integration;

import com.sanlam.claims.dto.request.PolicyEligibilityRequest;
import com.sanlam.claims.dto.response.PolicyEligibilityResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Component
public class PolicyManagerClient
{

    private final RestClient client;

    public PolicyManagerClient(RestClient.Builder builder, @Value("${clients.policy-manager.base-url}") String baseUrl)
    {
        this.client = builder.baseUrl(baseUrl).build();
    }

    public PolicyEligibilityResponse check(String policyNumber, BigDecimal amount)
    {
        return client.post().uri("/api/v1/claim-eligibility-checks")
                .body(new PolicyEligibilityRequest(policyNumber, amount)).retrieve()
                .body(PolicyEligibilityResponse.class);
    }
}
