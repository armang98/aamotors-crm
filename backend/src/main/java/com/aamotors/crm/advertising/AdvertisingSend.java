package com.aamotors.crm.advertising;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "advertising_sends")
public class AdvertisingSend {

    public enum Status { QUEUED, RUNNING, COMPLETED, COMPLETED_WITH_FAILURES, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, columnDefinition = "text") private String message;
    @Column(name = "image_asset_id") private UUID imageAssetId;
    @Column(name = "include_service_history", nullable = false) private boolean includeServiceHistory;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private Status status = Status.QUEUED;
    @Column(name = "campaign_id") private UUID campaignId;
    @Column(name = "created_by", length = 64) private String createdBy;

    @OneToMany(mappedBy = "send", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MessageDelivery> deliveries = new ArrayList<>();

    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected AdvertisingSend() {}

    public AdvertisingSend(String message, UUID imageAssetId, boolean includeServiceHistory, UUID campaignId, String createdBy) {
        this.message = message;
        this.imageAssetId = imageAssetId;
        this.includeServiceHistory = includeServiceHistory;
        this.campaignId = campaignId;
        this.createdBy = createdBy;
    }

    public UUID getId() { return id; }
    public String getMessage() { return message; }
    public UUID getImageAssetId() { return imageAssetId; }
    public boolean isIncludeServiceHistory() { return includeServiceHistory; }
    public Status getStatus() { return status; }
    public UUID getCampaignId() { return campaignId; }
    public String getCreatedBy() { return createdBy; }
    public List<MessageDelivery> getDeliveries() { return deliveries; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(Status s) { this.status = s; }

    /** Derives the envelope status from its deliveries. */
    public void recompute() {
        boolean anyOpen = deliveries.stream().anyMatch(d -> d.getStatus() == MessageDelivery.Status.PENDING || d.getStatus() == MessageDelivery.Status.ATTEMPTING);
        boolean anyFailed = deliveries.stream().anyMatch(d -> d.getStatus() == MessageDelivery.Status.FAILED || d.getStatus() == MessageDelivery.Status.UNCERTAIN);
        if (status == Status.CANCELLED) return;
        if (anyOpen) status = Status.RUNNING;
        else status = anyFailed ? Status.COMPLETED_WITH_FAILURES : Status.COMPLETED;
    }
}
