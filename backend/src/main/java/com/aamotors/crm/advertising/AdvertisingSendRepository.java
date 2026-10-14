package com.aamotors.crm.advertising;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdvertisingSendRepository extends JpaRepository<AdvertisingSend, UUID> {
    @Query("select s from AdvertisingSend s left join fetch s.deliveries where s.id = :id")
    Optional<AdvertisingSend> findWithDeliveries(@Param("id") UUID id);

    Page<AdvertisingSend> findByCampaignIdIsNullOrderByCreatedAtDesc(Pageable pageable);

    List<AdvertisingSend> findByCampaignId(UUID campaignId);
}
