package com.aamotors.crm.messaging.sms;

import com.aamotors.crm.audit.AuditService;
import com.aamotors.crm.common.error.ApiException;
import com.aamotors.crm.customer.ConsentChannel;
import com.aamotors.crm.customer.Customer;
import com.aamotors.crm.customer.CustomerConsent;
import com.aamotors.crm.customer.Language;
import com.aamotors.crm.messaging.config.RabbitTopology;
import com.aamotors.crm.messaging.outbox.OutboxService;
import com.aamotors.crm.serviceorder.ServiceOrder;
import com.aamotors.crm.serviceorder.ServiceOrderDtos;
import com.aamotors.crm.serviceorder.ServiceOrderRepository;
import com.aamotors.crm.settings.BusinessSettings;
import com.aamotors.crm.settings.IntegrationProperties;
import com.aamotors.crm.settings.SettingsService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Creates, queues and reports SMS notifications. Sending itself happens in {@link SmsDeliveryWorker}. */
@Service
public class SmsNotificationService {

    public static final String EVENT_SMS_QUEUED = RabbitTopology.SMS_ROUTING_KEY;

    private final SmsNotificationRepository notifications;
    private final ServiceOrderRepository orders;
    private final SettingsService settings;
    private final OutboxService outbox;
    private final SmsProvider provider;
    private final IntegrationProperties integrations;
    private final AuditService audit;
    private final Clock clock;

    public SmsNotificationService(SmsNotificationRepository notifications, ServiceOrderRepository orders, SettingsService settings,
                                  OutboxService outbox, SmsProvider provider, IntegrationProperties integrations, AuditService audit, Clock clock) {
        this.notifications = notifications;
        this.orders = orders;
        this.settings = settings;
        this.outbox = outbox;
        this.provider = provider;
        this.integrations = integrations;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ServiceOrderDtos.SmsPreview preview(ServiceOrder o, Language language) {
        Language lang = language != null ? language : o.getCustomer().getPreferredLanguage();
        String text = render(o, lang);
        var seg = SmsTemplateRenderer.segments(text);
        return new ServiceOrderDtos.SmsPreview(lang.name(), text, o.getCustomer().getPhone(), seg.chars(), seg.segments(), seg.unicode());
    }

    /**
     * Creates the notification row and the outbox event inside the caller's transaction.
     * For the automatic kind the partial unique index makes a second call a no-op
     * (returns the existing notification) so a vehicle is never notified twice automatically.
     */
    @Transactional
    public SmsNotification queueVehicleReady(ServiceOrder o, SmsNotification.Kind kind) {
        if (kind == SmsNotification.Kind.VEHICLE_READY_AUTO) {
            Optional<SmsNotification> existing = notifications.findByServiceOrderIdAndKind(o.getId(), kind);
            if (existing.isPresent()) return existing.get();
        }
        Customer c = o.getCustomer();
        Language lang = c.getPreferredLanguage();
        Instant now = Instant.now(clock);
        SmsNotification n = new SmsNotification(o.getId(), c.getId(), kind, c.getPhone(), lang, render(o, lang), provider.name(), now);

        String skip = skipReason(c);
        if (skip != null) {
            n.markSkipped(skip);
            notifications.save(n);
            audit.record("SMS_SKIPPED", "sms_notification", n.getId().toString(), Map.of("reason", skip, "orderId", o.getId().toString()));
            if (kind == SmsNotification.Kind.VEHICLE_READY_MANUAL) {
                throw ApiException.conflict("sms.disabledForCustomer", "Customer has opted out of SMS");
            }
            return n;
        }
        notifications.save(n);
        outbox.append("sms_notification", n.getId().toString(), EVENT_SMS_QUEUED, Map.of("notificationId", n.getId().toString(), "kind", kind.name()));
        audit.record("SMS_QUEUED", "sms_notification", n.getId().toString(), Map.of("orderId", o.getId().toString(), "kind", kind.name()));
        return n;
    }

    @Transactional(readOnly = true)
    public Optional<SmsNotification> latestFor(UUID orderId) {
        return notifications.findFirstByServiceOrderIdOrderByCreatedAtDesc(orderId);
    }

    @Transactional(readOnly = true)
    public List<SmsDtos.NotificationView> forOrder(UUID orderId) {
        String number = orders.findById(orderId).map(ServiceOrder::getOrderNumber).orElse(null);
        return notifications.findByServiceOrderIdOrderByCreatedAtDesc(orderId).stream().map(n -> SmsDtos.NotificationView.from(n, number)).toList();
    }

    @Transactional(readOnly = true)
    public Page<SmsDtos.NotificationView> list(SmsNotification.Status status, Pageable pageable) {
        return notifications.list(status, pageable).map(n -> SmsDtos.NotificationView.from(n,
                orders.findById(n.getServiceOrderId()).map(ServiceOrder::getOrderNumber).orElse(null)));
    }

    @Transactional(readOnly = true)
    public SmsDtos.ProviderStatus status() {
        var aws = integrations.aws();
        String origin = aws.smsOriginationIdentity() == null || aws.smsOriginationIdentity().isBlank() ? null
                : aws.smsOriginationIdentity().replaceAll("(?<=.{4}).(?=.{3})", "*");
        return new SmsDtos.ProviderStatus(provider.name(), integrations.sms().enabled(), provider.isConfigured(),
                origin, aws.region(),
                notifications.countByStatus(SmsNotification.Status.QUEUED), notifications.countByStatus(SmsNotification.Status.SENT),
                notifications.countByStatus(SmsNotification.Status.DELIVERED), notifications.countByStatus(SmsNotification.Status.FAILED),
                notifications.countByStatus(SmsNotification.Status.UNCERTAIN));
    }

    // ---------- helpers ----------

    private String render(ServiceOrder o, Language lang) {
        BusinessSettings s = settings.current();
        var v = o.getVehicle();
        return SmsTemplateRenderer.renderVehicleReady(lang, v.getBrand(), v.getModel(), v.getLicensePlate(), s);
    }

    /** Vehicle-ready is a service notification, so only an explicit SMS opt-out suppresses it. */
    private static String skipReason(Customer c) {
        if (c.getPhone() == null || c.getPhone().isBlank()) return "NO_PHONE";
        CustomerConsent smsConsent = c.consentFor(ConsentChannel.SMS);
        if (smsConsent != null && !smsConsent.isGranted() && smsConsent.getRevokedAt() != null) return "SMS_OPTED_OUT";
        return null;
    }
}
