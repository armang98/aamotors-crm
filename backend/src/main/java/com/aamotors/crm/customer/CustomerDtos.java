package com.aamotors.crm.customer;

import com.aamotors.crm.common.phone.E164;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CustomerDtos {
    private CustomerDtos() {}

    public record ConsentView(ConsentChannel channel, boolean granted, Instant grantedAt, Instant revokedAt, String source) {}

    public record CustomerView(
            UUID id, String fullName, String phone, Language preferredLanguage, String notes,
            boolean marketingOptOut, List<ConsentView> consents, long vehicleCount,
            Instant createdAt, Instant updatedAt) {}

    public record CreateCustomerRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @E164 String phone,
            Language preferredLanguage,
            @Size(max = 4000) String notes) {}

    public record UpdateCustomerRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @E164 String phone,
            Language preferredLanguage,
            @Size(max = 4000) String notes,
            boolean marketingOptOut) {}

    public record ConsentInput(@NotNull ConsentChannel channel, boolean granted, @Size(max = 32) String source) {}

    public record UpdateConsentsRequest(@NotNull @Valid List<ConsentInput> consents) {}
}
