package com.aamotors.crm.advertising;

import com.aamotors.crm.audit.AuditService;
import com.aamotors.crm.common.error.ApiException;
import com.aamotors.crm.common.media.MediaAsset;
import com.aamotors.crm.common.media.MediaAssetRepository;
import com.aamotors.crm.customer.Customer;
import com.aamotors.crm.customer.CustomerRepository;
import com.aamotors.crm.messaging.channel.Channel;
import com.aamotors.crm.messaging.channel.ChannelProvider;
import com.aamotors.crm.messaging.channel.ChannelRegistry;
import com.aamotors.crm.messaging.config.RabbitTopology;
import com.aamotors.crm.messaging.outbox.OutboxService;
import com.aamotors.crm.vehicle.Vehicle;
import com.aamotors.crm.vehicle.VehicleRepository;
import com.aamotors.crm.vehicle.VehicleServiceRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
public class AdvertisingService {

    private final AdvertisingSendRepository sends;
    private final MessageDeliveryRepository deliveries;
    private final CustomerRepository customers;
    private final VehicleRepository vehicles;
    private final VehicleServiceRecordRepository records;
    private final MediaAssetRepository media;
    private final ChannelRegistry channels;
    private final OutboxService outbox;
    private final AuditService audit;
    private final Clock clock;

    public AdvertisingService(AdvertisingSendRepository sends, MessageDeliveryRepository deliveries, CustomerRepository customers,
                              VehicleRepository vehicles, VehicleServiceRecordRepository records, MediaAssetRepository media,
                              ChannelRegistry channels, OutboxService outbox, AuditService audit, Clock clock) {
        this.sends = sends;
        this.deliveries = deliveries;
        this.customers = customers;
        this.vehicles = vehicles;
        this.records = records;
        this.media = media;
        this.channels = channels;
        this.outbox = outbox;
        this.audit = audit;
        this.clock = clock;
    }

    // ---------- preview ----------

    @Transactional(readOnly = true)
    public AdvertisingDtos.Preview preview(AdvertisingDtos.ComposeRequest req) {
        List<AdvertisingDtos.RecipientPreview> out = new ArrayList<>();
        int eligible = 0, skipped = 0;
        for (UUID customerId : new LinkedHashSet<>(req.customerIds())) {
            Customer c = customers.findById(customerId).orElseThrow(() -> ApiException.notFound("Customer not found: " + customerId));
            Rendered rendered = render(c, req);
            List<AdvertisingDtos.ChannelEligibility> elig = new ArrayList<>();
            Channel first = null;
            for (ChannelProvider p : channels.inFallbackOrder()) {
                var e = p.eligibility(c);
                elig.add(new AdvertisingDtos.ChannelEligibility(p.channel(), e.eligible(), e.reason(), p.note()));
                if (first == null && e.eligible()) first = p.channel();
            }
            boolean skip = first == null || rendered.needsVehicleSelection();
            String skipReason = first == null ? "NO_ELIGIBLE_CHANNEL" : rendered.needsVehicleSelection() ? "VEHICLE_SELECTION_REQUIRED" : null;
            if (skip) skipped++; else eligible++;
            out.add(new AdvertisingDtos.RecipientPreview(c.getId(), c.getFullName(), c.getPhone(), rendered.needsVehicleSelection(),
                    rendered.options(), rendered.text(), rendered.text().codePointCount(0, rendered.text().length()), elig, first, skip, skipReason));
        }
        return new AdvertisingDtos.Preview(out, eligible, skipped);
    }

    // ---------- send ----------

