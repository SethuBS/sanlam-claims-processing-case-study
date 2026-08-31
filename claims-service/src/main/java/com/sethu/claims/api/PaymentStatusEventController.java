package com.sethu.claims.api;

import com.sethu.claims.application.ClaimService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/payment-status-events")
public class PaymentStatusEventController {

    private final ClaimService claimService;

    public PaymentStatusEventController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping
    public ResponseEntity<Void> handle(@Valid @RequestBody PaymentStatusEvent event) {
        if (!"COMPLETED".equals(event.status())) {
            return ResponseEntity.accepted().build();
        }

        claimService.markPaid(event.claimId(), event.paymentReference());
        return ResponseEntity.accepted().build();
    }

    public record PaymentStatusEvent(
            @NotNull UUID eventId,
            @NotNull UUID claimId,
            @NotBlank String paymentReference,
            @NotBlank String status,
            @NotNull Instant occurredAt
    ) {
    }
}
