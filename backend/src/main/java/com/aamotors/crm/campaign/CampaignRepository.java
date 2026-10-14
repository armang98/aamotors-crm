package com.aamotors.crm.campaign;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {
    @Query("select c from Campaign c where (:status is null or c.status = :status) order by c.createdAt desc")
    Page<Campaign> list(@Param("status") CampaignStatus status, Pageable pageable);

    List<Campaign> findByStatusAndScheduledAtLessThanEqual(CampaignStatus status, Instant now);

    List<Campaign> findByStatus(CampaignStatus status);
}
