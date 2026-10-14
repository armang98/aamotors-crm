package com.aamotors.crm.vehicle;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One service visit. Rows are never overwritten by later visits; edits are audited. */
@Entity
@Table(name = "vehicle_service_records")
public class VehicleServiceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Column(name = "mileage_km", nullable = false)
    private int mileageKm;

    @Column(name = "next_oil_change_mileage_km")
    private Integer nextOilChangeMileageKm;

    @Column(name = "next_service_date")
    private LocalDate nextServiceDate;

    @Column(name = "oil_name", length = 120)
    private String oilName;

    @Enumerated(EnumType.STRING)
    @Column(name = "oil_type", length = 20)
    private OilType oilType;

    @Column(name = "oil_filter_name", length = 120)   private String oilFilterName;
    @Column(name = "oil_filter_code", length = 60)    private String oilFilterCode;
    @Column(name = "cabin_filter_name", length = 120) private String cabinFilterName;
    @Column(name = "cabin_filter_code", length = 60)  private String cabinFilterCode;
    @Column(name = "air_filter_name", length = 120)   private String airFilterName;
    @Column(name = "air_filter_code", length = 60)    private String airFilterCode;

    @Column(columnDefinition = "text")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected VehicleServiceRecord() {}

    public VehicleServiceRecord(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    public UUID getId() { return id; }
    public Vehicle getVehicle() { return vehicle; }
    public LocalDate getServiceDate() { return serviceDate; }
    public int getMileageKm() { return mileageKm; }
    public Integer getNextOilChangeMileageKm() { return nextOilChangeMileageKm; }
    public LocalDate getNextServiceDate() { return nextServiceDate; }
    public String getOilName() { return oilName; }
    public OilType getOilType() { return oilType; }
    public String getOilFilterName() { return oilFilterName; }
    public String getOilFilterCode() { return oilFilterCode; }
    public String getCabinFilterName() { return cabinFilterName; }
    public String getCabinFilterCode() { return cabinFilterCode; }
    public String getAirFilterName() { return airFilterName; }
    public String getAirFilterCode() { return airFilterCode; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setServiceDate(LocalDate d) { this.serviceDate = d; }
    public void setMileageKm(int km) { this.mileageKm = km; }
    public void setNextOilChangeMileageKm(Integer km) { this.nextOilChangeMileageKm = km; }
    public void setNextServiceDate(LocalDate d) { this.nextServiceDate = d; }
    public void setOilName(String v) { this.oilName = v; }
    public void setOilType(OilType v) { this.oilType = v; }
    public void setOilFilterName(String v) { this.oilFilterName = v; }
    public void setOilFilterCode(String v) { this.oilFilterCode = v; }
    public void setCabinFilterName(String v) { this.cabinFilterName = v; }
    public void setCabinFilterCode(String v) { this.cabinFilterCode = v; }
    public void setAirFilterName(String v) { this.airFilterName = v; }
    public void setAirFilterCode(String v) { this.airFilterCode = v; }
    public void setDescription(String v) { this.description = v; }
}
