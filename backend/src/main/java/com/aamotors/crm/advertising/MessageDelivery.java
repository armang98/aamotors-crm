package com.aamotors.crm.advertising;

import com.aamotors.crm.messaging.channel.Channel;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One recipient of one send. Walks the channel list in order; see {@link FallbackOrchestrator}. */
@Entity
@Table(name = "message_deliveries")
public class MessageDelivery {

    public enum Status { PENDING, ATTEMPTING, ACCEPTED, DELIVERED, FAILED, UNCERTAIN, SKIPPED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "send_id", nullable = false)
    private AdvertisingSend send;

    @Column(name = "customer_id", nullable = false) private UUID customerId;
    @Column(name = "vehicle_id") private UUID vehicleId;
    @Column(nullable = false, length = 20) private String phone;
    @Column(name = "rendered_message", nullable = false, columnDefinition = "text") private String renderedMessage;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status = Status.PENDING;
    @Enumerated(EnumType.STRING) @Column(name = "current_channel", length = 20) private Channel currentChannel;
    @Column(name = "channel_index", nullable = false) private int channelIndex;
    @Column(name = "skip_reason", length = 64) private String skipReason;
    @Column(name = "provider_message_id", length = 128) private String providerMessageId;
    @Column(name = "delivered_at") private Instant deliveredAt;
    @Column(name = "resolved_by", length = 64) private String resolvedBy;
    @Column(name = "resolution_note", length = 255) private String resolutionNote;

    @OneToMany(mappedBy = "delivery", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("attemptNo asc")
    private List<MessageAttempt> attempts = new ArrayList<>();

    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected MessageDelivery() {}

    public MessageDelivery(AdvertisingSend send, UUID customerId, UUID vehicleId, String phone, String renderedMessage) {
        this.send = send;
        this.customerId = customerId;
        this.vehicleId = vehicleId;
        this.phone = phone;
        this.renderedMessage = renderedMessage;
    }

    public UUID getId() { return id; }
    public AdvertisingSend getSend() { return send; }
    public UUID getCustomerId() { return customerId; }
    public UUID getVehicleId() { return vehicleId; }
    public String getPhone() { return phone; }
    public String getRenderedMessage() { return renderedMessage; }
    public Status getStatus() { return status; }
    public Channel getCurrentChannel() { return currentChannel; }
    public int getChannelIndex() { return channelIndex; }
    public String getSkipReason() { return skipReason; }
    public String getProviderMessageId() { return providerMessageId; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public String getResolvedBy() { return resolvedBy; }
    public String getResolutionNote() { return resolutionNote; }
    public List<MessageAttempt> getAttempts() { return attempts; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(Status s) { this.status = s; }
    public void setCurrentChannel(Channel c) { this.currentChannel = c; }
    public void setChannelIndex(int i) { this.channelIndex = i; }
    public void setSkipReason(String r) { this.skipReason = r; }
    public void markAccepted(Channel c, String providerMessageId) { this.status = Status.ACCEPTED; this.currentChannel = c; this.providerMessageId = providerMessageId; }
    public void markDelivered(Instant at) { this.status = Status.DELIVERED; this.deliveredAt = at; }
    public void resolve(Status s, String by, String note) { this.status = s; this.resolvedBy = by; this.resolutionNote = note; }

    public MessageAttempt newAttempt(Channel channel, Instant now) {
        MessageAttempt a = new MessageAttempt(this, channel, attempts.size() + 1, id + ":" + channel + ":" + (attempts.size() + 1), now);
        attempts.add(a);
        return a;
    }
}
