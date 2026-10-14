package com.aamotors.crm.messaging.sms;

import com.aamotors.crm.customer.Language;
import com.aamotors.crm.settings.BusinessSettings;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Renders the localized "vehicle ready" SMS exactly as specified. */
public final class SmsTemplateRenderer {

    public static final Map<Language, String> VEHICLE_READY = Map.of(
            Language.hy, "Հարգելի հաճախորդ, Ձեր {{vehicleBrand}} {{vehicleModel}} ({{licensePlate}}) ավտոմեքենան պատրաստ է։ Կարող եք մոտենալ և վերցնել այն։ Հասցե՝ {{businessAddress}}։ Հեռ․՝ {{businessPhone}}։ A&A Motors",
            Language.en, "Your {{vehicleBrand}} {{vehicleModel}} ({{licensePlate}}) is ready for pickup at A&A Motors. Address: {{businessAddress}}. Phone: {{businessPhone}}.");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    private SmsTemplateRenderer() {}

    public static String renderVehicleReady(Language language, String brand, String model, String plate, BusinessSettings settings) {
        Map<String, String> vars = Map.of(
                "vehicleBrand", nz(brand), "vehicleModel", nz(model), "licensePlate", nz(plate),
                "businessAddress", nz(settings.getAddress()), "businessPhone", nz(settings.getPrimaryPhone()),
                "businessName", nz(settings.getBusinessName()));
        return render(VEHICLE_READY.getOrDefault(language, VEHICLE_READY.get(Language.hy)), vars);
    }

    public static String render(String template, Map<String, String> vars) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(vars.getOrDefault(m.group(1), "")));
        }
        m.appendTail(sb);
        return sb.toString().replaceAll("[ ]{2,}", " ").trim();
    }

    /** GSM-7 vs UCS-2 segment estimate (Armenian always forces UCS-2: 70 / 67 chars per segment). */
    public record Segments(int chars, int segments, boolean unicode) {}

    public static Segments segments(String text) {
        boolean unicode = text.chars().anyMatch(c -> c > 0x7F && "€£¥èéùìòÇØøÅåΔΦΓΛΩΠΨΣΘΞÆæßÉÄÖÑÜ§¿äöñüà".indexOf(c) < 0);
        int len = text.codePointCount(0, text.length());
        int single = unicode ? 70 : 160;
        int multi = unicode ? 67 : 153;
        int segs = len <= single ? 1 : (int) Math.ceil(len / (double) multi);
        return new Segments(len, segs, unicode);
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
