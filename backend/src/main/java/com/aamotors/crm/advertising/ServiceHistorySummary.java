package com.aamotors.crm.advertising;

import com.aamotors.crm.customer.Language;
import com.aamotors.crm.vehicle.Vehicle;
import com.aamotors.crm.vehicle.VehicleServiceRecord;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Localized "latest service" block appended to advertising messages. Empty fields are omitted. */
final class ServiceHistorySummary {

    private ServiceHistorySummary() {}

    static String render(Language lang, Vehicle v, VehicleServiceRecord r) {
        boolean hy = lang != Language.en;
        List<String> lines = new ArrayList<>();
        lines.add(hy ? "— Վերջին սպասարկում —" : "— Latest service —");
        lines.add((hy ? "Ավտոմեքենա՝ " : "Vehicle: ") + v.getBrand() + " " + v.getModel() + (v.getLicensePlate() == null ? "" : " (" + v.getLicensePlate() + ")"));
        lines.add((hy ? "Ամսաթիվ՝ " : "Date: ") + r.getServiceDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        lines.add((hy ? "Վազք՝ " : "Mileage: ") + String.format("%,d", r.getMileageKm()).replace(',', ' ') + (hy ? " կմ" : " km"));
        if (r.getOilName() != null || r.getOilType() != null) {
            String oil = (r.getOilName() == null ? "" : r.getOilName()) + (r.getOilType() == null ? "" : " (" + r.getOilType().label(hy ? "hy" : "en") + ")");
            lines.add((hy ? "Յուղ՝ " : "Oil: ") + oil.trim());
        }
        filter(lines, hy ? "Յուղի ֆիլտր՝ " : "Oil filter: ", r.getOilFilterName(), r.getOilFilterCode());
        filter(lines, hy ? "Սրահի ֆիլտր՝ " : "Cabin filter: ", r.getCabinFilterName(), r.getCabinFilterCode());
        filter(lines, hy ? "Օդի ֆիլտր՝ " : "Air filter: ", r.getAirFilterName(), r.getAirFilterCode());
        if (r.getNextOilChangeMileageKm() != null) {
            lines.add((hy ? "Յուղի հաջորդ փոխում՝ " : "Next oil change: ") + String.format("%,d", r.getNextOilChangeMileageKm()).replace(',', ' ') + (hy ? " կմ" : " km"));
        }
        if (r.getDescription() != null && !r.getDescription().isBlank()) {
            lines.add((hy ? "Կատարված աշխատանքներ՝ " : "Work done: ") + r.getDescription().trim());
        }
        return String.join("\n", lines);
    }

    private static void filter(List<String> lines, String label, String name, String code) {
        if (name == null && code == null) return;
        String value = name == null ? "" : name;
        if (code != null) value += (value.isEmpty() ? "" : " ") + "(" + code + ")";
        lines.add(label + value.trim());
    }
}
