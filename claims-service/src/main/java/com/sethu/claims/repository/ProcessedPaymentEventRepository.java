package com.sethu.claims.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ProcessedPaymentEventRepository extends JpaRepository<ProcessedPaymentEvent, UUID> {

    @Modifying
    @Query(value = """
            insert into processed_payment_event(event_id, claim_id, processed_at)
            values (:eventId, :claimId, :processedAt)
            on conflict (event_id) do nothing
            """, nativeQuery = true)
    int registerIfNew(
            @Param("eventId") UUID eventId,
            @Param("claimId") UUID claimId,
            @Param("processedAt") Instant processedAt
    );
}
