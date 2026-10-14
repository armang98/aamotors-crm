package com.aamotors.crm.referral;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReferralRepository extends JpaRepository<Referral, UUID> {

    Optional<Referral> findByInvitedId(UUID invitedCustomerId);

    @Query("""
            select r from Referral r
              join fetch r.referrer ref
              join fetch r.invited inv
              left join fetch r.reward rw
            where (:status is null or r.status = :status)
              and (:rewardStatus is null or rw.status = :rewardStatus)
              and ( lower(ref.fullName) like concat('%', :text, '%')
                 or lower(inv.fullName) like concat('%', :text, '%')
                 or ref.phone like concat('%', :phone, '%')
                 or inv.phone like concat('%', :phone, '%') )
            order by r.createdAt desc
            """)
    Page<Referral> search(@Param("text") String lowerText, @Param("phone") String phoneFragment,
                          @Param("status") ReferralStatus status, @Param("rewardStatus") RewardStatus rewardStatus,
                          Pageable pageable);

    @Query("select r from Referral r join fetch r.referrer join fetch r.invited left join fetch r.reward where r.id = :id")
    Optional<Referral> findDetailed(@Param("id") UUID id);

    @Query("select r from Referral r join fetch r.referrer join fetch r.invited left join fetch r.reward rw where r.referrer.id = :customerId order by r.createdAt desc")
    List<Referral> findByReferrer(@Param("customerId") UUID customerId);

    @Query("""
            select r from Referral r join fetch r.referrer join fetch r.invited left join fetch r.reward rw
            where (r.createdAt >= :from and r.createdAt < :to)
               or (r.qualifiedAt >= :from and r.qualifiedAt < :to)
               or (rw.issuedAt >= :from and rw.issuedAt < :to)
               or (rw.redeemedAt >= :from and rw.redeemedAt < :to)
            """)
    List<Referral> findTouching(@Param("from") Instant from, @Param("to") Instant to);

    long countByStatus(ReferralStatus status);

    @Query("select count(rw) from ReferralReward rw where rw.status = :status")
    long countRewards(@Param("status") RewardStatus status);

    @Query("select count(r) from Referral r where r.status in (com.aamotors.crm.referral.ReferralStatus.CONFIRMED, com.aamotors.crm.referral.ReferralStatus.REWARDED)")
    long countSuccessful();
}
