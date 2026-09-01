package com.sethu.claims.contract;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URL;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiContractTest {

    @ParameterizedTest(name = "{0} contract is valid")
    @MethodSource("contracts")
    void parsesAndContainsTheRequiredOperation(String resource, String path, String responseCode) {
        URL url = getClass().getResource("/contracts/" + resource);
        assertThat(url).as("contract resource").isNotNull();

        SwaggerParseResult result = new OpenAPIParser().readLocation(
                url.toExternalForm(), null, new ParseOptions()
        );

        assertThat(result.getMessages()).isEmpty();
        OpenAPI api = result.getOpenAPI();
        assertThat(api).isNotNull();
        assertThat(api.getPaths()).containsKey(path);
        assertThat(api.getPaths().get(path).getPost()).isNotNull();
        assertThat(api.getPaths().get(path).getPost().getResponses()).containsKey(responseCode);
    }

    static Stream<Arguments> contracts() {
        return Stream.of(
                Arguments.of("client-registry.yaml", "/api/v1/client-validations", "200"),
                Arguments.of("policy-manager.yaml", "/api/v1/claim-eligibility-checks", "200"),
                Arguments.of("payment-system.yaml", "/api/v1/payment-requests", "202")
        );
    }

    @org.junit.jupiter.api.Test
    void claimsApiContractMatchesTheImplementedEndpoints() {
        URL url = getClass().getResource("/claims-api.yaml");
        assertThat(url).isNotNull();

        SwaggerParseResult result = new OpenAPIParser().readLocation(
                url.toExternalForm(), null, new ParseOptions()
        );

        assertThat(result.getMessages()).isEmpty();
        OpenAPI api = result.getOpenAPI();
        assertThat(api.getPaths()).containsKeys(
                "/api/v1/claims",
                "/api/v1/claims/{claimId}",
                "/api/v1/claims/{claimId}/decisions/approve",
                "/api/v1/claims/{claimId}/decisions/reject",
                "/internal/v1/payment-status-events"
        );
        assertThat(api.getPaths().get("/api/v1/claims").getPost().getResponses())
                .containsKey("202");
        assertThat(api.getPaths().get("/internal/v1/payment-status-events")
                .getPost().getParameters())
                .extracting(parameter -> parameter.getName())
                .containsExactlyInAnyOrder("X-Callback-Timestamp", "X-Callback-Signature");
    }
}
