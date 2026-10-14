package com.aamotors.crm.common.phone;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumberNormalizerTest {

    @ParameterizedTest
    @CsvSource({
            "+37494889881, +37494889881",
            "094889881, +37494889881",
            "094 88 98 81, +37494889881",
            "(093) 09-94-73, +37493099473",
            "0037493099473, +37493099473",
            "+7 912 345 67 89, +79123456789",
            "+1 (415) 555-2671, +14155552671"
    })
    void normalisesToE164(String input, String expected) {
        assertThat(PhoneNumberNormalizer.normalize(input)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "12345", "abc", "+374", "+9999999999999999"})
    void rejectsInvalid(String input) {
        assertThat(PhoneNumberNormalizer.normalize(input)).isEmpty();
        assertThat(PhoneNumberNormalizer.isValid(input)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "094889881, +37494889881",
            "0937766, 937766",
            "93 77 66, 937766",
            "0037493, 37493",
            "+37493, 37493"
    })
    void searchFragments(String input, String expected) {
        assertThat(PhoneNumberNormalizer.searchFragment(input)).contains(expected);
    }

    @Test
    void searchFragmentNeedsThreeDigits() {
        assertThat(PhoneNumberNormalizer.searchFragment("ab 12")).isEmpty();
    }

    @Test
    void masksForLogs() {
        assertThat(PhoneNumberNormalizer.mask("+37494889881")).isEqualTo("+374*****881");
        assertThat(PhoneNumberNormalizer.mask("12")).isEqualTo("***");
        assertThat(PhoneNumberNormalizer.mask(null)).isEqualTo("***");
    }
}
