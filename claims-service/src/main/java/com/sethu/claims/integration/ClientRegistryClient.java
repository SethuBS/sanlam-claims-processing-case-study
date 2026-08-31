package com.sethu.claims.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ClientRegistryClient {

    private final RestClient client;

    public ClientRegistryClient(
            RestClient.Builder builder,
            @Value("${clients.client-registry.base-url}") String baseUrl
    ) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    public ClientValidationResult validate(String clientId) {
        return client.post()
                .uri("/api/v1/client-validations")
                .body(new ClientValidationRequest(clientId))
                .retrieve()
                .body(ClientValidationResult.class);
    }

    public record ClientValidationRequest(String clientId) {
    }

    public record ClientValidationResult(
            String validationReference,
            boolean valid,
            String clientStatus,
            String[] reasonCodes
    ) {
    }
}
