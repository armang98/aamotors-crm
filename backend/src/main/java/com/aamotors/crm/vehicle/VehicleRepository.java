package com.aamotors.crm.vehicle;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    Optional<Vehicle> findByVin(String vin);

    long countByCustomerId(UUID customerId);

    List<Vehicle> findByCustomerIdOrderByCreatedAtAsc(UUID customerId);

    @Query("""
            select v from Vehicle v join fetch v.customer c
            where lower(v.brand) like concat('%', :text, '%')
               or lower(v.model) like concat('%', :text, '%')
               or v.licensePlateNormalized like concat('%', :plate, '%')
               or (v.vin is not null and v.vin like concat('%', :vinOrPlate, '%'))
               or c.phone like concat('%', :phone, '%')
            """)
    Page<Vehicle> search(@Param("text") String lowerText, @Param("plate") String plate,
                         @Param("vinOrPlate") String upper, @Param("phone") String phone, Pageable pageable);

    @Query("select v from Vehicle v join fetch v.customer")
    Page<Vehicle> findAllWithCustomer(Pageable pageable);

    /** Vehicles whose next oil change is within kmWindow km (or already passed) or whose next service date is near. */
    @Query("""
            select v from Vehicle v join fetch v.customer
            where (v.nextOilChangeMileageKm is not null and v.nextOilChangeMileageKm - v.currentMileageKm <= :kmWindow)
               or (v.nextServiceDate is not null and v.nextServiceDate <= :untilDate)
            order by v.nextOilChangeMileageKm - v.currentMileageKm asc nulls last, v.nextServiceDate asc nulls last
            """)
    List<Vehicle> findDue(@Param("kmWindow") int kmWindow, @Param("untilDate") LocalDate untilDate);
}
