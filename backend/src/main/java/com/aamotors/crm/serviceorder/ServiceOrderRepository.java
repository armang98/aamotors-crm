package com.aamotors.crm.serviceorder;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceOrderRepository extends JpaRepository<ServiceOrder, UUID> {

    @Query("select o from ServiceOrder o join fetch o.vehicle join fetch o.customer where o.id = :id")
    Optional<ServiceOrder> findDetailed(@Param("id") UUID id);

    @Query("""
            select o from ServiceOrder o join fetch o.vehicle v join fetch o.customer c
            where (:status is null or o.status = :status)
              and ( lower(o.orderNumber) like concat('%', :text, '%')
                 or lower(c.fullName) like concat('%', :text, '%')
                 or lower(v.brand) like concat('%', :text, '%')
                 or lower(v.model) like concat('%', :text, '%')
                 or v.licensePlateNormalized like concat('%', :plate, '%')
                 or (v.vin is not null and v.vin like concat('%', :upper, '%'))
                 or c.phone like concat('%', :phone, '%') )
            order by o.createdAt desc
            """)
    Page<ServiceOrder> search(@Param("text") String lowerText, @Param("plate") String plate, @Param("upper") String upper,
                              @Param("phone") String phone, @Param("status") ServiceOrderStatus status, Pageable pageable);

    @Query("select o from ServiceOrder o join fetch o.vehicle join fetch o.customer where o.vehicle.id = :vehicleId order by o.createdAt desc")
    List<ServiceOrder> findByVehicle(@Param("vehicleId") UUID vehicleId);

    @Query(value = "select nextval('service_order_number_seq')", nativeQuery = true)
    long nextOrderNumber();
}
