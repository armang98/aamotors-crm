package com.aamotors.crm.serviceorder;

import com.aamotors.crm.customer.Customer;
import com.aamotors.crm.vehicle.Vehicle;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_orders")
public class ServiceOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_number", nullable = false, unique = true, length = 20)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ServiceOrderStatus status = ServiceOrderStatus.RECEIVED;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "total_amount", precision = 12, scale = 0)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency = "AMD";

    @Column(name = "paid_at")        private Instant paidAt;
    @Column(name = "received_at", nullable = false) private Instant receivedAt;
    @Column(name = "ready_at")       private Instant readyAt;
    @Column(name = "delivered_at")   private Instant deliveredAt;
    @Column(name = "cancelled_at")   private Instant cancelledAt;
    @Column(name = "cancel_reason", length = 255) private String cancelReason;

    @Column(name = "referral_discount_applied", nullable = false)
    private boolean referralDiscountApplied;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected ServiceOrder() {}

    public ServiceOrder(String orderNumber, Vehicle vehicle, Customer customer, Instant receivedAt) {
        this.orderNumber = orderNumber;
        this.vehicle = vehicle;
        this.customer = customer;
        this.receivedAt = receivedAt;
    }

    public UUID getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public Vehicle getVehicle() { return vehicle; }
    public Customer getCustomer() { return customer; }
    public ServiceOrderStatus getStatus() { return status; }
    public String getDescription() { return description; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getReadyAt() { return readyAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public String getCancelReason() { return cancelReason; }
    public boolean isReferralDiscountApplied() { return referralDiscountApplied; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setDescription(String d) { this.description = d; }
    public void setTotalAmount(BigDecimal a) { this.totalAmount = a; }
    public void setPaidAt(Instant at) { this.paidAt = at; }
    public void setReferralDiscountApplied(boolean v) { this.referralDiscountApplied = v; }

    public boolean isPaid() { return paidAt != null; }

    void transition(ServiceOrderStatus next, Instant now, String reason) {
        this.status = next;
        switch (next) {
            case READY_FOR_PICKUP -> this.readyAt = now;
            case DELIVERED -> this.deliveredAt = now;
            case CANCELLED -> { this.cancelledAt = now; this.cancelReason = reason; }
            default -> {}
        }
    }
}
