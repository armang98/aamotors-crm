package com.aamotors.crm.common.demo;

import com.aamotors.crm.customer.ConsentChannel;
import com.aamotors.crm.customer.Customer;
import com.aamotors.crm.customer.CustomerConsent;
import com.aamotors.crm.customer.CustomerRepository;
import com.aamotors.crm.customer.Language;
import com.aamotors.crm.vehicle.OilType;
import com.aamotors.crm.vehicle.Vehicle;
import com.aamotors.crm.vehicle.VehicleRepository;
import com.aamotors.crm.vehicle.VehicleServiceRecord;
import com.aamotors.crm.vehicle.VehicleServiceRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Inserts a handful of demo customers, vehicles and service visits for local
 * development. Enabled only with {@code SEED_DEMO_DATA=true}; never runs when
 * customers already exist, so it is safe to leave the flag on for a dev database.
 */
@Component
@Order(10)
@ConditionalOnProperty(name = "app.demo.seed", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final CustomerRepository customers;
    private final VehicleRepository vehicles;
    private final VehicleServiceRecordRepository records;
    private final Clock clock;

    public DemoDataSeeder(CustomerRepository customers, VehicleRepository vehicles,
                          VehicleServiceRecordRepository records, Clock clock) {
        this.customers = customers;
        this.vehicles = vehicles;
        this.records = records;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (customers.count() > 0) {
            return;
        }
        LocalDate today = Instant.now(clock).atZone(ZoneId.of("Asia/Yerevan")).toLocalDate();

        Customer aram = customer("Արամ Պետրոսյան", "+37491234567", Language.hy, true, ConsentChannel.SMS, ConsentChannel.WHATSAPP);
        Customer ani = customer("Անի Սարգսյան", "+37493456789", Language.en, true, ConsentChannel.SMS, ConsentChannel.TELEGRAM);
        Customer gor = customer("Գոռ Հակոբյան", "+37477123123", Language.hy, false);

        Vehicle camry = vehicle(aram, "Toyota", "Camry", "JTNBE46K073012345", "34 AB 567", 120_500);
        visit(camry, today.minusMonths(7), 110_200, 120_200, null, "Castrol Edge 5W-30", OilType.ENGINE,
                "Mann W 712/95", "W712-95", "Bosch Cabin", "CU-2545", "Mahle Air", "LX-1780",
                "Փոխվել է շարժիչի յուղը, յուղի, սրահի և օդի ֆիլտրերը։ Ստուգվել են արգելակները։");
        visit(camry, today.minusDays(20), 120_500, 130_500, today.plusMonths(6), "Castrol Edge 5W-30", OilType.ENGINE,
                "Mann W 712/95", "W712-95", null, null, null, null,
                "Փոխվել են շարժիչի յուղը և յուղի ֆիլտրը։ Լցվել է հովացման հեղուկ։");

        Vehicle rav4 = vehicle(aram, "Toyota", "RAV4", "2T3BFREV8JW812345", "77 OO 901", 64_900);
        visit(rav4, today.minusMonths(2), 60_100, 65_100, today.plusMonths(4), "Mobil 1 0W-20", OilType.ENGINE,
                "Toyota 04152-YZZA1", "04152-YZZA1", "Denso 453-6052", "453-6052", null, null,
                "Replaced engine oil and oil filter, replaced cabin filter. Rotated tyres.");

        Vehicle hyundai = vehicle(ani, "Hyundai", "Tucson", "KMHJ3814GLU123456", "10 LL 220", 88_300);
        // visits are applied oldest → newest so the vehicle state ends up matching the latest visit
        visit(hyundai, today.minusMonths(13), 72_000, 82_000, null, "ZIC ATF SP4", OilType.TRANSMISSION,
                null, null, null, null, null, null, "Replaced automatic transmission fluid.");
        visit(hyundai, today.minusMonths(1), 88_300, 98_300, today.plusMonths(5), "Shell Helix Ultra 5W-40", OilType.ENGINE,
                "Hyundai 26300-35505", "26300-35505", "Hyundai 97133-D1000", "97133-D1000", "Hyundai 28113-D3300", "28113-D3300",
                "Full service: engine oil, oil filter, cabin filter, air filter. Brake pads checked.");

        Vehicle lada = vehicle(gor, "Lada", "Niva", null, "51 SS 777", 143_000);
        visit(lada, today.minusMonths(5), 141_900, 146_900, today.minusDays(3), "Lukoil Genesis 10W-40", OilType.ENGINE,
                "Невский фильтр NF-1001", "NF-1001", null, null, "Невский фильтр NF-5001", "NF-5001",
                "Փոխվել են շարժիչի յուղը, յուղի և օդի ֆիլտրերը։ Փոխվել է ռեդուկտորի յուղը։");
        visit(lada, today.minusMonths(5), 141_900, null, null, "Lukoil 75W-90", OilType.DIFFERENTIAL,
                null, null, null, null, null, null, "Ռեդուկտորի յուղի փոխարինում։");

        log.info("Seeded demo data: {} customers, {} vehicles, {} service records", customers.count(), vehicles.count(), records.count());
    }

    private Customer customer(String name, String phone, Language lang, boolean marketing, ConsentChannel... channels) {
        Customer c = new Customer(name, phone, lang);
        Instant now = Instant.now(clock);
        for (ConsentChannel ch : channels) {
            CustomerConsent consent = new CustomerConsent(c, ch);
            consent.apply(marketing, "DEMO", now);
            c.getConsents().add(consent);
        }
        return customers.save(c);
    }

    private Vehicle vehicle(Customer c, String brand, String model, String vin, String plate, int mileage) {
        return vehicles.save(new Vehicle(c, brand, model, vin, plate, mileage));
    }

    private void visit(Vehicle v, LocalDate date, int mileage, Integer nextOil, LocalDate nextDate, String oilName, OilType type,
                       String oilFilterName, String oilFilterCode, String cabinName, String cabinCode, String airName, String airCode,
                       String description) {
        VehicleServiceRecord r = new VehicleServiceRecord(v);
        r.setServiceDate(date);
        r.setMileageKm(mileage);
        r.setNextOilChangeMileageKm(nextOil);
        r.setNextServiceDate(nextDate);
        r.setOilName(oilName);
        r.setOilType(type);
        r.setOilFilterName(oilFilterName);
        r.setOilFilterCode(oilFilterCode);
        r.setCabinFilterName(cabinName);
        r.setCabinFilterCode(cabinCode);
        r.setAirFilterName(airName);
        r.setAirFilterCode(airCode);
        r.setDescription(description);
        records.save(r);
        if (nextOil != null) v.setNextOilChangeMileageKm(nextOil);
        if (nextDate != null) v.setNextServiceDate(nextDate);
        v.setCurrentMileageKm(Math.max(v.getCurrentMileageKm(), mileage));
    }
}
