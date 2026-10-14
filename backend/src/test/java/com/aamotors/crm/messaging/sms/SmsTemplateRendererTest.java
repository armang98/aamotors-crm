package com.aamotors.crm.messaging.sms;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SmsTemplateRendererTest {

    @Test
    void rendersPlaceholdersAndDropsMissingOnes() {
        String out = SmsTemplateRenderer.render("A {{x}} B {{missing}} C", Map.of("x", "1"));
        assertThat(out).isEqualTo("A 1 B C");
    }

    @Test
    void armenianTextIsUnicodeWith70CharSegments() {
        var s = SmsTemplateRenderer.segments("Հարգելի հաճախորդ, Ձեր ավտոմեքենան պատրաստ է։ Կարող եք մոտենալ և վերցնել այն։ Հասցե՝ Գյումրի։");
        assertThat(s.unicode()).isTrue();
        assertThat(s.segments()).isEqualTo(2);
        var en = SmsTemplateRenderer.segments("Your car is ready.");
        assertThat(en.unicode()).isFalse();
        assertThat(en.segments()).isEqualTo(1);
    }
}
