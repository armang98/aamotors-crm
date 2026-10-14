package com.aamotors.crm.messaging.sms;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SmsNotificationRepository extends JpaRepository<SmsNotification, UUID> {

    List<SmsNotification> findByServiceOrderIdOrderByCreatedAtDesc(UUID serviceOrderId);

    Optional<SmsNotification> findFirstByServiceOrderIdOrderByCreatedAtDesc(UUID serviceOrderId);

    Optional<SmsNotification> findByServiceOrderIdAndKind(UUID serviceOrderId, SmsNotification.Kind kind);

    Optional<SmsNotification> findByProviderMessageId(String providerMessageId);

    @Query("select n from SmsNotification n where (:status is null or n.status = :status) order by n.createdAt desc")
    Page<SmsNotification> list(@Param("status") SmsNotification.Status status, Pageable pageable);

    List<SmsNotification> findByStatusAndProviderAndSentAtBefore(SmsNotification.Status status, String provider, Instant before);

    long countByStatus(SmsNotification.Status status);
}
