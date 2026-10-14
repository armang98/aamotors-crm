package com.aamotors.crm.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(String entityType, String entityId);
    List<AuditLog> findByActionOrderByOccurredAtDesc(String action);
}
