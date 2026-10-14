package com.aamotors.crm.campaign;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/campaigns")
@Tag(name = "Advertising campaigns")
public class CampaignController {

    private final CampaignService service;

    public CampaignController(CampaignService service) { this.service = service; }

    @GetMapping
    public Page<CampaignDtos.CampaignView> list(@RequestParam(required = false) CampaignStatus status,
                                                @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(status, PageRequest.of(page, Math.min(Math.max(size, 1), 100)));
    }

    @GetMapping("/{id}")
    public CampaignDtos.CampaignView get(@PathVariable UUID id) { return service.get(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CampaignDtos.CampaignView create(@Valid @RequestBody CampaignDtos.UpsertRequest req) { return service.create(req); }

    @PutMapping("/{id}")
    public CampaignDtos.CampaignView update(@PathVariable UUID id, @Valid @RequestBody CampaignDtos.UpsertRequest req) { return service.update(id, req); }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) { service.delete(id); return ResponseEntity.noContent().build(); }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public CampaignDtos.CampaignView duplicate(@PathVariable UUID id) { return service.duplicate(id); }

    @PostMapping("/segment/preview")
    public CampaignDtos.SegmentPreview segment(@RequestBody(required = false) CampaignDtos.SegmentPreviewRequest req) {
        return service.previewSegment(req == null ? null : req.filters());
    }

    @GetMapping("/{id}/recipients")
    public Page<CampaignDtos.RecipientView> recipients(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return service.recipients(id, PageRequest.of(page, Math.min(Math.max(size, 1), 200)));
    }

    @PostMapping("/{id}/schedule")
    public CampaignDtos.CampaignView schedule(@PathVariable UUID id, @Valid @RequestBody CampaignDtos.ScheduleRequest req) { return service.schedule(id, req); }

    @PostMapping("/{id}/start")
    public CampaignDtos.CampaignView start(@PathVariable UUID id) { return service.start(id); }

    @PostMapping("/{id}/pause")
    public CampaignDtos.CampaignView pause(@PathVariable UUID id) { return service.pause(id); }

    @PostMapping("/{id}/resume")
    public CampaignDtos.CampaignView resume(@PathVariable UUID id) { return service.resume(id); }

    @PostMapping("/{id}/cancel")
    public CampaignDtos.CampaignView cancel(@PathVariable UUID id, @RequestBody(required = false) CampaignDtos.CancelRequest req) {
        return service.cancel(id, req == null ? null : req.reason());
    }

    @GetMapping("/{id}/results")
    public CampaignDtos.Results results(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return service.results(id, PageRequest.of(page, Math.min(Math.max(size, 1), 200)));
    }
}
