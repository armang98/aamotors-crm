package com.aamotors.crm.messaging.sms;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sms")
@Tag(name = "SMS delivery history")
public class SmsController {

    private final SmsNotificationService service;

    public SmsController(SmsNotificationService service) {
        this.service = service;
    }

    @GetMapping("/notifications")
    public Page<SmsDtos.NotificationView> list(@RequestParam(required = false) SmsNotification.Status status,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return service.list(status, PageRequest.of(page, Math.min(Math.max(size, 1), 100)));
    }

    @GetMapping("/status")
    public SmsDtos.ProviderStatus status() {
        return service.status();
    }
}
