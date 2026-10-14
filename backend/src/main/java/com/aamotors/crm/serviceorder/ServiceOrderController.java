package com.aamotors.crm.serviceorder;

import com.aamotors.crm.customer.Language;
import com.aamotors.crm.messaging.sms.SmsDtos;
import com.aamotors.crm.messaging.sms.SmsNotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@Tag(name = "Service orders and vehicle-ready SMS")
public class ServiceOrderController {

    private final ServiceOrderService service;
    private final SmsNotificationService sms;

    public ServiceOrderController(ServiceOrderService service, SmsNotificationService sms) {
        this.service = service;
        this.sms = sms;
    }

    @GetMapping("/service-orders")
    public Page<ServiceOrderDtos.ServiceOrderView> search(@RequestParam(required = false) String q,
                                                          @RequestParam(required = false) ServiceOrderStatus status,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return service.search(q, status, PageRequest.of(page, Math.min(Math.max(size, 1), 100)));
    }

    @GetMapping("/service-orders/{id}")
    public ServiceOrderDtos.ServiceOrderView get(@PathVariable UUID id) { return service.get(id); }

    @GetMapping("/vehicles/{vehicleId}/service-orders")
    public List<ServiceOrderDtos.ServiceOrderView> forVehicle(@PathVariable UUID vehicleId) { return service.forVehicle(vehicleId); }

    @PostMapping("/service-orders")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceOrderDtos.ServiceOrderView create(@Valid @RequestBody ServiceOrderDtos.CreateRequest req) { return service.create(req); }

    @PutMapping("/service-orders/{id}")
    public ServiceOrderDtos.ServiceOrderView update(@PathVariable UUID id, @Valid @RequestBody ServiceOrderDtos.UpdateRequest req) { return service.update(id, req); }

    @PostMapping("/service-orders/{id}/status")
    public ServiceOrderDtos.ServiceOrderView status(@PathVariable UUID id, @Valid @RequestBody ServiceOrderDtos.StatusRequest req) {
        return service.changeStatus(id, req.status(), req.reason());
    }

    @PostMapping("/service-orders/{id}/mark-ready")
    public ServiceOrderDtos.ServiceOrderView markReady(@PathVariable UUID id) {
        return service.changeStatus(id, ServiceOrderStatus.READY_FOR_PICKUP, null);
    }

    @PostMapping("/service-orders/{id}/mark-paid")
    public ServiceOrderDtos.ServiceOrderView markPaid(@PathVariable UUID id, @RequestBody(required = false) @Valid ServiceOrderDtos.MarkPaidRequest req) {
        return service.markPaid(id, req == null ? new ServiceOrderDtos.MarkPaidRequest(null, null) : req);
    }

    @GetMapping("/service-orders/{id}/sms-preview")
    public ServiceOrderDtos.SmsPreview preview(@PathVariable UUID id, @RequestParam(required = false) Language language) {
        return service.preview(id, language);
    }

    @GetMapping("/service-orders/{id}/notifications")
    public List<SmsDtos.NotificationView> notifications(@PathVariable UUID id) {
        service.require(id);
        return sms.forOrder(id);
    }

    @PostMapping("/service-orders/{id}/notifications/resend")
    @ResponseStatus(HttpStatus.CREATED)
    public SmsDtos.NotificationView resend(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        boolean confirm = body != null && Boolean.TRUE.equals(body.get("confirm"));
        return SmsDtos.NotificationView.from(service.resendReady(id, confirm), null);
    }
}
