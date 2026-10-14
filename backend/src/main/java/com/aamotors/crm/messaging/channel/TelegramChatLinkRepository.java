package com.aamotors.crm.messaging.channel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TelegramChatLinkRepository extends JpaRepository<TelegramChatLink, UUID> {
    Optional<TelegramChatLink> findByCustomerId(UUID customerId);
    Optional<TelegramChatLink> findByChatId(Long chatId);
    Optional<TelegramChatLink> findByLinkCode(String code);
}
