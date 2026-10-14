package com.aamotors.crm.vehicle;

import com.aamotors.crm.common.error.ApiException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@Tag(name = "Vehicles and service history")
public class VehicleController {

    private final VehicleService service;

    public VehicleController(VehicleService service) {
        this.service = service;
    }

    // ----- vehicles -----

    @GetMapping("/vehicles")
    public Page<VehicleDtos.VehicleView> search(@RequestParam(required = false) String q,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(defaultValue = "updatedAt,desc") String sort) {
        String[] parts = sort.split(",");
        String property = switch (parts[0]) {
            case "brand", "model", "licensePlate", "currentMileageKm", "nextOilChangeMileageKm", "createdAt", "updatedAt" -> parts[0];
            default -> "updatedAt";
        };
        Sort.Direction dir = parts.length > 1 && parts[1].equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        return service.search(q, PageRequest.of(page, Math.min(Math.max(size, 1), 100), Sort.by(dir, property)));
    }

    @GetMapping("/vehicles/reminders")
    public List<VehicleDtos.VehicleView> reminders(@RequestParam(defaultValue = "1000") int kmWindow,
                                                   @RequestParam(defaultValue = "14") int daysWindow) {
        return service.dueForMaintenance(Math.max(kmWindow, 0), Math.max(daysWindow, 0));
    }

    @GetMapping("/vehicles/{id}")
    public VehicleDtos.VehicleView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/customers/{customerId}/vehicles")
    public List<VehicleDtos.VehicleView> forCustomer(@PathVariable UUID customerId) {
        return service.forCustomer(customerId);
    }

    @PostMapping("/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleDtos.VehicleWithWarnings create(@Valid @RequestBody VehicleDtos.CreateVehicleRequest req) {
        return service.create(req);
    }

    @PutMapping("/vehicles/{id}")
    public VehicleDtos.VehicleWithWarnings update(@PathVariable UUID id, @Valid @RequestBody VehicleDtos.UpdateVehicleRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/vehicles/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ----- service records -----

    @GetMapping("/vehicles/{id}/service-records")
    public List<VehicleDtos.ServiceRecordView> history(@PathVariable UUID id) {
        return service.history(id);
    }

    @GetMapping("/vehicles/{id}/service-records/latest")
    public VehicleDtos.ServiceRecordView latest(@PathVariable UUID id) {
        return service.latest(id).orElseThrow(() -> ApiException.notFound("No service history"));
    }

    @PostMapping("/vehicles/{id}/service-records")
    public ResponseEntity<VehicleDtos.ServiceRecordView> add(@PathVariable UUID id, @Valid @RequestBody VehicleDtos.ServiceRecordInput in) {
        var created = service.addServiceRecord(id, in);
        return withWarnings(ResponseEntity.status(HttpStatus.CREATED), created.warnings()).body(created.record());
    }

    @PutMapping("/service-records/{id}")
    public ResponseEntity<VehicleDtos.ServiceRecordView> updateRecord(@PathVariable UUID id, @Valid @RequestBody VehicleDtos.ServiceRecordInput in) {
        var updated = service.updateServiceRecord(id, in);
        return withWarnings(ResponseEntity.ok(), updated.warnings()).body(updated.record());
    }

    @DeleteMapping("/service-records/{id}")
    public ResponseEntity<Void> deleteRecord(@PathVariable UUID id) {
        service.deleteServiceRecord(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/service-records/oil-types")
    public List<VehicleDtos.OilTypeView> oilTypes() {
        return service.oilTypes();
    }

    private static ResponseEntity.BodyBuilder withWarnings(ResponseEntity.BodyBuilder b, List<String> warnings) {
        return warnings.isEmpty() ? b : b.header("X-Warnings", String.join(",", warnings));
    }
}
