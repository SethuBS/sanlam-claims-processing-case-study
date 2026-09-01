package com.sethu.claims.repository;

import com.sethu.claims.application.ClaimCommands;
import com.sethu.claims.application.ClaimService;
import com.sethu.claims.application.OutboxPublisher;
import com.sethu.claims.domain.Claim;
import com.sethu.claims.domain.ClaimStatus;
import com.sethu.claims.domain.ClaimType;
import com.sethu.claims.integration.ClientRegistryClient;
import com.sethu.claims.integration.PaymentSystemClient;
import com.sethu.claims.integration.PolicyManagerClient;
import com.sethu.claims.support.PostgresIntegrationSupport;
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

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ClaimsPersistenceIntegrationTest extends PostgresIntegrationSupport {

    @Autowired ClaimService claimService;
    @Autowired ClaimRepository claimRepository;
    @Autowired IdempotencyRecordRepository idempotencyRepository;
    @Autowired OutboxEventRepository outboxRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PlatformTransactionManager transactionManager;

    @MockitoBean ClientRegistryClient clientRegistryClient;
    @MockitoBean PolicyManagerClient policyManagerClient;
    @MockitoBean PaymentSystemClient paymentSystemClient;
    @MockitoBean OutboxPublisher outboxPublisher;

    @BeforeEach
    void cleanDatabase() {
        outboxRepository.deleteAll();
        idempotencyRepository.deleteAll();
        claimRepository.deleteAll();
    }

    @Test
    void flywayCreatesTheExpectedTables() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables "
                        + "where table_schema = 'public' "
                        + "and table_name in ('claim', 'idempotency_record', 'outbox_event')",
                Integer.class
        );

        assertThat(count).isEqualTo(3);
    }

    @Test
    void claimIdempotencyRecordAndOutboxAreCommittedTogether() {
        Claim claim = claimService.submit(command("key-persistence", "WEB-PERSISTENCE"));

        assertThat(claimRepository.findById(claim.getId())).isPresent();
        assertThat(idempotencyRepository.findByIdempotencyKey("key-persistence"))
                .get().extracting(IdempotencyRecord::getClaimId).isEqualTo(claim.getId());
        assertThat(outboxRepository.findTop20ByPublishedAtIsNullOrderByOccurredAtAsc())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getAggregateId()).isEqualTo(claim.getId());
                    assertThat(event.getPayload()).contains("WEB-PERSISTENCE");
                });
    }

    @Test
    void uniqueExternalReferenceIsEnforcedByPostgres() {
        claimRepository.saveAndFlush(claim("WEB-DUPLICATE"));

        assertThatThrownBy(() -> claimRepository.saveAndFlush(claim("WEB-DUPLICATE")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void staleConcurrentUpdateIsRejectedByOptimisticLock() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        UUID id = transaction.execute(status -> claimRepository.saveAndFlush(claim("WEB-LOCK")).getId());

        Claim first = transaction.execute(status -> claimRepository.findById(id).orElseThrow());
        Claim stale = transaction.execute(status -> claimRepository.findById(id).orElseThrow());

        first.moveTo(ClaimStatus.VALIDATING, Instant.now());
        transaction.executeWithoutResult(status -> claimRepository.saveAndFlush(first));

        stale.moveTo(ClaimStatus.VALIDATING, Instant.now());
        assertThatThrownBy(() -> transaction.executeWithoutResult(
                status -> claimRepository.saveAndFlush(stale)
        )).isInstanceOf(OptimisticLockingFailureException.class);
    }

    private ClaimCommands.SubmitClaim command(String key, String reference) {
        return new ClaimCommands.SubmitClaim(
                key, reference, ClaimType.DEATH, "CLIENT-1", "POLICY-1",
                LocalDate.of(2026, 8, 31), new BigDecimal("1000.00"), "ZAR"
        );
    }

    private Claim claim(String reference) {
        return Claim.receive(
                UUID.randomUUID(), reference, "CLIENT-1", "POLICY-1", ClaimType.STANDARD,
                LocalDate.of(2026, 8, 31), new BigDecimal("1000.00"), "ZAR", Instant.now()
        );
    }
}
