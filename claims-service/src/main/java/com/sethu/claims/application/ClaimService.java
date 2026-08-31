package com.sethu.claims.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethu.claims.domain.Claim;
import com.sethu.claims.domain.ClaimStatus;
import com.sethu.claims.integration.ClientRegistryClient;
import com.sethu.claims.integration.PaymentSystemClient;
import com.sethu.claims.integration.PolicyManagerClient;
import com.sethu.claims.repository.ClaimRepository;
import com.sethu.claims.repository.IdempotencyRecord;
import com.sethu.claims.repository.IdempotencyRecordRepository;
import com.sethu.claims.repository.OutboxEvent;
import com.sethu.claims.repository.OutboxEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ClientRegistryClient clientRegistryClient;
    private final PolicyManagerClient policyManagerClient;
    private final PaymentSystemClient paymentSystemClient;
    private final ObjectMapper objectMapper;

    public ClaimService(
            ClaimRepository claimRepository,
            IdempotencyRecordRepository idempotencyRecordRepository,
            OutboxEventRepository outboxEventRepository,
            ClientRegistryClient clientRegistryClient,
            PolicyManagerClient policyManagerClient,
            PaymentSystemClient paymentSystemClient,
            ObjectMapper objectMapper
    ) {
        this.claimRepository = claimRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.clientRegistryClient = clientRegistryClient;
        this.policyManagerClient = policyManagerClient;
        this.paymentSystemClient = paymentSystemClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Claim submit(ClaimCommands.SubmitClaim command) {
        String requestHash = hash(command);

        Optional<IdempotencyRecord> existing =
                idempotencyRecordRepository.findByIdempotencyKey(command.idempotencyKey());

        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(requestHash)) {
                throw new IllegalArgumentException("Idempotency key was used with a different request");
            }
            return claimRepository.findById(existing.get().getClaimId())
                    .orElseThrow(() -> new IllegalStateException("Idempotent claim no longer exists"));
        }

        try {
            Instant now = Instant.now();
            Claim claim = Claim.receive(
                    UUID.randomUUID(),
                    command.externalReference(),
                    command.clientId(),
                    command.policyNumber(),
                    command.claimType(),
                    command.incidentDate(),
                    command.amount(),
                    command.currency(),
                    now
            );

            claimRepository.save(claim);
            idempotencyRecordRepository.save(new IdempotencyRecord(
                    UUID.randomUUID(),
                    command.idempotencyKey(),
                    requestHash,
                    claim.getId(),
                    now
            ));
            outboxEventRepository.save(new OutboxEvent(
                    UUID.randomUUID(),
                    "claim.submitted",
                    claim.getId(),
                    payload(claim),
                    now
            ));

            return claim;
        } catch (DataIntegrityViolationException exception) {
            return idempotencyRecordRepository.findByIdempotencyKey(command.idempotencyKey())
                    .flatMap(record -> claimRepository.findById(record.getClaimId()))
                    .orElseThrow(() -> exception);
        }
    }

    @Transactional(readOnly = true)
    public Claim get(UUID claimId) {
        return claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + claimId));
    }

    @Transactional
    public Claim process(UUID claimId) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + claimId));

        if (claim.getStatus() != ClaimStatus.RECEIVED) {
            return claim;
        }

        claim.moveTo(ClaimStatus.VALIDATING, Instant.now());

        var client = clientRegistryClient.validate(claim.getClientId());
        if (client == null || !client.valid()) {
            claim.moveTo(ClaimStatus.REJECTED, Instant.now());
            return claimRepository.save(claim);
        }

        var eligibility = policyManagerClient.check(
                claim.getPolicyNumber(),
                claim.getAmount()
        );

        if (eligibility == null || !eligibility.eligible()) {
            claim.moveTo(ClaimStatus.REJECTED, Instant.now());
            return claimRepository.save(claim);
        }

        if (eligibility.manualReviewRequired()) {
            claim.moveTo(ClaimStatus.MANUAL_REVIEW, Instant.now());
            return claimRepository.save(claim);
        }

        claim.moveTo(ClaimStatus.APPROVED, Instant.now());
        PaymentSystemClient.PaymentAccepted payment = paymentSystemClient.createPayment(
                claim.getId(),
                claim.getAmount(),
                claim.getCurrency()
        );

        claim.markPaymentPending(payment.paymentReference(), Instant.now());
        return claimRepository.save(claim);
    }

    @Transactional
    public Claim approve(UUID claimId) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + claimId));

        claim.moveTo(ClaimStatus.APPROVED, Instant.now());
        PaymentSystemClient.PaymentAccepted payment = paymentSystemClient.createPayment(
                claim.getId(),
                claim.getAmount(),
                claim.getCurrency()
        );
        claim.markPaymentPending(payment.paymentReference(), Instant.now());
        return claimRepository.save(claim);
    }

    @Transactional
    public Claim reject(UUID claimId) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + claimId));
        claim.moveTo(ClaimStatus.REJECTED, Instant.now());
        return claimRepository.save(claim);
    }

    @Transactional
    public Claim markPaid(UUID claimId, String paymentReference) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + claimId));
        claim.markPaid(paymentReference, Instant.now());
        return claimRepository.save(claim);
    }

    private String payload(Claim claim) {
        try {
            return objectMapper.writeValueAsString(new ClaimSubmittedPayload(
                    claim.getId(),
                    claim.getExternalReference(),
                    claim.getClaimType().name()
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialise claim event", exception);
        }
    }

    private String hash(Object value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash request", exception);
        }
    }

    private record ClaimSubmittedPayload(
            UUID claimId,
            String externalReference,
            String claimType
    ) {
    }
}
