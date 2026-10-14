package com.aamotors.crm.advertising;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageDeliveryRepository extends JpaRepository<MessageDelivery, UUID> {
    @Query("select d from MessageDelivery d where (:status is null or d.status = :status) order by d.updatedAt desc")
    Page<MessageDelivery> list(@Param("status") MessageDelivery.Status status, Pageable pageable);

    Optional<MessageDelivery> findByProviderMessageId(String providerMessageId);

    long countBySendIdAndStatus(UUID sendId, MessageDelivery.Status status);

    List<MessageDelivery> findBySendId(UUID sendId);

    Page<MessageDelivery> findBySendIdOrderByUpdatedAtDesc(UUID sendId, Pageable pageable);
}
