package com.sanlam.claims.integration;

import com.sanlam.claims.dto.request.ClientValidationRequest;
import com.sanlam.claims.dto.response.ClientValidationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ClientRegistryClient
{

    private final RestClient client;

    public ClientRegistryClient(RestClient.Builder builder,
            @Value("${clients.client-registry.base-url}") String baseUrl)
    {
        this.client = builder.baseUrl(baseUrl).build();
    }

    public ClientValidationResponse validate(String clientId)
    {
        return client.post().uri("/api/v1/client-validations").body(new ClientValidationRequest(clientId)).retrieve()
                .body(ClientValidationResponse.class);
    }
}
