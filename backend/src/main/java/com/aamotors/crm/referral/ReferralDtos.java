package com.aamotors.crm.referral;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ReferralDtos {
    private ReferralDtos() {}

    public record CustomerRef(UUID id, String fullName, String phone) {}

    public record RewardView(RewardStatus status, Instant issuedAt, Instant redeemedAt, UUID redeemedServiceOrderId,
                             String redemptionNotes, String voidReason) {}

    public record ReferralView(UUID id, CustomerRef referrer, CustomerRef invited, ReferralStatus status, int discountPercent,
                               UUID qualifyingServiceOrderId, Instant qualifiedAt, String cancelReason, RewardView reward,
                               String notes, Instant createdAt, Instant updatedAt) {}

    public record CreateReferralRequest(@NotNull UUID referrerCustomerId, @NotNull UUID invitedCustomerId, @Size(max = 2000) String notes) {}

    public record ConfirmRequest(UUID qualifyingServiceOrderId, LocalDate qualifiedAt) {}

    public record RedeemRequest(UUID serviceOrderId, @Size(max = 255) String notes) {}

    public record CancelRequest(@NotBlank @Size(max = 255) String reason) {}

    public record Summary(long totalReferrals, long successfulReferrals, long availableDiscounts, long redeemedDiscounts, int discountPercent) {}

    public record Point(String period, long total, long confirmed, long issued, long redeemed) {}

    public record Analytics(String granularity, int year, Integer month, List<Point> points) {}

    public record CustomerRewards(List<ReferralView> available, List<ReferralView> redeemed) {}
}
