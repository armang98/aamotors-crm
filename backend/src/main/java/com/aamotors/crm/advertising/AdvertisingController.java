package com.aamotors.crm.advertising;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/advertising")
@Tag(name = "Quick advertising", description = "WhatsApp → Viber Business → Telegram → Viber Bot, sequential fallback")
public class AdvertisingController {

    private final AdvertisingService service;

    public AdvertisingController(AdvertisingService service) {
        this.service = service;
    }

    @PostMapping("/preview")
    public AdvertisingDtos.Preview preview(@Valid @RequestBody AdvertisingDtos.ComposeRequest req) { return service.preview(req); }

    @PostMapping("/send")
    @ResponseStatus(HttpStatus.CREATED)
    public AdvertisingDtos.SendView send(@Valid @RequestBody AdvertisingDtos.ComposeRequest req) { return service.send(req); }

    @GetMapping("/sends")
    public Page<AdvertisingDtos.SendView> sends(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.listSends(PageRequest.of(page, Math.min(Math.max(size, 1), 100)));
    }

    @GetMapping("/sends/{id}")
    public AdvertisingDtos.SendView send(@PathVariable UUID id) { return service.getSend(id); }

    @GetMapping("/deliveries")
    public Page<AdvertisingDtos.DeliveryView> deliveries(@RequestParam(required = false) MessageDelivery.Status status,
                                                         @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.listDeliveries(status, PageRequest.of(page, Math.min(Math.max(size, 1), 100)));
    }

    @PostMapping("/deliveries/{id}/resolve")
    public AdvertisingDtos.DeliveryView resolve(@PathVariable UUID id, @Valid @RequestBody AdvertisingDtos.ResolveRequest req) {
        return service.resolve(id, req);
    }

    @GetMapping("/channels")
    public List<AdvertisingDtos.ChannelStatus> channels() { return service.channelStatuses(); }
}
