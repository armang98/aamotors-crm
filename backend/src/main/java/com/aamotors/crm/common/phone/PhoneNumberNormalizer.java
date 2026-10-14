package com.aamotors.crm.common.phone;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import java.util.Optional;

/**
 * Normalises phone numbers to E.164. Default region is Armenia (AM), so local
 * formats such as {@code 094 889881} or {@code 094-88-98-81} become {@code +37494889881}.
 */
public final class PhoneNumberNormalizer {

    public static final String DEFAULT_REGION = "AM";

    private static final PhoneNumberUtil UTIL = PhoneNumberUtil.getInstance();

    private PhoneNumberNormalizer() {}

    /** @return E.164 string, or empty when the input is not a valid phone number. */
    public static Optional<String> normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            Phonenumber.PhoneNumber number = UTIL.parse(raw.trim(), DEFAULT_REGION);
            if (!UTIL.isValidNumber(number)) {
                return Optional.empty();
            }
            return Optional.of(UTIL.format(number, PhoneNumberUtil.PhoneNumberFormat.E164));
        } catch (NumberParseException e) {
            return Optional.empty();
        }
    }

    /** @throws IllegalArgumentException when the number is not valid. */
    public static String normalizeOrThrow(String raw) {
        return normalize(raw).orElseThrow(() -> new IllegalArgumentException("Invalid phone number: " + mask(raw)));
    }

    public static boolean isValid(String raw) {
        return normalize(raw).isPresent();
    }

    /**
     * Turns free text into a fragment suitable for {@code LIKE '%fragment%'} against E.164 columns.
     * A complete number becomes its E.164 form; a partial number becomes its digits with the
     * Armenian trunk prefix ("0") or international prefix ("00") removed. Returns empty when the
     * text has fewer than 3 digits, so callers can skip the phone clause.
     */
    public static Optional<String> searchFragment(String raw) {
        if (raw == null) return Optional.empty();
        Optional<String> full = normalize(raw);
        if (full.isPresent()) return full;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.startsWith("00")) digits = digits.substring(2);
        else if (digits.startsWith("0")) digits = digits.substring(1);
        return digits.length() >= 3 ? Optional.of(digits) : Optional.empty();
    }

    /** Masks all but the last 3 digits for logs: +37494889881 -> +374*****881 */
    public static String mask(String phone) {
        if (phone == null || phone.length() < 6) {
            return "***";
        }
        int keepPrefix = phone.startsWith("+") ? 4 : 3;
        int keepSuffix = 3;
        if (phone.length() <= keepPrefix + keepSuffix) {
            return "***";
        }
        return phone.substring(0, keepPrefix)
                + "*".repeat(phone.length() - keepPrefix - keepSuffix)
                + phone.substring(phone.length() - keepSuffix);
    }
}
