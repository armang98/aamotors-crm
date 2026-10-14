package com.aamotors.crm.referral;

import com.aamotors.crm.audit.AuditService;
import com.aamotors.crm.common.error.ApiException;
import com.aamotors.crm.common.phone.PhoneNumberNormalizer;
import com.aamotors.crm.customer.Customer;
import com.aamotors.crm.customer.CustomerService;
import com.aamotors.crm.settings.SettingsService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;

@Service
public class ReferralService {

    private final ReferralRepository referrals;
    private final CustomerService customers;
    private final SettingsService settings;
    private final AuditService audit;
    private final Clock clock;
    /** Hook for the service-order module (Phase 4) to validate qualifying / redeemed orders. */
    private volatile ServiceOrderGateway orderGateway = ServiceOrderGateway.NONE;

    public interface ServiceOrderGateway {
        /** @throws ApiException when the order does not exist, is unpaid, or belongs to another customer. */
        void assertPaidOrderOf(UUID serviceOrderId, UUID customerId);
        ServiceOrderGateway NONE = (orderId, customerId) -> {};
    }

    public ReferralService(ReferralRepository referrals, CustomerService customers, SettingsService settings,
                           AuditService audit, Clock clock) {
        this.referrals = referrals;
        this.customers = customers;
        this.settings = settings;
        this.audit = audit;
        this.clock = clock;
    }

    public void registerServiceOrderGateway(ServiceOrderGateway gateway) {
        this.orderGateway = gateway;
    }

    // ---------- queries ----------

    @Transactional(readOnly = true)
    public Page<ReferralDtos.ReferralView> search(String q, ReferralStatus status, RewardStatus rewardStatus, Pageable pageable) {
        String query = q == null ? "" : q.trim();
        String phone = PhoneNumberNormalizer.searchFragment(query).orElse("#");
        return referrals.search(query.toLowerCase(), phone, status, rewardStatus, pageable).map(ReferralService::toView);
    }

    @Transactional(readOnly = true)
    public ReferralDtos.ReferralView get(UUID id) {
        return toView(require(id));
    }

    @Transactional(readOnly = true)
    public ReferralDtos.Summary summary() {
        return new ReferralDtos.Summary(referrals.count(), referrals.countSuccessful(),
                referrals.countRewards(RewardStatus.AVAILABLE), referrals.countRewards(RewardStatus.REDEEMED),
                ReferralPolicy.DISCOUNT_PERCENT);
    }

    @Transactional(readOnly = true)
    public ReferralDtos.CustomerRewards rewardsOf(UUID customerId) {
        customers.require(customerId);
        List<Referral> mine = referrals.findByReferrer(customerId);
        return new ReferralDtos.CustomerRewards(
                mine.stream().filter(r -> r.getReward().getStatus() == RewardStatus.AVAILABLE).map(ReferralService::toView).toList(),
                mine.stream().filter(r -> r.getReward().getStatus() == RewardStatus.REDEEMED).map(ReferralService::toView).toList());
    }