    @Transactional
    public AdvertisingDtos.SendView send(AdvertisingDtos.ComposeRequest req) {
        if (!req.confirm()) throw ApiException.badRequest("advertising.confirmRequired", "Sending must be confirmed after preview");
        AdvertisingDtos.Preview preview = preview(req);
        if (preview.recipients().stream().anyMatch(AdvertisingDtos.RecipientPreview::needsVehicleSelection)) {
            throw ApiException.badRequest("advertising.vehicleSelectionRequired", "Select a vehicle for customers with several vehicles");
        }
        if (preview.eligibleCount() == 0) throw ApiException.badRequest("advertising.noEligibleRecipients", "No recipient can be reached on any channel");
        MediaAsset image = req.imageId() == null ? null : media.findById(req.imageId()).orElseThrow(() -> ApiException.notFound("Image not found"));
        var auth = SecurityContextHolder.getContext().getAuthentication();
        AdvertisingSend send = new AdvertisingSend(req.message().trim(), image == null ? null : image.getId(), req.includeServiceHistory(), null,
                auth == null ? "system" : auth.getName());
        sends.save(send);
        for (AdvertisingDtos.RecipientPreview r : preview.recipients()) {
            UUID vehicleId = req.vehicleSelections() == null ? null : req.vehicleSelections().get(r.customerId());
            MessageDelivery d = new MessageDelivery(send, r.customerId(), vehicleId, r.phone(), r.renderedMessage());
            if (r.skipped()) {
                d.setStatus(MessageDelivery.Status.SKIPPED);
                d.setSkipReason(r.skipReason());
            }
            send.getDeliveries().add(d);
        }
        send.recompute();
        if (send.getStatus() == AdvertisingSend.Status.COMPLETED) send.setStatus(AdvertisingSend.Status.COMPLETED); else send.setStatus(AdvertisingSend.Status.QUEUED);
        sends.saveAndFlush(send);
        for (MessageDelivery d : send.getDeliveries()) {
            if (d.getStatus() == MessageDelivery.Status.PENDING) {
                outbox.append("message_delivery", d.getId().toString(), RabbitTopology.ADVERTISING_ROUTING_KEY, Map.of("deliveryId", d.getId().toString()));
            }
        }
        audit.record("ADVERTISING_SENT", "advertising_send", send.getId().toString(),
                Map.of("recipients", send.getDeliveries().size(), "includeServiceHistory", req.includeServiceHistory(), "image", image != null));
        return toView(send, true);
    }

    // ---------- queries ----------

    @Transactional(readOnly = true)
    public Page<AdvertisingDtos.SendView> listSends(Pageable pageable) {
        return sends.findByCampaignIdIsNullOrderByCreatedAtDesc(pageable).map(s -> toView(s, false));
    }

    @Transactional(readOnly = true)
    public AdvertisingDtos.SendView getSend(UUID id) {
        return toView(sends.findWithDeliveries(id).orElseThrow(() -> ApiException.notFound("Send not found")), true);
    }

    @Transactional(readOnly = true)
    public Page<AdvertisingDtos.DeliveryView> listDeliveries(MessageDelivery.Status status, Pageable pageable) {
        return deliveries.list(status, pageable).map(this::toView);
    }

    /** Used by campaigns: render the message for one customer (history for the chosen vehicle, if any). */
    @Transactional(readOnly = true)
    public String renderForCustomer(Customer c, String message, boolean includeHistory, UUID vehicleId) {
        Map<UUID, UUID> selections = vehicleId == null ? Map.of() : Map.of(c.getId(), vehicleId);
        Rendered r = render(c, new AdvertisingDtos.ComposeRequest(List.of(c.getId()), message, null, includeHistory, selections, true));
        return r.text();
    }

    @Transactional(readOnly = true)
    public Page<AdvertisingDtos.DeliveryView> listDeliveriesForSend(UUID sendId, Pageable pageable) {
        return deliveries.findBySendIdOrderByUpdatedAtDesc(sendId, pageable).map(this::toView);
    }

    public List<AdvertisingDtos.ChannelStatus> channelStatuses() {
        return channels.inFallbackOrder().stream()
                .map(p -> new AdvertisingDtos.ChannelStatus(p.channel(), p.isConfigured(), p.isEnabled(), p.isMock(), p.note())).toList();
    }

    /** Manual reconciliation of an UNCERTAIN delivery. */
    @Transactional
    public AdvertisingDtos.DeliveryView resolve(UUID deliveryId, AdvertisingDtos.ResolveRequest req) {
        MessageDelivery d = deliveries.findById(deliveryId).orElseThrow(() -> ApiException.notFound("Delivery not found"));
        if (d.getStatus() != MessageDelivery.Status.UNCERTAIN) {
            throw ApiException.conflict("advertising.notUncertain", "Only uncertain deliveries can be resolved manually");
        }
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String by = auth == null ? "system" : auth.getName();
        switch (req.outcome()) {
            case "DELIVERED" -> { d.resolve(MessageDelivery.Status.DELIVERED, by, req.note()); d.markDelivered(Instant.now(clock)); d.resolve(MessageDelivery.Status.DELIVERED, by, req.note()); }
            case "FAILED" -> {
                if (req.continueFallback()) {
                    d.resolve(MessageDelivery.Status.PENDING, by, req.note());
                    d.setChannelIndex(d.getChannelIndex() + 1);
                    outbox.append("message_delivery", d.getId().toString(), RabbitTopology.ADVERTISING_ROUTING_KEY, Map.of("deliveryId", d.getId().toString()));
                } else {
                    d.resolve(MessageDelivery.Status.FAILED, by, req.note());
                }
            }
            default -> throw ApiException.badRequest("validation.failed", "outcome must be DELIVERED or FAILED");
        }
        d.getSend().recompute();
        audit.record("DELIVERY_RESOLVED", "message_delivery", deliveryId.toString(), Map.of("outcome", req.outcome(), "continueFallback", req.continueFallback()));
        return toView(d);
    }

