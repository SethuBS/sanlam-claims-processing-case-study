package com.sanlam.claims.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanlam.claims.domain.Claim;
import com.sanlam.claims.domain.ClaimStatus;
import com.sanlam.claims.domain.ClaimType;
import com.sanlam.claims.dto.response.ClientValidationResponse;
import com.sanlam.claims.dto.response.PaymentResponse;
import com.sanlam.claims.dto.response.PolicyEligibilityResponse;
import com.sanlam.claims.integration.ClientRegistryClient;
import com.sanlam.claims.integration.PaymentSystemClient;
import com.sanlam.claims.integration.PolicyManagerClient;
import com.sanlam.claims.persistence.repository.ClaimRepository;
import com.sanlam.claims.persistence.entity.IdempotencyRecord;
import com.sanlam.claims.persistence.repository.IdempotencyRecordRepository;
import com.sanlam.claims.persistence.entity.OutboxEvent;
import com.sanlam.claims.persistence.repository.OutboxEventRepository;
import com.sanlam.claims.persistence.repository.ProcessedPaymentEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimServiceTest
{

    private static final Instant NOW = Instant.parse("2026-08-31T08:15:31Z");

    @Mock
    ClaimRepository claimRepository;
    @Mock
    IdempotencyRecordRepository idempotencyRepository;
    @Mock
    OutboxEventRepository outboxRepository;
    @Mock
    ProcessedPaymentEventRepository processedPaymentEventRepository;
    @Mock
    ClientRegistryClient clientRegistryClient;
    @Mock
    PolicyManagerClient policyManagerClient;
    @Mock
    PaymentSystemClient paymentSystemClient;

    private ClaimService service;

    @BeforeEach
    void setUp()
    {
        service = new ClaimService(claimRepository, idempotencyRepository, outboxRepository,
                processedPaymentEventRepository, clientRegistryClient, policyManagerClient, paymentSystemClient,
                new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
        when(claimRepository.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void submitCommitsClaimIdempotencyRecordAndOutboxPayloadAtControlledTime()
    {
        ClaimCommands.SubmitClaim command = command("key-1", "WEB-001");
        when(idempotencyRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());

        Claim result = service.submit(command);

        assertThat(result.getStatus()).isEqualTo(ClaimStatus.RECEIVED);
        assertThat(result.getCreatedAt()).isEqualTo(NOW);
        verify(claimRepository).save(result);
        verify(idempotencyRepository).save(any(IdempotencyRecord.class));
        ArgumentCaptor<OutboxEvent> event = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo("claim.submitted");
        assertThat(event.getValue().getPayload()).contains(result.getId().toString(), "WEB-001");
    }

    @Test
    void validStraightThroughClaimCreatesOnePaymentAndBecomesPending()
    {
        Claim claim = receivedClaim();
        when(claimRepository.findById(claim.getId())).thenReturn(Optional.of(claim));
        when(clientRegistryClient.validate(claim.getClientId()))
                .thenReturn(new ClientValidationResponse("CV-1", true, "ACTIVE", new String[0]));
        when(policyManagerClient.check(claim.getPolicyNumber(), claim.getAmount()))
                .thenReturn(new PolicyEligibilityResponse("PV-1", true, false, "v1", new String[0]));
        when(paymentSystemClient.createPayment(claim.getId(), claim.getAmount(), claim.getCurrency()))
                .thenReturn(new PaymentResponse("PAY-001", "ACCEPTED"));

        Claim result = service.process(claim.getId());

        assertThat(result.getStatus()).isEqualTo(ClaimStatus.PAYMENT_PENDING);
        assertThat(result.getPaymentReference()).isEqualTo("PAY-001");
        assertThat(result.getUpdatedAt()).isEqualTo(NOW);
        verify(paymentSystemClient).createPayment(claim.getId(), claim.getAmount(), claim.getCurrency());
    }

    @Test
    void manualReviewStopsBeforePayment()
    {
        Claim claim = receivedClaim();
        when(claimRepository.findById(claim.getId())).thenReturn(Optional.of(claim));
        when(clientRegistryClient.validate(claim.getClientId()))
                .thenReturn(new ClientValidationResponse("CV-1", true, "ACTIVE", new String[0]));
        when(policyManagerClient.check(claim.getPolicyNumber(), claim.getAmount()))
                .thenReturn(new PolicyEligibilityResponse("PV-1", true, true, "v1", new String[0]));

        Claim result = service.process(claim.getId());

        assertThat(result.getStatus()).isEqualTo(ClaimStatus.MANUAL_REVIEW);
        verify(paymentSystemClient, never()).createPayment(any(), any(), any());
    }

    @Test
    void invalidClientIsBusinessRejectionAndSkipsOtherSystems()
    {
        Claim claim = receivedClaim();
        when(claimRepository.findById(claim.getId())).thenReturn(Optional.of(claim));
        when(clientRegistryClient.validate(claim.getClientId()))
                .thenReturn(new ClientValidationResponse("CV-1", false, "INACTIVE", new String[]{"CLIENT_NOT_ACTIVE"}));

        Claim result = service.process(claim.getId());

        assertThat(result.getStatus()).isEqualTo(ClaimStatus.REJECTED);
        verify(policyManagerClient, never()).check(any(), any());
        verify(paymentSystemClient, never()).createPayment(any(), any(), any());
    }

    private ClaimCommands.SubmitClaim command(String key, String reference)
    {
        return new ClaimCommands.SubmitClaim(key, reference, ClaimType.DEATH, "CLIENT-10542", "POL-847563",
                LocalDate.of(2026, 8, 31), new BigDecimal("250000.00"), "ZAR");
    }

    private Claim receivedClaim()
    {
        return Claim.receive(UUID.randomUUID(), "WEB-001", "CLIENT-10542", "POL-847563", ClaimType.DEATH,
                LocalDate.of(2026, 8, 31), new BigDecimal("250000.00"), "ZAR", NOW);
    }
}
