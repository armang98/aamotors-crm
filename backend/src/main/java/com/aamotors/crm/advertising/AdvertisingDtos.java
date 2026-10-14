package com.aamotors.crm.advertising;

import com.aamotors.crm.messaging.channel.Channel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AdvertisingDtos {
    private AdvertisingDtos() {}

    public record ComposeRequest(
            @NotEmpty List<UUID> customerIds,
            @NotBlank @Size(max = 4000) String message,
            UUID imageId,
            boolean includeServiceHistory,
            Map<UUID, UUID> vehicleSelections,
            boolean confirm) {}

    public record ChannelEligibility(Channel channel, boolean eligible, String reason, String note) {}
    public record VehicleOption(UUID id, String label) {}

    public record RecipientPreview(UUID customerId, String fullName, String phone, boolean needsVehicleSelection,
                                   List<VehicleOption> vehicles, String renderedMessage, int charCount,
                                   List<ChannelEligibility> channels, Channel firstChannel, boolean skipped, String skipReason) {}

    public record Preview(List<RecipientPreview> recipients, int eligibleCount, int skippedCount) {}

    public record AttemptView(Channel channel, int attemptNo, String outcome, String reason, String providerMessageId, String error,
                              Instant startedAt, Instant finishedAt) {}

    public record DeliveryView(UUID id, UUID sendId, UUID customerId, String customerName, String phone, MessageDelivery.Status status,
                               Channel currentChannel, List<Channel> channelsTried, String skipReason, String renderedMessage,
                               List<AttemptView> attempts, String providerMessageId, Instant deliveredAt, String resolvedBy,
                               String resolutionNote, Instant updatedAt) {}

    public record Counts(long total, long pending, long accepted, long delivered, long failed, long skipped, long uncertain) {}

    public record SendView(UUID id, String message, String imageUrl, boolean includeServiceHistory, AdvertisingSend.Status status,
                           Counts counts, UUID campaignId, Instant createdAt, List<DeliveryView> deliveries) {}

    public record ResolveRequest(@NotBlank String outcome, @Size(max = 255) String note, boolean continueFallback) {}

    public record ChannelStatus(Channel channel, boolean configured, boolean enabled, boolean mock, String note) {}
}
