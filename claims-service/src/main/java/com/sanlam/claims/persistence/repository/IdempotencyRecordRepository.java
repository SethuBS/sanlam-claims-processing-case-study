package com.sanlam.claims.persistence.repository;

import com.sanlam.claims.persistence.entity.IdempotencyRecord;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID>
{

    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);
}
