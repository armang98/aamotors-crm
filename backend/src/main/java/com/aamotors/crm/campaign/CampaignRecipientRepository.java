package com.aamotors.crm.campaign;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CampaignRecipientRepository extends JpaRepository<CampaignRecipient, UUID> {
    Page<CampaignRecipient> findByCampaignIdOrderByCreatedAt(UUID campaignId, Pageable pageable);
    List<CampaignRecipient> findByCampaignId(UUID campaignId);
    List<CampaignRecipient> findFirstNByCampaignIdAndEligibleTrueAndDispatchedAtIsNull(UUID campaignId, Pageable pageable);
    long countByCampaignId(UUID campaignId);
    long countByCampaignIdAndEligibleTrue(UUID campaignId);
    long countByCampaignIdAndEligibleTrueAndDispatchedAtIsNull(UUID campaignId);

    @Modifying
    @Query("delete from CampaignRecipient r where r.campaignId = :campaignId")
    void deleteByCampaignId(@Param("campaignId") UUID campaignId);

    @Query("select r from CampaignRecipient r where r.campaignId = :campaignId and r.eligible = true and r.dispatchedAt is null order by r.createdAt")
    List<CampaignRecipient> pending(@Param("campaignId") UUID campaignId, Pageable pageable);
}
