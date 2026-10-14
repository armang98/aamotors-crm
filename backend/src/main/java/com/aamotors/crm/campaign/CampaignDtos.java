package com.aamotors.crm.campaign;

import com.aamotors.crm.advertising.MessageDelivery;
import com.aamotors.crm.messaging.channel.Channel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CampaignDtos {
    private CampaignDtos() {}

    public record Stats(long selected, long eligible, long attempted, long accepted, long delivered, long failed, long pending,
                        long skipped, long uncertain, Map<Channel, Long> byChannel, long estimatedCostAmd, Long actualCostAmd) {}

    public record CampaignView(UUID id, String name, String description, String message, UUID imageId, String imageUrl,
                               boolean includeServiceHistory, CampaignFilters filters, LocalDate scheduledDate, LocalTime scheduledTime,
                               String timeZone, Instant scheduledAt, CampaignStatus status, String cancelReason, long recipientCount,
                               long eligibleCount, Stats stats, UUID sendId, Instant startedAt, Instant completedAt, Instant createdAt, Instant updatedAt) {}

    public record UpsertRequest(@NotBlank @Size(max = 120) String name, @Size(max = 4000) String description,
                                @NotBlank @Size(max = 4000) String message, UUID imageId, boolean includeServiceHistory,
                                CampaignFilters filters, LocalDate scheduledDate, LocalTime scheduledTime, @Size(max = 64) String timeZone) {}

    public record ScheduleRequest(@NotNull LocalDate scheduledDate, @NotNull LocalTime scheduledTime, @Size(max = 64) String timeZone) {}

    public record CancelRequest(@Size(max = 255) String reason) {}

    public record SegmentPreviewRequest(CampaignFilters filters) {}
    public record SegmentMember(UUID customerId, String fullName, String phone, UUID vehicleId, String vehicleLabel, Channel firstChannel, String reason) {}
    public record SegmentPreview(long total, long eligible, List<SegmentMember> sample, Map<Channel, Long> byChannel) {}

    public record RecipientView(UUID customerId, String fullName, String phone, UUID vehicleId, String vehicleLabel, boolean eligible,
                                String reason, Channel firstChannel, UUID deliveryId, MessageDelivery.Status deliveryStatus, Channel deliveredVia, Instant dispatchedAt) {}

    public record Results(Stats stats, Page<com.aamotors.crm.advertising.AdvertisingDtos.DeliveryView> deliveries) {}
}
