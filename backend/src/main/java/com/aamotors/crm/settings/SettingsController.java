package com.aamotors.crm.settings;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/settings")
@Tag(name = "Business settings")
public class SettingsController {

    private final SettingsService service;

    public SettingsController(SettingsService service) {
        this.service = service;
    }

    @Operation(summary = "Read business settings and integration status flags")
    @GetMapping
    public SettingsDtos.BusinessSettingsResponse get() {
        return service.get();
    }

    @Operation(summary = "Update editable business settings")
    @PutMapping
    public SettingsDtos.BusinessSettingsResponse update(@Valid @RequestBody SettingsDtos.UpdateBusinessSettingsRequest request) {
        return service.update(request);
    }

    @Operation(summary = "Upload the business logo (png/jpeg/webp/svg, max 2 MB)")
    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SettingsDtos.BusinessSettingsResponse uploadLogo(@RequestParam("file") MultipartFile file) {
        return service.uploadLogo(file);
    }

    @Operation(summary = "Remove the business logo")
    @DeleteMapping("/logo")
    public SettingsDtos.BusinessSettingsResponse removeLogo() {
        return service.removeLogo();
    }
}
