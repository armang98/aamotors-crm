package com.aamotors.crm.vehicle;

import java.util.Map;

/** Where the replaced oil goes. Labels are Armenian / English as required by the specification. */
public enum OilType {
    ENGINE("Շարժիչ", "Engine"),
    TRANSMISSION("Փոխանցման տուփ", "Transmission"),
    STEERING("Ղեկային համակարգ", "Steering"),
    DIFFERENTIAL("Ռեդուկտոր", "Differential"),
    AXLE("Կամուրջ", "Axle"),
    TRANSFER_CASE("Բաշխիչ տուփ", "Transfer Case");

    private final String hy;
    private final String en;

    OilType(String hy, String en) {
        this.hy = hy;
        this.en = en;
    }

    public Map<String, String> labels() {
        return Map.of("hy", hy, "en", en);
    }

    public String label(String lang) {
        return "en".equals(lang) ? en : hy;
    }
}
