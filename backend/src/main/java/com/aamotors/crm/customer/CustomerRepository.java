package com.aamotors.crm.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByPhone(String phone);

    /** Both fragments must be non-null; pass an impossible fragment (e.g. "#") to disable one side. */
    @Query("""
            select c from Customer c
            where c.phone like concat('%', :phone, '%')
               or lower(c.fullName) like concat('%', :name, '%')
            order by c.fullName
            """)
    Page<Customer> search(@Param("phone") String phoneFragment, @Param("name") String nameFragment, Pageable pageable);
}
