package com.sanlam.claims.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord
{

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private String requestHash;

    @Column(nullable = false)
    private UUID claimId;

    @Column(nullable = false)
    private Instant createdAt;

    protected IdempotencyRecord()
    {
    }

    public IdempotencyRecord(UUID id, String idempotencyKey, String requestHash, UUID claimId, Instant createdAt)
    {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.claimId = claimId;
        this.createdAt = createdAt;
    }

    public UUID getId()
    {
        return id;
    }
    public String getIdempotencyKey()
    {
        return idempotencyKey;
    }
    public String getRequestHash()
    {
        return requestHash;
    }
    public UUID getClaimId()
    {
        return claimId;
    }
    public Instant getCreatedAt()
    {
        return createdAt;
    }
}
