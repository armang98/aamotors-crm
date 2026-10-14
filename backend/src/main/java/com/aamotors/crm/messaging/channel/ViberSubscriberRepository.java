package com.aamotors.crm.messaging.channel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ViberSubscriberRepository extends JpaRepository<ViberSubscriber, UUID> {
    Optional<ViberSubscriber> findByViberUserId(String viberUserId);
    List<ViberSubscriber> findByCustomerIdAndSubscribedTrue(UUID customerId);
}
