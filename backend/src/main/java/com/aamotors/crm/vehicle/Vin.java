package com.aamotors.crm.vehicle;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * VIN handling. Format check only (ISO 3779: 17 characters, A–Z and 0–9, never I, O or Q).
 * The North-American check digit is not enforced because most vehicles serviced in
 * Armenia are European or Asian imports whose VINs do not carry one.
 */
public final class Vin {

    private static final Pattern FORMAT = Pattern.compile("^[A-HJ-NPR-Z0-9]{17}$");

    private Vin() {}

    /** @return the normalised (upper-case, trimmed) VIN, or empty when the input is blank. */
    public static Optional<String> normalize(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        return Optional.of(raw.trim().toUpperCase().replace(" ", "").replace("-", ""));
    }

    public static boolean isValid(String normalized) {
        return normalized != null && FORMAT.matcher(normalized).matches();
    }

    public static String normalizePlate(String plate) {
        return plate == null ? null : plate.trim().toUpperCase().replaceAll("[\\s\\-]", "");
    }
}
