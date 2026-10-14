package com.aamotors.crm.campaign;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "campaigns")
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120) private String name;
    @Column(columnDefinition = "text") private String description;
    @Column(nullable = false, columnDefinition = "text") private String message;
    @Column(name = "image_asset_id") private UUID imageAssetId;
    @Column(name = "include_service_history", nullable = false) private boolean includeServiceHistory;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private CampaignFilters filters = CampaignFilters.empty();

    @Column(name = "scheduled_date") private LocalDate scheduledDate;
    @Column(name = "scheduled_time") private LocalTime scheduledTime;
    @Column(name = "time_zone", nullable = false, length = 64) private String timeZone = "Asia/Yerevan";
    @Column(name = "scheduled_at") private Instant scheduledAt;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private CampaignStatus status = CampaignStatus.DRAFT;
    @Column(name = "cancel_reason", length = 255) private String cancelReason;
    @Column(name = "send_id") private UUID sendId;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;

    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected Campaign() {}

    public Campaign(String name) { this.name = name; }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getMessage() { return message; }
    public UUID getImageAssetId() { return imageAssetId; }
    public boolean isIncludeServiceHistory() { return includeServiceHistory; }
    public CampaignFilters getFilters() { return filters; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public LocalTime getScheduledTime() { return scheduledTime; }
    public String getTimeZone() { return timeZone; }
    public Instant getScheduledAt() { return scheduledAt; }
    public CampaignStatus getStatus() { return status; }
    public String getCancelReason() { return cancelReason; }
    public UUID getSendId() { return sendId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setName(String v) { this.name = v; }
    public void setDescription(String v) { this.description = v; }
    public void setMessage(String v) { this.message = v; }
    public void setImageAssetId(UUID v) { this.imageAssetId = v; }
    public void setIncludeServiceHistory(boolean v) { this.includeServiceHistory = v; }
    public void setFilters(CampaignFilters f) { this.filters = f == null ? CampaignFilters.empty() : f; }
    public void setSchedule(LocalDate date, LocalTime time, String tz, Instant at) { this.scheduledDate = date; this.scheduledTime = time; this.timeZone = tz; this.scheduledAt = at; }
    public void setStatus(CampaignStatus s) { this.status = s; }
    public void setCancelReason(String r) { this.cancelReason = r; }
    public void setSendId(UUID id) { this.sendId = id; }
    public void setStartedAt(Instant at) { this.startedAt = at; }
    public void setCompletedAt(Instant at) { this.completedAt = at; }
}
