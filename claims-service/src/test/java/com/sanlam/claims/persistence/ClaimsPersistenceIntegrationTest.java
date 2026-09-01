package com.sanlam.claims.persistence;

import com.sanlam.claims.application.ClaimCommands;
import com.sanlam.claims.application.ClaimService;
import com.sanlam.claims.application.OutboxPublisher;
import com.sanlam.claims.domain.Claim;
import com.sanlam.claims.domain.ClaimStatus;
import com.sanlam.claims.domain.ClaimType;
import com.sanlam.claims.dto.response.PaymentResponse;
import com.sanlam.claims.integration.ClientRegistryClient;
import com.sanlam.claims.integration.PaymentSystemClient;
import com.sanlam.claims.integration.PolicyManagerClient;
import com.sanlam.claims.persistence.entity.IdempotencyRecord;
import com.sanlam.claims.persistence.repository.ClaimRepository;
import com.sanlam.claims.persistence.repository.IdempotencyRecordRepository;
import com.sanlam.claims.persistence.repository.OutboxEventRepository;
import com.sanlam.claims.persistence.repository.ProcessedPaymentEventRepository;
import com.sanlam.claims.support.PostgresIntegrationSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "payment.reconciliation.enabled=false")
@Testcontainers(disabledWithoutDocker = true)
class ClaimsPersistenceIntegrationTest extends PostgresIntegrationSupport
{

    @Autowired
    ClaimService claimService;
    @Autowired
    ClaimRepository claimRepository;
    @Autowired
    IdempotencyRecordRepository idempotencyRepository;
    @Autowired
    OutboxEventRepository outboxRepository;
    @Autowired
    ProcessedPaymentEventRepository processedPaymentEventRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    PlatformTransactionManager transactionManager;

    @MockitoBean
    ClientRegistryClient clientRegistryClient;
    @MockitoBean
    PolicyManagerClient policyManagerClient;
    @MockitoBean
    PaymentSystemClient paymentSystemClient;
    @MockitoBean
    OutboxPublisher outboxPublisher;

    @BeforeEach
    void cleanDatabase()
    {
        outboxRepository.deleteAll();
        processedPaymentEventRepository.deleteAll();
        idempotencyRepository.deleteAll();
        claimRepository.deleteAll();
    }

    @Test
    void flywayCreatesTheExpectedTables()
    {
        Integer count = jdbcTemplate.queryForObject("select count(*) from information_schema.tables "
                + "where table_schema = 'public' "
                + "and table_name in ('claim', 'idempotency_record', 'outbox_event', " + "'processed_payment_event')",
                Integer.class);

        assertThat(count).isEqualTo(4);
    }

    @Test
    void claimIdempotencyRecordAndOutboxAreCommittedTogether()
    {
        Claim claim = claimService.submit(command("key-persistence", "WEB-PERSISTENCE"));

        assertThat(claimRepository.findById(claim.getId())).isPresent();
        assertThat(idempotencyRepository.findByIdempotencyKey("key-persistence")).get()
                .extracting(IdempotencyRecord::getClaimId).isEqualTo(claim.getId());
        assertThat(outboxRepository.findTop20ByPublishedAtIsNullOrderByOccurredAtAsc()).singleElement()
                .satisfies(event -> {
                    assertThat(event.getAggregateId()).isEqualTo(claim.getId());
                    assertThat(event.getPayload()).contains("WEB-PERSISTENCE");
                });
    }

    @Test
    void uniqueExternalReferenceIsEnforcedByPostgres()
    {
        claimRepository.saveAndFlush(claim("WEB-DUPLICATE"));

        assertThatThrownBy(() -> claimRepository.saveAndFlush(claim("WEB-DUPLICATE")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void staleConcurrentUpdateIsRejectedByOptimisticLock()
    {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        UUID id = transaction.execute(status -> claimRepository.saveAndFlush(claim("WEB-LOCK")).getId());

        Claim first = transaction.execute(status -> claimRepository.findById(id).orElseThrow());
        Claim stale = transaction.execute(status -> claimRepository.findById(id).orElseThrow());

        first.moveTo(ClaimStatus.VALIDATING, Instant.now());
        transaction.executeWithoutResult(status -> claimRepository.saveAndFlush(first));

        stale.moveTo(ClaimStatus.VALIDATING, Instant.now());
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> claimRepository.saveAndFlush(stale)))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void duplicatePaymentEventIsRecordedAndAppliedExactlyOnce()
    {
        Claim pending = claim("WEB-CALLBACK-REPLAY");
        pending.moveTo(ClaimStatus.VALIDATING, Instant.now());
        pending.moveTo(ClaimStatus.APPROVED, Instant.now());
        pending.markPaymentPending("PAY-REPLAY", Instant.now());
        claimRepository.saveAndFlush(pending);
        UUID eventId = UUID.randomUUID();

        assertThat(claimService.handlePaymentStatusEvent(eventId, pending.getId(), "PAY-REPLAY", "COMPLETED")).isTrue();
        assertThat(claimService.handlePaymentStatusEvent(eventId, pending.getId(), "PAY-REPLAY", "COMPLETED"))
                .isFalse();

        assertThat(processedPaymentEventRepository.count()).isEqualTo(1);
        assertThat(claimRepository.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(ClaimStatus.PAID);
    }

    @Test
    void outOfOrderCallbackCanBeRetriedAfterTheAnalystApprovesTheClaim()
    {
        Claim manual = claim("WEB-ANALYST-CALLBACK");
        manual.moveTo(ClaimStatus.VALIDATING, Instant.now());
        manual.moveTo(ClaimStatus.MANUAL_REVIEW, Instant.now());
        claimRepository.saveAndFlush(manual);
        UUID eventId = UUID.randomUUID();

        assertThatThrownBy(
                () -> claimService.handlePaymentStatusEvent(eventId, manual.getId(), "PAY-AFTER-APPROVAL", "COMPLETED"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not waiting for payment");
        assertThat(processedPaymentEventRepository.findById(eventId)).isEmpty();

        when(paymentSystemClient.createPayment(manual.getId(), manual.getAmount(), manual.getCurrency()))
                .thenReturn(new PaymentResponse("PAY-AFTER-APPROVAL", "ACCEPTED"));
        claimService.approve(manual.getId());

        assertThat(claimService.handlePaymentStatusEvent(eventId, manual.getId(), "PAY-AFTER-APPROVAL", "COMPLETED"))
                .isTrue();
        assertThat(claimRepository.findById(manual.getId()).orElseThrow().getStatus()).isEqualTo(ClaimStatus.PAID);
    }

    private ClaimCommands.SubmitClaim command(String key, String reference)
    {
        return new ClaimCommands.SubmitClaim(key, reference, ClaimType.DEATH, "CLIENT-1", "POLICY-1",
                LocalDate.of(2026, 8, 31), new BigDecimal("1000.00"), "ZAR");
    }

    private Claim claim(String reference)
    {
        return Claim.receive(UUID.randomUUID(), reference, "CLIENT-1", "POLICY-1", ClaimType.STANDARD,
                LocalDate.of(2026, 8, 31), new BigDecimal("1000.00"), "ZAR", Instant.now());
    }
}
