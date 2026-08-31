package com.sethu.claims.application;

import com.sethu.claims.repository.OutboxEvent;
import com.sethu.claims.repository.OutboxEventRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ClaimService claimService;

    public OutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            ClaimService claimService
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.claimService = claimService;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-delay-ms:1000}")
    @Transactional
    public void publish() {
        for (OutboxEvent event : outboxEventRepository.findTop20ByPublishedAtIsNullOrderByOccurredAtAsc()) {
            try {
                claimService.process(event.getAggregateId());
                event.markPublished(Instant.now());
            } catch (RuntimeException exception) {
                // Keep the row unpublished so the next poll can retry it.
                break;
            }
        }
        outboxEventRepository.flush();
    }
}
