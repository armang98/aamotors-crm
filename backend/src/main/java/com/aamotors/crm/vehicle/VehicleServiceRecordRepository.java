package com.aamotors.crm.vehicle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface VehicleServiceRecordRepository extends JpaRepository<VehicleServiceRecord, UUID> {

    List<VehicleServiceRecord> findByVehicleIdOrderByServiceDateDescCreatedAtDesc(UUID vehicleId);

    Optional<VehicleServiceRecord> findFirstByVehicleIdOrderByServiceDateDescCreatedAtDesc(UUID vehicleId);

    long countByVehicleId(UUID vehicleId);

    @Query("select r.vehicle.id as vehicleId, count(r) as cnt, max(r.serviceDate) as lastDate from VehicleServiceRecord r where r.vehicle.id in :ids group by r.vehicle.id")
    List<VehicleStats> statsFor(@Param("ids") List<UUID> ids);

    interface VehicleStats {
        UUID getVehicleId();
        long getCnt();
        java.time.LocalDate getLastDate();
    }

    default Map<UUID, VehicleStats> statsMap(List<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return statsFor(ids).stream().collect(java.util.stream.Collectors.toMap(VehicleStats::getVehicleId, s -> s));
    }
}
