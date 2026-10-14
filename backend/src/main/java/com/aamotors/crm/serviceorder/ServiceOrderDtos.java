package com.aamotors.crm.serviceorder;

import com.aamotors.crm.customer.Language;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class ServiceOrderDtos {
    private ServiceOrderDtos() {}

    public record VehicleRef(UUID id, String brand, String model, String licensePlate, String vin) {}
    public record CustomerRef(UUID id, String fullName, String phone, Language preferredLanguage) {}
    public record ReadyNotificationSummary(String status, int attempts, Instant lastAttemptAt, String providerMessageId, UUID notificationId) {}

    public record ServiceOrderView(
            UUID id, String orderNumber, VehicleRef vehicle, CustomerRef customer, ServiceOrderStatus status,
            String description, BigDecimal totalAmount, String currency, Instant paidAt, Instant receivedAt,
            Instant readyAt, Instant deliveredAt, Instant cancelledAt, String cancelReason, boolean referralDiscountApplied,
            ReadyNotificationSummary readyNotification, Instant createdAt, Instant updatedAt) {}

    public record CreateRequest(@NotNull UUID vehicleId, @Size(max = 4000) String description,
                                @DecimalMin(value = "0", message = "amount.negative") BigDecimal totalAmount) {}

    public record UpdateRequest(@Size(max = 4000) String description,
                                @DecimalMin(value = "0", message = "amount.negative") BigDecimal totalAmount) {}

    public record StatusRequest(@NotNull ServiceOrderStatus status, @Size(max = 255) String reason) {}

    public record MarkPaidRequest(LocalDate paidAt, @DecimalMin(value = "0", message = "amount.negative") BigDecimal totalAmount) {}

    public record SmsPreview(String language, String text, String recipient, int charCount, int segments, boolean unicode) {}
}
