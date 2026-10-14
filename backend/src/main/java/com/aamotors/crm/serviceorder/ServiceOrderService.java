package com.aamotors.crm.serviceorder;

import com.aamotors.crm.audit.AuditService;
import com.aamotors.crm.common.error.ApiException;
import com.aamotors.crm.common.phone.PhoneNumberNormalizer;
import com.aamotors.crm.customer.Language;
import com.aamotors.crm.messaging.sms.SmsNotification;
import com.aamotors.crm.messaging.sms.SmsNotificationService;
import com.aamotors.crm.referral.ReferralService;
import com.aamotors.crm.vehicle.Vehicle;
import com.aamotors.crm.vehicle.VehicleRepository;
import com.aamotors.crm.vehicle.Vin;
import jakarta.annotation.PostConstruct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ServiceOrderService {

    private final ServiceOrderRepository orders;
    private final VehicleRepository vehicles;
    private final SmsNotificationService sms;
    private final ReferralService referrals;
    private final AuditService audit;
    private final Clock clock;

    public ServiceOrderService(ServiceOrderRepository orders, VehicleRepository vehicles, SmsNotificationService sms,
                               ReferralService referrals, AuditService audit, Clock clock) {
        this.orders = orders;
        this.vehicles = vehicles;
        this.sms = sms;
        this.referrals = referrals;
        this.audit = audit;
        this.clock = clock;
    }

    /** Lets the referral module validate qualifying / redeemed orders without depending on this module. */
    @PostConstruct
    void wireReferralGateway() {
        referrals.registerServiceOrderGateway((orderId, customerId) -> {
            ServiceOrder o = orders.findDetailed(orderId).orElseThrow(() -> ApiException.notFound("Service order not found"));
            if (!o.getCustomer().getId().equals(customerId)) {
                throw ApiException.conflict("referral.orderNotInvitedCustomer", "Service order belongs to a different customer");
            }
            if (!o.isPaid()) {
                throw ApiException.conflict("referral.orderNotPaid", "Service order is not paid");
            }
        });
    }

    // ---------- queries ----------

    @Transactional(readOnly = true)
    public Page<ServiceOrderDtos.ServiceOrderView> search(String q, ServiceOrderStatus status, Pageable pageable) {
        String query = q == null ? "" : q.trim();
        String phone = PhoneNumberNormalizer.searchFragment(query).orElse("#");
        return orders.search(query.toLowerCase(), query.isEmpty() ? "" : Vin.normalizePlate(query), query.toUpperCase(), phone, status, pageable)
                .map(this::toView);
    }

    @Transactional(readOnly = true)
    public ServiceOrderDtos.ServiceOrderView get(UUID id) {
        return toView(require(id));
    }

    @Transactional(readOnly = true)
    public List<ServiceOrderDtos.ServiceOrderView> forVehicle(UUID vehicleId) {
        return orders.findByVehicle(vehicleId).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public ServiceOrderDtos.SmsPreview preview(UUID id, Language language) {
        ServiceOrder o = require(id);
        return sms.preview(o, language);
    }

    // ---------- commands ----------

    @Transactional
    public ServiceOrderDtos.ServiceOrderView create(ServiceOrderDtos.CreateRequest req) {
        Vehicle v = vehicles.findById(req.vehicleId()).orElseThrow(() -> ApiException.notFound("Vehicle not found"));
        Instant now = Instant.now(clock);
        String number = String.format("SO-%d-%06d", Year.now(ZoneId.of("Asia/Yerevan")).getValue(), orders.nextOrderNumber());
        ServiceOrder o = new ServiceOrder(number, v, v.getCustomer(), now);
        o.setDescription(blank(req.description()));
        o.setTotalAmount(req.totalAmount());
        orders.save(o);
        audit.record("SERVICE_ORDER_CREATED", "service_order", o.getId().toString(), Map.of("orderNumber", number, "vehicleId", v.getId().toString()));
        return toView(o);
    }

    @Transactional
    public ServiceOrderDtos.ServiceOrderView update(UUID id, ServiceOrderDtos.UpdateRequest req) {
        ServiceOrder o = require(id);
        o.setDescription(blank(req.description()));
        if (req.totalAmount() != null) o.setTotalAmount(req.totalAmount());
        audit.record("SERVICE_ORDER_UPDATED", "service_order", id.toString(), Map.of("totalAmount", String.valueOf(req.totalAmount())));
        return toView(o);
    }

    /**
     * Status transition. Moving to READY_FOR_PICKUP persists the change, creates the
     * SMS notification row and the outbox event in this same transaction. The unique
     * index on (service_order_id) for the automatic kind guarantees a single automatic SMS.
     */
    @Transactional
    public ServiceOrderDtos.ServiceOrderView changeStatus(UUID id, ServiceOrderStatus next, String reason) {
        ServiceOrder o = require(id);
        ServiceOrderStatus current = o.getStatus();
        if (!current.allowedTransitions().contains(next)) {
            throw ApiException.conflict("serviceOrder.invalidTransition", "Cannot move from " + current + " to " + next);
        }
        Instant now = Instant.now(clock);
        o.transition(next, now, blank(reason));
        audit.record("SERVICE_ORDER_STATUS", "service_order", id.toString(), Map.of("from", current.name(), "to", next.name()));
        if (next == ServiceOrderStatus.READY_FOR_PICKUP) {
            sms.queueVehicleReady(o, SmsNotification.Kind.VEHICLE_READY_AUTO);
        }
        return toView(o);
    }

    @Transactional
    public ServiceOrderDtos.ServiceOrderView markPaid(UUID id, ServiceOrderDtos.MarkPaidRequest req) {
        ServiceOrder o = require(id);
        if (o.getStatus() == ServiceOrderStatus.CANCELLED) {
            throw ApiException.conflict("serviceOrder.invalidTransition", "Cancelled orders cannot be paid");
        }
        Instant paidAt = req.paidAt() == null ? Instant.now(clock) : req.paidAt().atStartOfDay(ZoneId.of("Asia/Yerevan")).toInstant();
        o.setPaidAt(paidAt);
        if (req.totalAmount() != null) o.setTotalAmount(req.totalAmount());
        audit.record("SERVICE_ORDER_PAID", "service_order", id.toString(), Map.of("totalAmount", String.valueOf(o.getTotalAmount())));
        return toView(o);
    }

    @Transactional
    public SmsNotification resendReady(UUID id, boolean confirm) {
        if (!confirm) {
            throw ApiException.badRequest("sms.confirmRequired", "Manual resend must be confirmed");
        }
        ServiceOrder o = require(id);
        if (o.getStatus() != ServiceOrderStatus.READY_FOR_PICKUP) {
            throw ApiException.conflict("serviceOrder.notReady", "Vehicle is not marked ready for pickup");
        }
        SmsNotification n = sms.queueVehicleReady(o, SmsNotification.Kind.VEHICLE_READY_MANUAL);
        audit.record("SMS_MANUAL_RESEND", "service_order", id.toString(), Map.of("notificationId", n.getId().toString()));
        return n;
    }

    // ---------- helpers ----------

    public ServiceOrder require(UUID id) {
        return orders.findDetailed(id).orElseThrow(() -> ApiException.notFound("Service order not found"));
    }

    ServiceOrderDtos.ServiceOrderView toView(ServiceOrder o) {
        var v = o.getVehicle();
        var c = o.getCustomer();
        var summary = sms.latestFor(o.getId())
                .map(n -> new ServiceOrderDtos.ReadyNotificationSummary(n.getStatus().name(), n.getAttemptCount(),
                        n.getAttempts().isEmpty() ? null : n.getAttempts().get(n.getAttempts().size() - 1).getStartedAt(),
                        n.getProviderMessageId(), n.getId()))
                .orElse(null);
        return new ServiceOrderDtos.ServiceOrderView(o.getId(), o.getOrderNumber(),
                new ServiceOrderDtos.VehicleRef(v.getId(), v.getBrand(), v.getModel(), v.getLicensePlate(), v.getVin()),
                new ServiceOrderDtos.CustomerRef(c.getId(), c.getFullName(), c.getPhone(), c.getPreferredLanguage()),
                o.getStatus(), o.getDescription(), o.getTotalAmount(), o.getCurrency(), o.getPaidAt(), o.getReceivedAt(),
                o.getReadyAt(), o.getDeliveredAt(), o.getCancelledAt(), o.getCancelReason(), o.isReferralDiscountApplied(),
                summary, o.getCreatedAt(), o.getUpdatedAt());
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
