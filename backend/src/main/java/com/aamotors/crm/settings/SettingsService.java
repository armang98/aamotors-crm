package com.aamotors.crm.settings;

import com.aamotors.crm.audit.AuditService;
import com.aamotors.crm.common.error.ApiException;
import com.aamotors.crm.common.media.MediaStorageService;
import com.aamotors.crm.common.phone.PhoneNumberNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SettingsService {

    static final String LOGO_FOLDER = "logo";
    static final String LOGO_URL_PREFIX = "/api/public/media/logo/";

    private final BusinessSettingsRepository repository;
    private final IntegrationProperties integrations;
    private final MediaStorageService media;
    private final AuditService audit;

    public SettingsService(BusinessSettingsRepository repository, IntegrationProperties integrations,
                           MediaStorageService media, AuditService audit) {
        this.repository = repository;
        this.integrations = integrations;
        this.media = media;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public BusinessSettings current() {
        return repository.findBySingletonKey((short) 1)
                .orElseThrow(() -> new IllegalStateException("business_settings row missing; run Flyway migrations"));
    }

    @Transactional(readOnly = true)
    public SettingsDtos.BusinessSettingsResponse get() {
        return toResponse(current());
    }

    @Transactional
    public SettingsDtos.BusinessSettingsResponse update(SettingsDtos.UpdateBusinessSettingsRequest req) {
        BusinessSettings s = current();
        try {
            ZoneId.of(req.timeZone());
        } catch (DateTimeException e) {
            throw ApiException.badRequest("settings.invalidTimeZone", "Unknown time zone: " + req.timeZone());
        }

        Map<String, Object> before = snapshot(s);
        s.setBusinessName(req.businessName().trim());
        s.setAddress(req.address().trim());
        s.setPrimaryPhone(PhoneNumberNormalizer.normalizeOrThrow(req.primaryPhone()));
        s.setSecondaryPhone(req.secondaryPhone() == null || req.secondaryPhone().isBlank()
                ? null : PhoneNumberNormalizer.normalizeOrThrow(req.secondaryPhone()));
        s.setWorkingHours(req.workingHours() == null ? null : req.workingHours().trim());
        s.setDefaultLanguage(req.defaultLanguage());
        s.setTimeZone(req.timeZone());
        s.setCurrency(req.currency());
        repository.save(s);
        audit.record("SETTINGS_UPDATED", "business_settings", s.getId().toString(), Map.of("before", before, "after", snapshot(s)));
        return toResponse(s);
    }

    @Transactional
    public SettingsDtos.BusinessSettingsResponse uploadLogo(MultipartFile file) {
        BusinessSettings s = current();
        var stored = media.storeImage(LOGO_FOLDER, file, media.logoMaxBytes());
        String previous = s.getLogoFileName();
        s.setLogo(stored.fileName(), stored.contentType());
        repository.save(s);
        if (previous != null) {
            media.delete(LOGO_FOLDER, previous);
        }
        audit.record("LOGO_UPLOADED", "business_settings", s.getId().toString(),
                Map.of("fileName", stored.fileName(), "contentType", stored.contentType(), "size", stored.size()));
        return toResponse(s);
    }

    @Transactional
    public SettingsDtos.BusinessSettingsResponse removeLogo() {
        BusinessSettings s = current();
        if (s.getLogoFileName() != null) {
            media.delete(LOGO_FOLDER, s.getLogoFileName());
            s.setLogo(null, null);
            repository.save(s);
            audit.record("LOGO_REMOVED", "business_settings", s.getId().toString(), null);
        }
        return toResponse(s);
    }

    SettingsDtos.BusinessSettingsResponse toResponse(BusinessSettings s) {
        var sms = integrations.sms();
        boolean smsConfigured = "AWS".equalsIgnoreCase(sms.provider()) ? integrations.aws().isSmsConfigured() : true;
        var integ = new SettingsDtos.Integrations(
                new SettingsDtos.IntegrationStatus(sms.provider(), sms.enabled(), smsConfigured),
                SettingsDtos.IntegrationStatus.of(integrations.whatsapp().enabled(), integrations.whatsapp().isConfigured()),
                SettingsDtos.IntegrationStatus.of(integrations.viberBusiness().enabled(), integrations.viberBusiness().isConfigured()),
                SettingsDtos.IntegrationStatus.of(integrations.viberBot().enabled(), integrations.viberBot().isConfigured()),
                SettingsDtos.IntegrationStatus.of(integrations.telegram().enabled(), integrations.telegram().isConfigured()));
        return new SettingsDtos.BusinessSettingsResponse(
                s.getBusinessName(), s.getAddress(), s.getPrimaryPhone(), s.getSecondaryPhone(), s.getWorkingHours(),
                s.getDefaultLanguage(), s.getTimeZone(), s.getCurrency(),
                s.getLogoFileName() == null ? null : LOGO_URL_PREFIX + s.getLogoFileName(),
                integ, s.getUpdatedAt());
    }

    private static Map<String, Object> snapshot(BusinessSettings s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("businessName", s.getBusinessName());
        m.put("address", s.getAddress());
        m.put("primaryPhone", PhoneNumberNormalizer.mask(s.getPrimaryPhone()));
        m.put("secondaryPhone", s.getSecondaryPhone() == null ? null : PhoneNumberNormalizer.mask(s.getSecondaryPhone()));
        m.put("workingHours", s.getWorkingHours());
        m.put("defaultLanguage", s.getDefaultLanguage());
        m.put("timeZone", s.getTimeZone());
        m.put("currency", s.getCurrency());
        return m;
    }
}
