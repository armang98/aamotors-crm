package com.aamotors.crm.settings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BusinessSettingsRepository extends JpaRepository<BusinessSettings, UUID> {
    Optional<BusinessSettings> findBySingletonKey(short key);
}
