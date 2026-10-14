package com.aamotors.crm.campaign;

import com.aamotors.crm.messaging.channel.Channel;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "campaign_recipients")
public class CampaignRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "campaign_id", nullable = false) private UUID campaignId;
    @Column(name = "customer_id", nullable = false) private UUID customerId;
    @Column(name = "vehicle_id") private UUID vehicleId;
    @Column(nullable = false) private boolean eligible = true;
    @Column(length = 64) private String reason;
    @Enumerated(EnumType.STRING) @Column(name = "first_channel", length = 20) private Channel firstChannel;
    @Column(name = "delivery_id") private UUID deliveryId;
    @Column(name = "dispatched_at") private Instant dispatchedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected CampaignRecipient() {}

    public CampaignRecipient(UUID campaignId, UUID customerId, UUID vehicleId, boolean eligible, String reason, Channel firstChannel, Instant now) {
        this.campaignId = campaignId;
        this.customerId = customerId;
        this.vehicleId = vehicleId;
        this.eligible = eligible;
        this.reason = reason;
        this.firstChannel = firstChannel;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public UUID getCampaignId() { return campaignId; }
    public UUID getCustomerId() { return customerId; }
    public UUID getVehicleId() { return vehicleId; }
    public boolean isEligible() { return eligible; }
    public String getReason() { return reason; }
    public Channel getFirstChannel() { return firstChannel; }
    public UUID getDeliveryId() { return deliveryId; }
    public Instant getDispatchedAt() { return dispatchedAt; }

    public void dispatched(UUID deliveryId, Instant at) { this.deliveryId = deliveryId; this.dispatchedAt = at; }
    public void skipped(String reason, Instant at) { this.eligible = false; this.reason = reason; this.dispatchedAt = at; }
}
