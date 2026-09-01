package com.sethu.claims.contract;

import com.sethu.claims.integration.ClientRegistryClient;
import com.sethu.claims.integration.PaymentSystemClient;
import com.sethu.claims.integration.PolicyManagerClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withAccepted;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ExternalSystemClientContractTest {

    @Test
    void clientRegistryAdapterMatchesTheProviderContract() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ClientRegistryClient client = new ClientRegistryClient(builder, "http://client-registry");
        server.expect(once(), requestTo("http://client-registry/api/v1/client-validations"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"clientId\":\"CLIENT-1\"}"))
                .andRespond(withSuccess(
                        "{\"validationReference\":\"CV-1\",\"valid\":true,"
                                + "\"clientStatus\":\"ACTIVE\",\"reasonCodes\":[]}",
                        MediaType.APPLICATION_JSON
                ));

        assertThat(client.validate("CLIENT-1").valid()).isTrue();
        server.verify();
    }

    @Test
    void policyManagerAdapterMatchesTheProviderContract() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyManagerClient client = new PolicyManagerClient(builder, "http://policy-manager");
        server.expect(once(), requestTo("http://policy-manager/api/v1/claim-eligibility-checks"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"policyNumber\":\"POL-1\",\"amount\":1000.00}"))
                .andRespond(withSuccess(
                        "{\"validationReference\":\"PV-1\",\"eligible\":true,"
                                + "\"manualReviewRequired\":false,\"ruleVersion\":\"v1\","
                                + "\"reasonCodes\":[]}",
                        MediaType.APPLICATION_JSON
                ));

        assertThat(client.check("POL-1", new BigDecimal("1000.00")).eligible()).isTrue();
        server.verify();
    }

    @Test
    void paymentAdapterSendsTheStableIdempotencyKey() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaymentSystemClient client = new PaymentSystemClient(builder, "http://payment-system");
        UUID claimId = UUID.randomUUID();
        server.expect(once(), requestTo("http://payment-system/api/v1/payment-requests"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "claim:" + claimId + ":payment:v1"))
                .andExpect(content().json("{\"claimId\":\"" + claimId
                        + "\",\"amount\":1000.00,\"currency\":\"ZAR\"}"))
                .andRespond(withAccepted()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"paymentReference\":\"PAY-1\",\"status\":\"ACCEPTED\"}"));

        assertThat(client.createPayment(claimId, new BigDecimal("1000.00"), "ZAR")
                .paymentReference()).isEqualTo("PAY-1");
        server.verify();
    }
}