    // ---------- rendering ----------

    record Rendered(String text, boolean needsVehicleSelection, List<AdvertisingDtos.VehicleOption> options) {}

    Rendered render(Customer c, AdvertisingDtos.ComposeRequest req) {
        String base = req.message().trim();
        if (!req.includeServiceHistory()) return new Rendered(base, false, List.of());
        List<Vehicle> owned = vehicles.findByCustomerIdOrderByCreatedAtAsc(c.getId());
        List<AdvertisingDtos.VehicleOption> options = owned.stream()
                .map(v -> new AdvertisingDtos.VehicleOption(v.getId(), v.getBrand() + " " + v.getModel() + " · " + v.getLicensePlate())).toList();
        if (owned.isEmpty()) return new Rendered(base, false, options);
        Vehicle chosen;
        if (owned.size() == 1) {
            chosen = owned.get(0);
        } else {
            UUID selected = req.vehicleSelections() == null ? null : req.vehicleSelections().get(c.getId());
            chosen = owned.stream().filter(v -> v.getId().equals(selected)).findFirst().orElse(null);
            if (chosen == null) return new Rendered(base, true, options);
        }
        var latest = records.findFirstByVehicleIdOrderByServiceDateDescCreatedAtDesc(chosen.getId());
        if (latest.isEmpty()) return new Rendered(base, false, options);
        return new Rendered(base + "\n\n" + ServiceHistorySummary.render(c.getPreferredLanguage(), chosen, latest.get()), false, options);
    }

    // ---------- views ----------

    AdvertisingDtos.SendView toView(AdvertisingSend s, boolean withDeliveries) {
        List<MessageDelivery> ds = withDeliveries ? s.getDeliveries() : deliveries.findBySendId(s.getId());
        var counts = new AdvertisingDtos.Counts(ds.size(),
                ds.stream().filter(d -> d.getStatus() == MessageDelivery.Status.PENDING || d.getStatus() == MessageDelivery.Status.ATTEMPTING).count(),
                ds.stream().filter(d -> d.getStatus() == MessageDelivery.Status.ACCEPTED).count(),
                ds.stream().filter(d -> d.getStatus() == MessageDelivery.Status.DELIVERED).count(),
                ds.stream().filter(d -> d.getStatus() == MessageDelivery.Status.FAILED).count(),
                ds.stream().filter(d -> d.getStatus() == MessageDelivery.Status.SKIPPED).count(),
                ds.stream().filter(d -> d.getStatus() == MessageDelivery.Status.UNCERTAIN).count());
        String imageUrl = s.getImageAssetId() == null ? null : media.findById(s.getImageAssetId()).map(MediaAsset::publicUrl).orElse(null);
        return new AdvertisingDtos.SendView(s.getId(), s.getMessage(), imageUrl, s.isIncludeServiceHistory(), s.getStatus(), counts, s.getCampaignId(),
                s.getCreatedAt(), withDeliveries ? ds.stream().map(this::toView).toList() : null);
    }

    AdvertisingDtos.DeliveryView toView(MessageDelivery d) {
        String name = customers.findById(d.getCustomerId()).map(Customer::getFullName).orElse(null);
        return new AdvertisingDtos.DeliveryView(d.getId(), d.getSend().getId(), d.getCustomerId(), name, d.getPhone(), d.getStatus(), d.getCurrentChannel(),
                d.getAttempts().stream().map(MessageAttempt::getChannel).distinct().toList(), d.getSkipReason(), d.getRenderedMessage(),
                d.getAttempts().stream().map(a -> new AdvertisingDtos.AttemptView(a.getChannel(), a.getAttemptNo(),
                        a.getOutcome() == null ? null : a.getOutcome().name(), a.getReason(), a.getProviderMessageId(), a.getError(), a.getStartedAt(), a.getFinishedAt())).toList(),
                d.getProviderMessageId(), d.getDeliveredAt(), d.getResolvedBy(), d.getResolutionNote(), d.getUpdatedAt());
    }
}
