package com.aamotors.crm.serviceorder;

import java.util.EnumSet;
import java.util.Set;

public enum ServiceOrderStatus {
    RECEIVED, IN_PROGRESS, WAITING_FOR_PARTS, READY_FOR_PICKUP, DELIVERED, CANCELLED;

    public Set<ServiceOrderStatus> allowedTransitions() {
        return switch (this) {
            case RECEIVED -> EnumSet.of(IN_PROGRESS, WAITING_FOR_PARTS, READY_FOR_PICKUP, CANCELLED);
            case IN_PROGRESS -> EnumSet.of(WAITING_FOR_PARTS, READY_FOR_PICKUP, CANCELLED);
            case WAITING_FOR_PARTS -> EnumSet.of(IN_PROGRESS, READY_FOR_PICKUP, CANCELLED);
            case READY_FOR_PICKUP -> EnumSet.of(DELIVERED, IN_PROGRESS, CANCELLED);
            case DELIVERED, CANCELLED -> EnumSet.noneOf(ServiceOrderStatus.class);
        };
    }
}