    @Transactional(readOnly = true)
    public ReferralDtos.Analytics analytics(int year, Integer month) {
        ZoneId zone = ZoneId.of(settings.current().getTimeZone());
        boolean daily = month != null;
        LocalDate start = daily ? LocalDate.of(year, month, 1) : LocalDate.of(year, 1, 1);
        LocalDate end = daily ? start.plusMonths(1) : start.plusYears(1);
        Instant from = start.atStartOfDay(zone).toInstant();
        Instant to = end.atStartOfDay(zone).toInstant();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern(daily ? "yyyy-MM-dd" : "yyyy-MM");

        Map<String, long[]> buckets = new LinkedHashMap<>();
        for (LocalDate d = start; d.isBefore(end); d = daily ? d.plusDays(1) : d.plusMonths(1)) {
            buckets.put(d.format(fmt), new long[4]);
        }
        Function<Instant, String> key = i -> i.atZone(zone).toLocalDate().format(fmt);
        for (Referral r : referrals.findTouching(from, to)) {
            bump(buckets, key, r.getCreatedAt(), from, to, 0);
            bump(buckets, key, r.getQualifiedAt(), from, to, 1);
            if (r.getReward() != null) {
                bump(buckets, key, r.getReward().getIssuedAt(), from, to, 2);
                bump(buckets, key, r.getReward().getRedeemedAt(), from, to, 3);
            }
        }
        List<ReferralDtos.Point> points = buckets.entrySet().stream()
                .map(e -> new ReferralDtos.Point(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3]))
                .toList();
        return new ReferralDtos.Analytics(daily ? "day" : "month", year, month, points);
    }

    private static void bump(Map<String, long[]> buckets, Function<Instant, String> key, Instant at, Instant from, Instant to, int idx) {
        if (at == null || at.isBefore(from) || !at.isBefore(to)) return;
        long[] b = buckets.get(key.apply(at));
        if (b != null) b[idx]++;
    }

    // ---------- commands ----------

    @Transactional
    public ReferralDtos.ReferralView create(ReferralDtos.CreateReferralRequest req) {
        if (req.referrerCustomerId().equals(req.invitedCustomerId())) {
            throw ApiException.badRequest("referral.selfReferral", "A customer cannot refer themselves");
        }
        Customer referrer = customers.require(req.referrerCustomerId());
        Customer invited = customers.require(req.invitedCustomerId());
        referrals.findByInvitedId(invited.getId()).ifPresent(existing -> {
            throw ApiException.conflict("referral.invitedAlreadyReferred", "This customer has already been referred");
        });
        Referral r = new Referral(referrer, invited, blankToNull(req.notes()));
        try {
            referrals.saveAndFlush(r);
        } catch (DataIntegrityViolationException e) {
            // race on uq_referrals_invited
            throw ApiException.conflict("referral.invitedAlreadyReferred", "This customer has already been referred");
        }
        audit.record("REFERRAL_CREATED", "referral", r.getId().toString(), Map.of(
                "referrer", referrer.getId().toString(), "invited", invited.getId().toString(), "discountPercent", ReferralPolicy.DISCOUNT_PERCENT));
        return toView(require(r.getId()));
    }

    /** Invited customer completed their first qualifying paid service → confirm and issue the referrer's reward atomically. */
    @Transactional
    public ReferralDtos.ReferralView confirm(UUID id, ReferralDtos.ConfirmRequest req) {
        Referral r = require(id);
        if (r.getStatus() != ReferralStatus.PENDING) {
            throw ApiException.conflict("referral.invalidTransition", "Only pending referrals can be confirmed");
        }
        if (r.getReward().getStatus() != RewardStatus.NOT_ISSUED) {
            throw ApiException.conflict("referral.rewardAlreadyIssued", "Reward already issued");
        }
        if (req.qualifyingServiceOrderId() != null) {
            orderGateway.assertPaidOrderOf(req.qualifyingServiceOrderId(), r.getInvited().getId());
        }
        Instant now = Instant.now(clock);
        Instant qualifiedAt = req.qualifiedAt() == null ? now
                : req.qualifiedAt().atStartOfDay(ZoneId.of(settings.current().getTimeZone())).toInstant();
        r.confirm(req.qualifyingServiceOrderId(), qualifiedAt);
        r.getReward().issue(now);
        r.markRewarded();
        audit.record("REFERRAL_REWARD_ISSUED", "referral", id.toString(), Map.of(
                "rewardId", r.getReward().getId() == null ? "pending" : r.getReward().getId().toString(),
                "customer", r.getReferrer().getId().toString(),
                "qualifyingServiceOrderId", String.valueOf(req.qualifyingServiceOrderId()),
                "discountPercent", ReferralPolicy.DISCOUNT_PERCENT));
        return toView(r);
    }

    @Transactional
    public ReferralDtos.ReferralView redeem(UUID id, ReferralDtos.RedeemRequest req) {
        Referral r = require(id);
        ReferralReward reward = r.getReward();
        if (reward.getStatus() == RewardStatus.REDEEMED) {
            throw ApiException.conflict("referral.rewardAlreadyRedeemed", "This discount has already been used");
        }
        if (reward.getStatus() != RewardStatus.AVAILABLE) {
            throw ApiException.conflict("referral.rewardNotAvailable", "No available discount on this referral");
        }
        if (req.serviceOrderId() != null) {
            orderGateway.assertPaidOrderOf(req.serviceOrderId(), r.getReferrer().getId());
        }
        reward.redeem(req.serviceOrderId(), blankToNull(req.notes()), Instant.now(clock));
        try {
            referrals.saveAndFlush(r);
        } catch (DataIntegrityViolationException e) {
            // uq_referral_rewards_order: the order already carries a referral discount
            throw ApiException.conflict("referral.orderAlreadyDiscounted", "That service order already has a referral discount");
        }
        audit.record("REFERRAL_REWARD_REDEEMED", "referral", id.toString(), Map.of(
                "rewardId", reward.getId().toString(), "customer", r.getReferrer().getId().toString(),
                "serviceOrderId", String.valueOf(req.serviceOrderId()), "discountPercent", ReferralPolicy.DISCOUNT_PERCENT));
        return toView(r);
    }

    @Transactional
    public ReferralDtos.ReferralView cancel(UUID id, ReferralDtos.CancelRequest req) {
        Referral r = require(id);
        if (r.getStatus() == ReferralStatus.CANCELLED || r.getReward().getStatus() == RewardStatus.REDEEMED) {
            throw ApiException.conflict("referral.invalidTransition", "Referral cannot be cancelled");
        }
        r.cancel(req.reason().trim());
        if (r.getReward().getStatus() == RewardStatus.AVAILABLE) {
            r.getReward().voidReward(req.reason().trim());
        }
        audit.record("REFERRAL_CANCELLED", "referral", id.toString(), Map.of("reason", req.reason().trim()));
        return toView(r);
    }

    // ---------- helpers ----------

    private Referral require(UUID id) {
        return referrals.findDetailed(id).orElseThrow(() -> ApiException.notFound("Referral not found"));
    }

    static ReferralDtos.ReferralView toView(Referral r) {
        ReferralReward w = r.getReward();
        var reward = new ReferralDtos.RewardView(w.getStatus(), w.getIssuedAt(), w.getRedeemedAt(), w.getRedeemedServiceOrderId(),
                w.getRedemptionNotes(), w.getVoidReason());
        return new ReferralDtos.ReferralView(r.getId(), ref(r.getReferrer()), ref(r.getInvited()), r.getStatus(), r.getDiscountPercent(),
                r.getQualifyingServiceOrderId(), r.getQualifiedAt(), r.getCancelReason(), reward, r.getNotes(), r.getCreatedAt(), r.getUpdatedAt());
    }

    private static ReferralDtos.CustomerRef ref(Customer c) {
        return new ReferralDtos.CustomerRef(c.getId(), c.getFullName(), c.getPhone());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
