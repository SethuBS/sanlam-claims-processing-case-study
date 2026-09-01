package com.sanlam.claims.persistence.repository;

import com.sanlam.claims.persistence.entity.OutboxEvent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID>
{

    List<OutboxEvent> findTop20ByPublishedAtIsNullOrderByOccurredAtAsc();
}
