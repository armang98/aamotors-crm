package com.aamotors.crm.campaign;

import com.aamotors.crm.advertising.AdvertisingDtos;
import com.aamotors.crm.advertising.AdvertisingSend;
import com.aamotors.crm.advertising.AdvertisingSendRepository;
import com.aamotors.crm.advertising.AdvertisingService;
import com.aamotors.crm.advertising.MessageDelivery;
import com.aamotors.crm.advertising.MessageDeliveryRepository;
import com.aamotors.crm.audit.AuditService;
import com.aamotors.crm.common.error.ApiException;
import com.aamotors.crm.common.media.MediaAsset;
import com.aamotors.crm.common.media.MediaAssetRepository;
import com.aamotors.crm.customer.Customer;
import com.aamotors.crm.customer.CustomerRepository;
import com.aamotors.crm.messaging.channel.Channel;
import com.aamotors.crm.messaging.config.RabbitTopology;
import com.aamotors.crm.messaging.outbox.OutboxService;
import com.aamotors.crm.vehicle.Vehicle;
import com.aamotors.crm.vehicle.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class CampaignService {

    private static final Logger log = LoggerFactory.getLogger(CampaignService.class);

    private final CampaignRepository campaigns;
    private final CampaignRecipientRepository recipients;
    private final CampaignSegmentService segments;
    private final AdvertisingService advertising;
    private final AdvertisingSendRepository sends;
    private final MessageDeliveryRepository deliveries;
    private final CustomerRepository customers;
    private final VehicleRepository vehicles;
    private final MediaAssetRepository media;
    private final OutboxService outbox;
    private final CampaignProperties props;
    private final AuditService audit;
    private final Clock clock;

    public CampaignService(CampaignRepository campaigns, CampaignRecipientRepository recipients, CampaignSegmentService segments,
                           AdvertisingService advertising, AdvertisingSendRepository sends, MessageDeliveryRepository deliveries,
                           CustomerRepository customers, VehicleRepository vehicles, MediaAssetRepository media, OutboxService outbox,
                           CampaignProperties props, AuditService audit, Clock clock) {
        this.campaigns = campaigns;
        this.recipients = recipients;
        this.segments = segments;
        this.advertising = advertising;
        this.sends = sends;
        this.deliveries = deliveries;
        this.customers = customers;
        this.vehicles = vehicles;
        this.media = media;
        this.outbox = outbox;
        this.props = props;
        this.audit = audit;
        this.clock = clock;
    }

    // ---------- CRUD ----------

    @Transactional(readOnly = true)
    public Page<CampaignDtos.CampaignView> list(CampaignStatus status, Pageable pageable) {
        return campaigns.list(status, pageable).map(this::toView);
    }

    @Transactional(readOnly = true)
    public CampaignDtos.CampaignView get(UUID id) { return toView(require(id)); }

    @Transactional
    public CampaignDtos.CampaignView create(CampaignDtos.UpsertRequest req) {
        Campaign c = new Campaign(req.name().trim());
        apply(c, req);
        campaigns.save(c);
        audit.record("CAMPAIGN_CREATED", "campaign", c.getId().toString(), Map.of("name", c.getName()));
        return toView(c);
    }

    @Transactional
    public CampaignDtos.CampaignView update(UUID id, CampaignDtos.UpsertRequest req) {
        Campaign c = require(id);
        if (!EnumSet.of(CampaignStatus.DRAFT, CampaignStatus.SCHEDULED, CampaignStatus.PAUSED).contains(c.getStatus())) {
            throw ApiException.conflict("campaign.notEditable", "Campaign can no longer be edited");
        }
        c.setName(req.name().trim());
        apply(c, req);
        if (c.getStatus() == CampaignStatus.SCHEDULED) {
            // segment may have changed → re-materialise
            recipients.deleteByCampaignId(id);
            materialise(c);
        }
        audit.record("CAMPAIGN_UPDATED", "campaign", id.toString(), Map.of("name", c.getName()));
        return toView(c);
    }

    @Transactional
    public void delete(UUID id) {
        Campaign c = require(id);
        if (c.getStatus() != CampaignStatus.DRAFT) throw ApiException.conflict("campaign.notDraft", "Only drafts can be deleted");
        campaigns.delete(c);
        audit.record("CAMPAIGN_DELETED", "campaign", id.toString(), Map.of("name", c.getName()));
    }

    @Transactional
    public CampaignDtos.CampaignView duplicate(UUID id) {
        Campaign src = require(id);
        Campaign c = new Campaign(src.getName() + " (copy)");
        c.setDescription(src.getDescription());
        c.setMessage(src.getMessage());
        c.setImageAssetId(src.getImageAssetId());
        c.setIncludeServiceHistory(src.isIncludeServiceHistory());
        c.setFilters(src.getFilters());
        c.setSchedule(null, null, src.getTimeZone(), null);
        campaigns.save(c);
        audit.record("CAMPAIGN_DUPLICATED", "campaign", c.getId().toString(), Map.of("from", id.toString()));
        return toView(c);
    }

    // ---------- segment ----------

    @Transactional(readOnly = true)
    public CampaignDtos.SegmentPreview previewSegment(CampaignFilters filters) {
        List<CampaignSegmentService.Member> members = segments.resolve(filters);
        Map<Channel, Long> byChannel = new EnumMap<>(Channel.class);
        for (Channel ch : Channel.values()) byChannel.put(ch, 0L);
        members.stream().filter(CampaignSegmentService.Member::eligible).forEach(m -> byChannel.merge(m.firstChannel(), 1L, Long::sum));
        List<CampaignDtos.SegmentMember> sample = members.stream().limit(25).map(m -> new CampaignDtos.SegmentMember(
                m.customer().getId(), m.customer().getFullName(), m.customer().getPhone(),
                m.vehicle() == null ? null : m.vehicle().getId(), CampaignSegmentService.vehicleLabel(m.vehicle()), m.firstChannel(), m.reason())).toList();
        return new CampaignDtos.SegmentPreview(members.size(), members.stream().filter(CampaignSegmentService.Member::eligible).count(), sample, byChannel);
    }

    @Transactional(readOnly = true)
    public Page<CampaignDtos.RecipientView> recipients(UUID id, Pageable pageable) {
        require(id);
        return recipients.findByCampaignIdOrderByCreatedAt(id, pageable).map(r -> {
            Customer c = customers.findById(r.getCustomerId()).orElse(null);
            Vehicle v = r.getVehicleId() == null ? null : vehicles.findById(r.getVehicleId()).orElse(null);
            MessageDelivery d = r.getDeliveryId() == null ? null : deliveries.findById(r.getDeliveryId()).orElse(null);
            return new CampaignDtos.RecipientView(r.getCustomerId(), c == null ? null : c.getFullName(), c == null ? null : c.getPhone(),
                    r.getVehicleId(), CampaignSegmentService.vehicleLabel(v), r.isEligible(), r.getReason(), r.getFirstChannel(),
                    r.getDeliveryId(), d == null ? null : d.getStatus(), d == null ? null : d.getCurrentChannel(), r.getDispatchedAt());
        });
    }

    // ---------- lifecycle ----------

    @Transactional
    public CampaignDtos.CampaignView schedule(UUID id, CampaignDtos.ScheduleRequest req) {
        Campaign c = require(id);
        if (!EnumSet.of(CampaignStatus.DRAFT, CampaignStatus.SCHEDULED, CampaignStatus.PAUSED).contains(c.getStatus())) {
            throw ApiException.conflict("campaign.invalidTransition", "Campaign cannot be scheduled from " + c.getStatus());
        }
        String tz = req.timeZone() == null || req.timeZone().isBlank() ? c.getTimeZone() : req.timeZone();
        Instant at;
        try {
            at = ZonedDateTime.of(req.scheduledDate(), req.scheduledTime(), ZoneId.of(tz)).toInstant();
        } catch (DateTimeException e) {
            throw ApiException.badRequest("settings.invalidTimeZone", "Unknown time zone");
        }
        if (at.isBefore(Instant.now(clock))) throw ApiException.badRequest("campaign.scheduleInPast", "Scheduled time is in the past");
        c.setSchedule(req.scheduledDate(), req.scheduledTime(), tz, at);
        recipients.deleteByCampaignId(id);
        long eligible = materialise(c);
        if (eligible == 0) throw ApiException.badRequest("campaign.noRecipients", "No eligible recipients match the segment");
        c.setStatus(CampaignStatus.SCHEDULED);
        audit.record("CAMPAIGN_SCHEDULED", "campaign", id.toString(), Map.of("scheduledAt", at.toString(), "eligible", eligible));
        return toView(c);
    }

    @Transactional
    public CampaignDtos.CampaignView start(UUID id) {
        Campaign c = require(id);
        if (!EnumSet.of(CampaignStatus.DRAFT, CampaignStatus.SCHEDULED).contains(c.getStatus())) {
            throw ApiException.conflict("campaign.invalidTransition", "Campaign cannot be started from " + c.getStatus());
        }
        if (c.getStatus() == CampaignStatus.DRAFT || recipients.countByCampaignId(id) == 0) {
            recipients.deleteByCampaignId(id);
            if (materialise(c) == 0) throw ApiException.badRequest("campaign.noRecipients", "No eligible recipients match the segment");
        }
        var auth = SecurityContextHolder.getContext().getAuthentication();
        AdvertisingSend send = new AdvertisingSend(c.getMessage(), c.getImageAssetId(), c.isIncludeServiceHistory(), c.getId(), auth == null ? "scheduler" : auth.getName());
        send.setStatus(AdvertisingSend.Status.RUNNING);
        sends.save(send);
        c.setSendId(send.getId());
        c.setStartedAt(Instant.now(clock));
        c.setStatus(CampaignStatus.RUNNING);
        audit.record("CAMPAIGN_STARTED", "campaign", id.toString(), Map.of("sendId", send.getId().toString()));
        return toView(c);
    }

    @Transactional
    public CampaignDtos.CampaignView pause(UUID id) {
        Campaign c = require(id);
        if (c.getStatus() != CampaignStatus.RUNNING) throw ApiException.conflict("campaign.invalidTransition", "Only running campaigns can be paused");
        c.setStatus(CampaignStatus.PAUSED);
        audit.record("CAMPAIGN_PAUSED", "campaign", id.toString(), null);
        return toView(c);
    }

    @Transactional
    public CampaignDtos.CampaignView resume(UUID id) {
        Campaign c = require(id);
        if (c.getStatus() != CampaignStatus.PAUSED) throw ApiException.conflict("campaign.invalidTransition", "Only paused campaigns can be resumed");
        c.setStatus(c.getSendId() == null ? CampaignStatus.SCHEDULED : CampaignStatus.RUNNING);
        audit.record("CAMPAIGN_RESUMED", "campaign", id.toString(), null);
        return toView(c);
    }

    @Transactional
    public CampaignDtos.CampaignView cancel(UUID id, String reason) {
        Campaign c = require(id);
        if (!EnumSet.of(CampaignStatus.SCHEDULED, CampaignStatus.RUNNING, CampaignStatus.PAUSED).contains(c.getStatus())) {
            throw ApiException.conflict("campaign.invalidTransition", "Campaign cannot be cancelled from " + c.getStatus());
        }
        Instant now = Instant.now(clock);
        c.setStatus(CampaignStatus.CANCELLED);
        c.setCancelReason(reason);
        c.setCompletedAt(now);
        // undispatched recipients never get a delivery; already queued deliveries that are still PENDING are skipped
        recipients.pending(id, Pageable.unpaged()).forEach(r -> r.skipped("CAMPAIGN_CANCELLED", now));
        if (c.getSendId() != null) {
            sends.findById(c.getSendId()).ifPresent(s -> {
                deliveries.findBySendId(s.getId()).stream().filter(d -> d.getStatus() == MessageDelivery.Status.PENDING).forEach(d -> {
                    d.setStatus(MessageDelivery.Status.SKIPPED);
                    d.setSkipReason("CAMPAIGN_CANCELLED");
                });
                s.setStatus(AdvertisingSend.Status.CANCELLED);
            });
        }
        audit.record("CAMPAIGN_CANCELLED", "campaign", id.toString(), Map.of("reason", String.valueOf(reason)));
        return toView(c);
    }

    @Transactional(readOnly = true)
    public CampaignDtos.Results results(UUID id, Pageable pageable) {
        Campaign c = require(id);
        Page<AdvertisingDtos.DeliveryView> page = c.getSendId() == null ? Page.empty(pageable)
                : advertising.listDeliveriesForSend(c.getSendId(), pageable);
        return new CampaignDtos.Results(stats(c), page);
    }

    // ---------- dispatch (called by the scheduler) ----------

    /** Dispatches up to {@code batchSize} pending recipients of a RUNNING campaign; marks completion when nothing is left. */
    @Transactional
    public int dispatchBatch(UUID campaignId, int batchSize) {
        Campaign c = require(campaignId);
        if (c.getStatus() != CampaignStatus.RUNNING || c.getSendId() == null) return 0;
        AdvertisingSend send = sends.findById(c.getSendId()).orElseThrow();
        List<CampaignRecipient> batch = recipients.pending(campaignId, PageRequest.of(0, batchSize));
        Instant now = Instant.now(clock);
        int dispatched = 0;
        for (CampaignRecipient r : batch) {
            Customer cust = customers.findById(r.getCustomerId()).orElse(null);
            if (cust == null) { r.skipped("CUSTOMER_DELETED", now); continue; }
            String rendered = advertising.renderForCustomer(cust, c.getMessage(), c.isIncludeServiceHistory(), r.getVehicleId());
            MessageDelivery d = new MessageDelivery(send, cust.getId(), r.getVehicleId(), cust.getPhone(), rendered);
            send.getDeliveries().add(d);
            deliveries.save(d);
            outbox.append("message_delivery", d.getId().toString(), RabbitTopology.ADVERTISING_ROUTING_KEY, Map.of("deliveryId", d.getId().toString()));
            r.dispatched(d.getId(), now);
            dispatched++;
        }
        if (recipients.countByCampaignIdAndEligibleTrueAndDispatchedAtIsNull(campaignId) == 0) {
            boolean allDone = deliveries.findBySendId(send.getId()).stream()
                    .noneMatch(d -> d.getStatus() == MessageDelivery.Status.PENDING || d.getStatus() == MessageDelivery.Status.ATTEMPTING);
            if (allDone) {
                send.recompute();
                c.setStatus(send.getStatus() == AdvertisingSend.Status.COMPLETED_WITH_FAILURES && deliveries.findBySendId(send.getId()).stream()
                        .allMatch(d -> d.getStatus() == MessageDelivery.Status.FAILED) ? CampaignStatus.FAILED : CampaignStatus.COMPLETED);
                c.setCompletedAt(now);
                audit.record("scheduler", "CAMPAIGN_COMPLETED", "campaign", campaignId.toString(), Map.of("status", c.getStatus().name()));
            }
        }
        return dispatched;
    }

    // ---------- helpers ----------

    private long materialise(Campaign c) {
        Instant now = Instant.now(clock);
        long eligible = 0;
        for (CampaignSegmentService.Member m : segments.resolve(c.getFilters())) {
            recipients.save(new CampaignRecipient(c.getId(), m.customer().getId(), m.vehicle() == null ? null : m.vehicle().getId(),
                    m.eligible(), m.reason(), m.firstChannel(), now));
            if (m.eligible()) eligible++;
        }
        return eligible;
    }

    private void apply(Campaign c, CampaignDtos.UpsertRequest req) {
        c.setDescription(req.description() == null || req.description().isBlank() ? null : req.description().trim());
        c.setMessage(req.message().trim());
        if (req.imageId() != null && media.findById(req.imageId()).isEmpty()) throw ApiException.notFound("Image not found");
        c.setImageAssetId(req.imageId());
        c.setIncludeServiceHistory(req.includeServiceHistory());
        c.setFilters(req.filters());
        String tz = req.timeZone() == null || req.timeZone().isBlank() ? c.getTimeZone() : req.timeZone();
        try { ZoneId.of(tz); } catch (DateTimeException e) { throw ApiException.badRequest("settings.invalidTimeZone", "Unknown time zone"); }
        Instant at = req.scheduledDate() != null && req.scheduledTime() != null
                ? ZonedDateTime.of(req.scheduledDate(), req.scheduledTime(), ZoneId.of(tz)).toInstant() : null;
        c.setSchedule(req.scheduledDate(), req.scheduledTime(), tz, at);
    }

    Campaign require(UUID id) {
        return campaigns.findById(id).orElseThrow(() -> ApiException.notFound("Campaign not found"));
    }

    CampaignDtos.Stats stats(Campaign c) {
        long selected = recipients.countByCampaignId(c.getId());
        long eligible = recipients.countByCampaignIdAndEligibleTrue(c.getId());
        List<MessageDelivery> ds = c.getSendId() == null ? List.of() : deliveries.findBySendId(c.getSendId());
        Map<Channel, Long> byChannel = new EnumMap<>(Channel.class);
        for (Channel ch : Channel.values()) byChannel.put(ch, 0L);
        long accepted = 0, delivered = 0, failed = 0, pending = 0, skipped = 0, uncertain = 0, attempted = 0;
        long actual = 0; boolean anyCost = props.hasAnyCost();
        for (MessageDelivery d : ds) {
            if (!d.getAttempts().isEmpty() && d.getAttempts().stream().anyMatch(a -> a.getOutcome() != null && a.getOutcome().name().equals("SKIPPED") == false)) attempted++;
            switch (d.getStatus()) {
                case ACCEPTED -> { accepted++; byChannel.merge(d.getCurrentChannel(), 1L, Long::sum); actual += props.costOf(d.getCurrentChannel()); }
                case DELIVERED -> { delivered++; byChannel.merge(d.getCurrentChannel(), 1L, Long::sum); actual += props.costOf(d.getCurrentChannel()); }
                case FAILED -> failed++;
                case PENDING, ATTEMPTING -> pending++;
                case SKIPPED -> skipped++;
                case UNCERTAIN -> uncertain++;
            }
        }
        long estimated = 0;
        for (CampaignRecipient r : recipients.findByCampaignId(c.getId())) if (r.isEligible() && r.getFirstChannel() != null) estimated += props.costOf(r.getFirstChannel());
        return new CampaignDtos.Stats(selected, eligible, attempted, accepted, delivered, failed, pending, skipped, uncertain, byChannel, estimated, anyCost ? actual : null);
    }

    CampaignDtos.CampaignView toView(Campaign c) {
        String imageUrl = c.getImageAssetId() == null ? null : media.findById(c.getImageAssetId()).map(MediaAsset::publicUrl).orElse(null);
        return new CampaignDtos.CampaignView(c.getId(), c.getName(), c.getDescription(), c.getMessage(), c.getImageAssetId(), imageUrl,
                c.isIncludeServiceHistory(), c.getFilters(), c.getScheduledDate(), c.getScheduledTime(), c.getTimeZone(), c.getScheduledAt(),
                c.getStatus(), c.getCancelReason(), recipients.countByCampaignId(c.getId()), recipients.countByCampaignIdAndEligibleTrue(c.getId()),
                stats(c), c.getSendId(), c.getStartedAt(), c.getCompletedAt(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
