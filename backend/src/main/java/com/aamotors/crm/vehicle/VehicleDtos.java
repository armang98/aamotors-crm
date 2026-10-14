package com.aamotors.crm.vehicle;

import com.aamotors.crm.common.phone.E164;
import com.aamotors.crm.customer.Language;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class VehicleDtos {
    private VehicleDtos() {}

    public enum MaintenanceStatus { OK, DUE_SOON, OVERDUE, UNKNOWN }

    public record Maintenance(MaintenanceStatus status, Integer kmRemaining, Long daysRemaining) {}

    public record VehicleView(
            UUID id, UUID customerId, String customerName, String customerPhone,
            String brand, String model, String vin, String licensePlate,
            int currentMileageKm, Integer nextOilChangeMileageKm, LocalDate nextServiceDate,
            LocalDate lastServiceDate, long serviceRecordCount, Maintenance maintenance,
            Instant createdAt, Instant updatedAt) {}

    public record VehicleWithWarnings(
            UUID id, UUID customerId, String customerName, String customerPhone,
            String brand, String model, String vin, String licensePlate,
            int currentMileageKm, Integer nextOilChangeMileageKm, LocalDate nextServiceDate,
            LocalDate lastServiceDate, long serviceRecordCount, Maintenance maintenance,
            Instant createdAt, Instant updatedAt, List<String> warnings) {
        static VehicleWithWarnings of(VehicleView v, List<String> warnings) {
            return new VehicleWithWarnings(v.id(), v.customerId(), v.customerName(), v.customerPhone(), v.brand(), v.model(),
                    v.vin(), v.licensePlate(), v.currentMileageKm(), v.nextOilChangeMileageKm(), v.nextServiceDate(),
                    v.lastServiceDate(), v.serviceRecordCount(), v.maintenance(), v.createdAt(), v.updatedAt(), warnings);
        }
    }

    public record NewCustomer(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @E164 String phone,
            Language preferredLanguage) {}

    public record ServiceRecordInput(
            @NotNull @PastOrPresent LocalDate serviceDate,
            @NotNull @Min(value = 0, message = "mileage.negative") Integer mileageKm,
            @Min(value = 0, message = "mileage.negative") Integer nextOilChangeMileageKm,
            LocalDate nextServiceDate,
            @Size(max = 120) String oilName,
            OilType oilType,
            @Size(max = 120) String oilFilterName,
            @Size(max = 60) String oilFilterCode,
            @Size(max = 120) String cabinFilterName,
            @Size(max = 60) String cabinFilterCode,
            @Size(max = 120) String airFilterName,
            @Size(max = 60) String airFilterCode,
            @Size(max = 8000) String description) {}

    public record CreateVehicleRequest(
            UUID customerId,
            @Valid NewCustomer customer,
            @NotBlank @Size(max = 60) String brand,
            @NotBlank @Size(max = 60) String model,
            @Size(max = 20) String vin,
            @NotBlank(message = "licensePlate.required") @Size(max = 20) String licensePlate,
            @NotNull @Min(value = 0, message = "mileage.negative") Integer currentMileageKm,
            @Min(value = 0, message = "mileage.negative") Integer nextOilChangeMileageKm,
            LocalDate nextServiceDate,
            @Valid ServiceRecordInput initialService) {}

    public record UpdateVehicleRequest(
            @NotNull UUID customerId,
            @NotBlank @Size(max = 60) String brand,
            @NotBlank @Size(max = 60) String model,
            @Size(max = 20) String vin,
            @NotBlank(message = "licensePlate.required") @Size(max = 20) String licensePlate,
            @NotNull @Min(value = 0, message = "mileage.negative") Integer currentMileageKm,
            @Min(value = 0, message = "mileage.negative") Integer nextOilChangeMileageKm,
            LocalDate nextServiceDate) {}

    public record ServiceRecordView(
            UUID id, UUID vehicleId, LocalDate serviceDate, int mileageKm, Integer nextOilChangeMileageKm, LocalDate nextServiceDate,
            String oilName, OilType oilType,
            String oilFilterName, String oilFilterCode, String cabinFilterName, String cabinFilterCode,
            String airFilterName, String airFilterCode, String description,
            Instant createdAt, Instant updatedAt) {
        static ServiceRecordView from(VehicleServiceRecord r) {
            return new ServiceRecordView(r.getId(), r.getVehicle().getId(), r.getServiceDate(), r.getMileageKm(),
                    r.getNextOilChangeMileageKm(), r.getNextServiceDate(), r.getOilName(), r.getOilType(),
                    r.getOilFilterName(), r.getOilFilterCode(), r.getCabinFilterName(), r.getCabinFilterCode(),
                    r.getAirFilterName(), r.getAirFilterCode(), r.getDescription(), r.getCreatedAt(), r.getUpdatedAt());
        }
    }

    public record OilTypeView(OilType value, java.util.Map<String, String> labels) {}
}
