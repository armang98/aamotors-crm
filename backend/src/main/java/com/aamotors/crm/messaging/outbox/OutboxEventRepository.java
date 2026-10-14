package com.aamotors.crm.messaging.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /** Pending events, oldest first, locked with SKIP LOCKED so concurrent relays never double-publish. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select e from OutboxEvent e where e.publishedAt is null and e.attempts < 20 order by e.createdAt")
    List<OutboxEvent> lockPending(Pageable pageable);

    long countByPublishedAtIsNull();

    List<OutboxEvent> findByAggregateTypeAndAggregateIdOrderByCreatedAt(String aggregateType, String aggregateId);
}
