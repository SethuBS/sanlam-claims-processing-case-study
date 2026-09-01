package com.sethu.claims.repository;

import com.sethu.claims.domain.Claim;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ClaimRepository extends JpaRepository<Claim, UUID> {

    Optional<Claim> findByExternalReference(String externalReference);

    List<Claim> findTop20ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            com.sethu.claims.domain.ClaimStatus status,
            Instant updatedBefore
    );
}
