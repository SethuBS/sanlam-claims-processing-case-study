package com.sanlam.claims.api;

import com.sanlam.claims.application.ClaimCommands;
import com.sanlam.claims.application.ClaimService;
import com.sanlam.claims.domain.Claim;
import com.sanlam.claims.dto.request.SubmitClaimRequest;
import com.sanlam.claims.dto.response.ClaimResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/claims")
public class ClaimController
{

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService)
    {
        this.claimService = claimService;
    }

    @PostMapping
    public ResponseEntity<ClaimResponse> submit(@RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @Valid @RequestBody SubmitClaimRequest request)
    {
        Claim claim = claimService.submit(new ClaimCommands.SubmitClaim(idempotencyKey, request.externalReference(),
                request.claimType(), request.clientId(), request.policyNumber(), request.incidentDate(),
                request.amount().value(), request.amount().currency()));

        return ResponseEntity.accepted().location(URI.create("/api/v1/claims/" + claim.getId()))
                .body(ClaimResponse.from(claim));
    }

    @GetMapping("/{claimId}")
    public ClaimResponse get(@PathVariable UUID claimId)
    {
        return ClaimResponse.from(claimService.get(claimId));
    }

    @PostMapping("/{claimId}/decisions/approve")
    public ClaimResponse approve(@PathVariable UUID claimId)
    {
        return ClaimResponse.from(claimService.approve(claimId));
    }

    @PostMapping("/{claimId}/decisions/reject")
    public ClaimResponse reject(@PathVariable UUID claimId)
    {
        return ClaimResponse.from(claimService.reject(claimId));
    }

}
