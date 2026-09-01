package com.sanlam.claims.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_payment_event")
public class ProcessedPaymentEvent
{

    @Id
    private UUID eventId;

    private UUID claimId;

    private Instant processedAt;

    protected ProcessedPaymentEvent()
    {
    }
}
