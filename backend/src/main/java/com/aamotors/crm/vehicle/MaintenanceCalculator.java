package com.aamotors.crm.vehicle;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Derives a maintenance status from the vehicle's last known mileage and dates.
 * It never assumes the vehicle has driven further than the last recorded reading.
 */
public final class MaintenanceCalculator {

    public static final int DUE_SOON_KM = 1000;
    public static final int DUE_SOON_DAYS = 14;

    private MaintenanceCalculator() {}

    public static VehicleDtos.Maintenance evaluate(Vehicle v, LocalDate today) {
        Integer kmRemaining = v.getNextOilChangeMileageKm() == null ? null : v.getNextOilChangeMileageKm() - v.getCurrentMileageKm();
        Long daysRemaining = v.getNextServiceDate() == null ? null : ChronoUnit.DAYS.between(today, v.getNextServiceDate());

        if (kmRemaining == null && daysRemaining == null) {
            return new VehicleDtos.Maintenance(VehicleDtos.MaintenanceStatus.UNKNOWN, null, null);
        }
        boolean overdue = (kmRemaining != null && kmRemaining <= 0) || (daysRemaining != null && daysRemaining < 0);
        boolean dueSoon = (kmRemaining != null && kmRemaining <= DUE_SOON_KM) || (daysRemaining != null && daysRemaining <= DUE_SOON_DAYS);
        var status = overdue ? VehicleDtos.MaintenanceStatus.OVERDUE
                : dueSoon ? VehicleDtos.MaintenanceStatus.DUE_SOON
                : VehicleDtos.MaintenanceStatus.OK;
        return new VehicleDtos.Maintenance(status, kmRemaining, daysRemaining);
    }
}
