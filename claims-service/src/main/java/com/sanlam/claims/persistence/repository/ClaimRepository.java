package com.sanlam.claims.persistence.repository;

import com.sanlam.claims.domain.Claim;
import com.sanlam.claims.domain.ClaimStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ClaimRepository extends JpaRepository<Claim, UUID>
{

    Optional<Claim> findByExternalReference(String externalReference);

    List<Claim> findTop20ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(ClaimStatus status, Instant updatedBefore);
}
