package com.aamotors.crm.campaign;

import com.aamotors.crm.messaging.channel.Channel;
import com.aamotors.crm.vehicle.OilType;

import java.time.LocalDate;
import java.util.List;

/** Customer segment definition, stored as JSON on the campaign. All fields optional (AND-combined). */
public record CampaignFilters(
        String brand, String model,
        LocalDate lastServiceFrom, LocalDate lastServiceTo,
        OilType oilType, String oilName,
        String oilFilterName, String oilFilterCode,
        String cabinFilterName, String cabinFilterCode,
        String airFilterName, String airFilterCode,
        Integer nextOilChangeMileageMax,
        List<Channel> consentChannels) {

    public static CampaignFilters empty() {
        return new CampaignFilters(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
