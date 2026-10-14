package com.aamotors.crm.messaging.outbox;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/** Call from inside a domain transaction; the event commits (or rolls back) with it. */
@Service
public class OutboxService {

    private final OutboxEventRepository repository;
    private final Clock clock;

    public OutboxService(OutboxEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent append(String aggregateType, String aggregateId, String eventType, Map<String, Object> payload) {
        return repository.save(new OutboxEvent(aggregateType, aggregateId, eventType, payload, Instant.now(clock)));
    }
}
