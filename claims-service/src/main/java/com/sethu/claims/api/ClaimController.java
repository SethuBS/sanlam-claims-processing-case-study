package com.sethu.claims.api;

import com.sethu.claims.application.ClaimCommands;
import com.sethu.claims.application.ClaimService;
import com.sethu.claims.domain.Claim;
import com.sethu.claims.domain.ClaimType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/claims")
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping
    public ResponseEntity<ClaimResponse> submit(
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @Valid @RequestBody SubmitClaimRequest request
    ) {
        Claim claim = claimService.submit(new ClaimCommands.SubmitClaim(
                idempotencyKey,
                request.externalReference(),
                request.claimType(),
                request.clientId(),
                request.policyNumber(),
                request.incidentDate(),
                request.amount().value(),
                request.amount().currency()
        ));

        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/claims/" + claim.getId()))
                .body(ClaimResponse.from(claim));
    }

    @GetMapping("/{claimId}")
    public ClaimResponse get(@PathVariable UUID claimId) {
        return ClaimResponse.from(claimService.get(claimId));
    }

    @PostMapping("/{claimId}/decisions/approve")
    public ClaimResponse approve(@PathVariable UUID claimId) {
        return ClaimResponse.from(claimService.approve(claimId));
    }

    @PostMapping("/{claimId}/decisions/reject")
    public ClaimResponse reject(@PathVariable UUID claimId) {
        return ClaimResponse.from(claimService.reject(claimId));
    }

    public record SubmitClaimRequest(
            @NotBlank String externalReference,
            @NotNull ClaimType claimType,
            @NotBlank String clientId,
            @NotBlank String policyNumber,
            @NotNull LocalDate incidentDate,
            @NotNull MoneyRequest amount
    ) {
    }

    public record MoneyRequest(
            @NotBlank String currency,
            @NotNull @Positive BigDecimal value
    ) {
    }

    public record ClaimResponse(
            UUID claimId,
            String externalReference,
            String status,
            String priority,
            String paymentReference
    ) {
        static ClaimResponse from(Claim claim) {
            return new ClaimResponse(
                    claim.getId(),
                    claim.getExternalReference(),
                    claim.getStatus().name(),
                    claim.getPriority().name(),
                    claim.getPaymentReference()
            );
        }
    }
}
