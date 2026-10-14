package com.aamotors.crm.vehicle;

import com.aamotors.crm.customer.Customer;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, length = 60)
    private String brand;

    @Column(nullable = false, length = 60)
    private String model;

    @Column(length = 17, unique = true)
    private String vin;

    @Column(name = "license_plate", nullable = false, length = 20)
    private String licensePlate;

    @Column(name = "license_plate_normalized", nullable = false, length = 20)
    private String licensePlateNormalized;

    @Column(name = "current_mileage_km", nullable = false)
    private int currentMileageKm;

    @Column(name = "next_oil_change_mileage_km")
    private Integer nextOilChangeMileageKm;

    @Column(name = "next_service_date")
    private LocalDate nextServiceDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Vehicle() {}

    public Vehicle(Customer customer, String brand, String model, String vin, String licensePlate, int currentMileageKm) {
        this.customer = customer;
        this.brand = brand;
        this.model = model;
        this.vin = vin;
        setLicensePlate(licensePlate);
        this.currentMileageKm = currentMileageKm;
    }

    public UUID getId() { return id; }
    public Customer getCustomer() { return customer; }
    public String getBrand() { return brand; }
    public String getModel() { return model; }
    public String getVin() { return vin; }
    public String getLicensePlate() { return licensePlate; }
    public int getCurrentMileageKm() { return currentMileageKm; }
    public Integer getNextOilChangeMileageKm() { return nextOilChangeMileageKm; }
    public LocalDate getNextServiceDate() { return nextServiceDate; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setCustomer(Customer customer) { this.customer = customer; }
    public void setBrand(String brand) { this.brand = brand; }
    public void setModel(String model) { this.model = model; }
    public void setVin(String vin) { this.vin = vin; }
    public void setLicensePlate(String plate) {
        this.licensePlate = plate.trim();
        this.licensePlateNormalized = Vin.normalizePlate(plate);
    }
    public void setCurrentMileageKm(int km) { this.currentMileageKm = km; }
    public void setNextOilChangeMileageKm(Integer km) { this.nextOilChangeMileageKm = km; }
    public void setNextServiceDate(LocalDate d) { this.nextServiceDate = d; }
}
