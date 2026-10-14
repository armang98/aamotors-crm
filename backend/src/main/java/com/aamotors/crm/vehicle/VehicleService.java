package com.aamotors.crm.vehicle;

import com.aamotors.crm.audit.AuditService;
import com.aamotors.crm.common.error.ApiException;
import com.aamotors.crm.common.phone.PhoneNumberNormalizer;
import com.aamotors.crm.customer.Customer;
import com.aamotors.crm.customer.CustomerService;
import jakarta.annotation.PostConstruct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
public class VehicleService {

    public static final String WARN_NEXT_OIL_NOT_AHEAD = "vehicle.nextOilChangeNotAhead";

    private final VehicleRepository vehicles;
    private final VehicleServiceRecordRepository records;
    private final CustomerService customerService;
    private final AuditService audit;
    private final Clock clock;

    public VehicleService(VehicleRepository vehicles, VehicleServiceRecordRepository records,
                          CustomerService customerService, AuditService audit, Clock clock) {
        this.vehicles = vehicles;
        this.records = records;
        this.customerService = customerService;
        this.audit = audit;
        this.clock = clock;
    }

    @PostConstruct
    void wireCustomerModule() {
        customerService.registerVehicleCounter(vehicles::countByCustomerId);
    }

    // ---------- queries ----------

    @Transactional(readOnly = true)
    public Page<VehicleDtos.VehicleView> search(String q, Pageable pageable) {
        String query = q == null ? "" : q.trim();
        Page<Vehicle> page;
        if (query.isEmpty()) {
            page = vehicles.findAllWithCustomer(pageable);
        } else {
            String phone = PhoneNumberNormalizer.searchFragment(query).orElse("#"); // "#" never matches an E.164 value
            page = vehicles.search(query.toLowerCase(), Vin.normalizePlate(query), query.toUpperCase().trim(), phone, pageable);
        }
        return toViews(page);
    }

    @Transactional(readOnly = true)
    public VehicleDtos.VehicleView get(UUID id) {
        return toView(require(id));
    }

