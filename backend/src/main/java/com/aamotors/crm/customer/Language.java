package com.aamotors.crm.customer;

/** UI / notification languages supported by the business. */
public enum Language {
    hy, en;

    public static Language fromCode(String code) {
        return code == null ? hy : Language.valueOf(code);
    }
}