    @Transactional(readOnly = true)
    public List<VehicleDtos.VehicleView> forCustomer(UUID customerId) {
        return vehicles.findByCustomerIdOrderByCreatedAtAsc(customerId).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<VehicleDtos.VehicleView> dueForMaintenance(int kmWindow, int daysWindow) {
        LocalDate today = today();
        return vehicles.findDue(kmWindow, today.plusDays(daysWindow)).stream().map(v -> toView(v, today)).toList();
    }

    // ---------- commands ----------

    @Transactional
    public VehicleDtos.VehicleWithWarnings create(VehicleDtos.CreateVehicleRequest req) {
        if ((req.customerId() == null) == (req.customer() == null)) {
            throw ApiException.badRequest("validation.failed", "Provide exactly one of customerId or customer");
        }
        Customer customer = req.customerId() != null
                ? customerService.require(req.customerId())
                : customerService.findOrCreate(req.customer().fullName(), req.customer().phone(), req.customer().preferredLanguage());

        String vin = normalizeAndCheckVin(req.vin(), null);
        Vehicle v = new Vehicle(customer, req.brand().trim(), req.model().trim(), vin, req.licensePlate(), req.currentMileageKm());
        v.setNextOilChangeMileageKm(req.nextOilChangeMileageKm());
        v.setNextServiceDate(req.nextServiceDate());
        vehicles.save(v);

        List<String> warnings = new ArrayList<>();
        if (req.initialService() != null) {
            VehicleServiceRecord r = applyInput(new VehicleServiceRecord(v), req.initialService());
            records.save(r);
            syncVehicleFromRecord(v, r);
            warnings.addAll(warningsFor(r.getMileageKm(), r.getNextOilChangeMileageKm()));
        } else {
            warnings.addAll(warningsFor(v.getCurrentMileageKm(), v.getNextOilChangeMileageKm()));
        }
        audit.record("VEHICLE_CREATED", "vehicle", v.getId().toString(), snapshot(v));
        return VehicleDtos.VehicleWithWarnings.of(toView(v), warnings);
    }

    @Transactional
    public VehicleDtos.VehicleWithWarnings update(UUID id, VehicleDtos.UpdateVehicleRequest req) {
        Vehicle v = require(id);
        Map<String, Object> before = snapshot(v);
        v.setCustomer(customerService.require(req.customerId()));
        v.setBrand(req.brand().trim());
        v.setModel(req.model().trim());
        v.setVin(normalizeAndCheckVin(req.vin(), id));
        v.setLicensePlate(req.licensePlate());
        v.setCurrentMileageKm(req.currentMileageKm());
        v.setNextOilChangeMileageKm(req.nextOilChangeMileageKm());
        v.setNextServiceDate(req.nextServiceDate());
        audit.record("VEHICLE_UPDATED", "vehicle", id.toString(), Map.of("before", before, "after", snapshot(v)));
        return VehicleDtos.VehicleWithWarnings.of(toView(v), warningsFor(v.getCurrentMileageKm(), v.getNextOilChangeMileageKm()));
    }

    @Transactional
    public void delete(UUID id) {
        Vehicle v = require(id);
        long count = records.countByVehicleId(id);
        vehicles.delete(v);
        Map<String, Object> details = new LinkedHashMap<>(snapshot(v));
        details.put("deletedServiceRecords", count);
        audit.record("VEHICLE_DELETED", "vehicle", id.toString(), details);
    }

    // ---------- service records ----------

    @Transactional(readOnly = true)
    public List<VehicleDtos.ServiceRecordView> history(UUID vehicleId) {
        require(vehicleId);
        return records.findByVehicleIdOrderByServiceDateDescCreatedAtDesc(vehicleId).stream()
                .map(VehicleDtos.ServiceRecordView::from).toList();
    }

    @Transactional(readOnly = true)
    public Optional<VehicleDtos.ServiceRecordView> latest(UUID vehicleId) {
        require(vehicleId);
        return records.findFirstByVehicleIdOrderByServiceDateDescCreatedAtDesc(vehicleId).map(VehicleDtos.ServiceRecordView::from);
    }

    public record Created(VehicleDtos.ServiceRecordView record, List<String> warnings) {}

    @Transactional
    public Created addServiceRecord(UUID vehicleId, VehicleDtos.ServiceRecordInput in) {
        Vehicle v = require(vehicleId);
        VehicleServiceRecord r = records.save(applyInput(new VehicleServiceRecord(v), in));
        if (isNewest(v, r)) {
            syncVehicleFromRecord(v, r);
        }
        audit.record("SERVICE_RECORD_CREATED", "service_record", r.getId().toString(), recordSnapshot(r));
        return new Created(VehicleDtos.ServiceRecordView.from(r), warningsFor(r.getMileageKm(), r.getNextOilChangeMileageKm()));
    }

    @Transactional
    public Created updateServiceRecord(UUID recordId, VehicleDtos.ServiceRecordInput in) {
        VehicleServiceRecord r = records.findById(recordId).orElseThrow(() -> ApiException.notFound("Service record not found"));
        Map<String, Object> before = recordSnapshot(r);
        applyInput(r, in);
        Vehicle v = r.getVehicle();
        if (isNewest(v, r)) {
            syncVehicleFromRecord(v, r);
        }
        audit.record("SERVICE_RECORD_UPDATED", "service_record", recordId.toString(), Map.of("before", before, "after", recordSnapshot(r)));
        return new Created(VehicleDtos.ServiceRecordView.from(r), warningsFor(r.getMileageKm(), r.getNextOilChangeMileageKm()));
    }

    @Transactional
    public void deleteServiceRecord(UUID recordId) {
        VehicleServiceRecord r = records.findById(recordId).orElseThrow(() -> ApiException.notFound("Service record not found"));
        Map<String, Object> snap = recordSnapshot(r);
        records.delete(r);
        audit.record("SERVICE_RECORD_DELETED", "service_record", recordId.toString(), snap);
    }

    public List<VehicleDtos.OilTypeView> oilTypes() {
        return Arrays.stream(OilType.values()).map(t -> new VehicleDtos.OilTypeView(t, t.labels())).toList();
    }

    // ---------- helpers ----------

    Vehicle require(UUID id) {
        return vehicles.findById(id).orElseThrow(() -> ApiException.notFound("Vehicle not found"));
    }

    private String normalizeAndCheckVin(String raw, UUID selfId) {
        Optional<String> normalized = Vin.normalize(raw);
        if (normalized.isEmpty()) return null;
        String vin = normalized.get();
        if (!Vin.isValid(vin)) {
            throw ApiException.fieldError("vin", "vin.invalid", "VIN must be 17 characters (A-Z, 0-9, no I/O/Q)");
        }
        vehicles.findByVin(vin).filter(other -> !other.getId().equals(selfId)).ifPresent(other -> {
            throw ApiException.conflict("vehicle.vinExists", "A vehicle with this VIN already exists");
        });
        return vin;
    }

    private static VehicleServiceRecord applyInput(VehicleServiceRecord r, VehicleDtos.ServiceRecordInput in) {
        r.setServiceDate(in.serviceDate());
        r.setMileageKm(in.mileageKm());
        r.setNextOilChangeMileageKm(in.nextOilChangeMileageKm());
        r.setNextServiceDate(in.nextServiceDate());
        r.setOilName(clean(in.oilName()));
        r.setOilType(in.oilType());
        r.setOilFilterName(clean(in.oilFilterName()));
        r.setOilFilterCode(clean(in.oilFilterCode()));
        r.setCabinFilterName(clean(in.cabinFilterName()));
        r.setCabinFilterCode(clean(in.cabinFilterCode()));
        r.setAirFilterName(clean(in.airFilterName()));
        r.setAirFilterCode(clean(in.airFilterCode()));
        r.setDescription(clean(in.description()));
        return r;
    }

    private boolean isNewest(Vehicle v, VehicleServiceRecord r) {
        return records.findFirstByVehicleIdOrderByServiceDateDescCreatedAtDesc(v.getId())
                .map(latest -> latest.getId().equals(r.getId()))
                .orElse(true);
    }

    /** The newest visit becomes the vehicle's current state. Mileage never goes backwards. */
    private static void syncVehicleFromRecord(Vehicle v, VehicleServiceRecord r) {
        v.setCurrentMileageKm(Math.max(v.getCurrentMileageKm(), r.getMileageKm()));
        if (r.getNextOilChangeMileageKm() != null) v.setNextOilChangeMileageKm(r.getNextOilChangeMileageKm());
        if (r.getNextServiceDate() != null) v.setNextServiceDate(r.getNextServiceDate());
    }

    private static List<String> warningsFor(int mileage, Integer nextOil) {
        return nextOil != null && nextOil <= mileage ? List.of(WARN_NEXT_OIL_NOT_AHEAD) : List.of();
    }

    private LocalDate today() {
        return Instant.now(clock).atZone(ZoneId.of("Asia/Yerevan")).toLocalDate();
    }

    private Page<VehicleDtos.VehicleView> toViews(Page<Vehicle> page) {
        LocalDate today = today();
        var stats = records.statsMap(page.getContent().stream().map(Vehicle::getId).toList());
        return page.map(v -> toView(v, today, stats.get(v.getId())));
    }

    private VehicleDtos.VehicleView toView(Vehicle v) {
        return toView(v, today());
    }

    private VehicleDtos.VehicleView toView(Vehicle v, LocalDate today) {
        return toView(v, today, records.statsMap(List.of(v.getId())).get(v.getId()));
    }

    private static VehicleDtos.VehicleView toView(Vehicle v, LocalDate today, VehicleServiceRecordRepository.VehicleStats stats) {
        Customer c = v.getCustomer();
        return new VehicleDtos.VehicleView(v.getId(), c.getId(), c.getFullName(), c.getPhone(),
                v.getBrand(), v.getModel(), v.getVin(), v.getLicensePlate(),
                v.getCurrentMileageKm(), v.getNextOilChangeMileageKm(), v.getNextServiceDate(),
                stats == null ? null : stats.getLastDate(), stats == null ? 0 : stats.getCnt(),
                MaintenanceCalculator.evaluate(v, today), v.getCreatedAt(), v.getUpdatedAt());
    }

    private static Map<String, Object> snapshot(Vehicle v) {
        var m = new LinkedHashMap<String, Object>();
        m.put("brand", v.getBrand());
        m.put("model", v.getModel());
        m.put("vin", v.getVin());
        m.put("licensePlate", v.getLicensePlate());
        m.put("currentMileageKm", v.getCurrentMileageKm());
        m.put("nextOilChangeMileageKm", v.getNextOilChangeMileageKm());
        m.put("nextServiceDate", v.getNextServiceDate() == null ? null : v.getNextServiceDate().toString());
        m.put("customerId", v.getCustomer().getId().toString());
        return m;
    }

    private static Map<String, Object> recordSnapshot(VehicleServiceRecord r) {
        var m = new LinkedHashMap<String, Object>();
        m.put("vehicleId", r.getVehicle().getId().toString());
        m.put("serviceDate", r.getServiceDate().toString());
        m.put("mileageKm", r.getMileageKm());
        m.put("nextOilChangeMileageKm", r.getNextOilChangeMileageKm());
        m.put("oilName", r.getOilName());
        m.put("oilType", r.getOilType() == null ? null : r.getOilType().name());
        m.put("oilFilter", r.getOilFilterName() + " / " + r.getOilFilterCode());
        m.put("cabinFilter", r.getCabinFilterName() + " / " + r.getCabinFilterCode());
        m.put("airFilter", r.getAirFilterName() + " / " + r.getAirFilterCode());
        m.put("description", r.getDescription());
        return m;
    }

    private static String clean(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
