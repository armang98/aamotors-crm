package com.aamotors.crm.messaging.channel;

import com.aamotors.crm.audit.AuditService;
import com.aamotors.crm.common.error.ApiException;
import com.aamotors.crm.customer.Customer;
import com.aamotors.crm.customer.CustomerService;
import com.aamotors.crm.settings.IntegrationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Per-customer channel identities: Telegram chat links (deep link + QR) and Viber bot subscriptions. */
@Service
public class CustomerChannelService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TelegramChatLinkRepository telegram;
    private final ViberSubscriberRepository viber;
    private final ChannelLinkCodeRepository codes;
    private final CustomerService customers;
    private final ChannelRegistry registry;
    private final IntegrationProperties integrations;
    private final AuditService audit;
    private final Clock clock;

    public CustomerChannelService(TelegramChatLinkRepository telegram, ViberSubscriberRepository viber, ChannelLinkCodeRepository codes,
                                  CustomerService customers, ChannelRegistry registry, IntegrationProperties integrations, AuditService audit, Clock clock) {
        this.telegram = telegram;
        this.viber = viber;
        this.codes = codes;
        this.customers = customers;
        this.registry = registry;
        this.integrations = integrations;
        this.audit = audit;
        this.clock = clock;
    }

    public record LinkCode(String code, String deepLink, String qrSvg, Instant expiresAt) {}

    @Transactional(readOnly = true)
    public Map<String, Object> channelsOf(UUID customerId) {
        Customer c = customers.require(customerId);
        Map<String, Object> out = new LinkedHashMap<>();
        for (ChannelProvider p : registry.inFallbackOrder()) {
            var e = p.eligibility(c);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("eligible", e.eligible());
            entry.put("reason", e.reason());
            entry.put("mock", p.isMock());
            if (p.channel() == Channel.TELEGRAM) {
                var link = telegram.findByCustomerId(customerId).orElse(null);
                entry.put("linked", link != null && link.isLinked());
                entry.put("chatId", link == null || link.getChatId() == null ? null : mask(String.valueOf(link.getChatId())));
                entry.put("username", link == null ? null : link.getUsername());
                entry.put("verifiedAt", link == null ? null : link.getVerifiedAt());
                entry.put("blockedAt", link == null ? null : link.getBlockedAt());
                entry.put("pendingCode", link != null && link.getLinkCode() != null && link.getCodeExpiresAt() != null && link.getCodeExpiresAt().isAfter(Instant.now(clock)));
            }
            if (p.channel() == Channel.VIBER_BOT) {
                var subs = viber.findByCustomerIdAndSubscribedTrue(customerId);
                entry.put("subscribed", !subs.isEmpty());
                entry.put("userId", subs.isEmpty() ? null : mask(subs.get(0).getViberUserId()));
            }
            out.put(camel(p.channel()), entry);
        }
        return out;
    }

    @Transactional
    public LinkCode issueTelegramLinkCode(UUID customerId, String botUsername) {
        customers.require(customerId);
        Instant now = Instant.now(clock);
        TelegramChatLink link = telegram.findByCustomerId(customerId).orElseGet(() -> telegram.save(new TelegramChatLink(customerId, now)));
        String code = randomCode(6);
        link.issueCode(code, now.plus(Duration.ofHours(24)));
        String deepLink = "https://t.me/" + (botUsername == null || botUsername.isBlank() ? "<bot>" : botUsername) + "?start=" + code;
        audit.record("TELEGRAM_LINK_CODE_ISSUED", "customer", customerId.toString(), Map.of("expiresAt", link.getCodeExpiresAt().toString()));
        return new LinkCode(code, deepLink, QrSvg.render(deepLink), link.getCodeExpiresAt());
    }

    /** Called by the Telegram webhook (Phase 7) or the mock start endpoint when a user sends /start &lt;code&gt;. */
    @Transactional
    public TelegramChatLink completeTelegramLink(String code, long chatId, String username) {
        TelegramChatLink link = telegram.findByLinkCode(code == null ? "" : code.trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Unknown or expired link code"));
        if (link.getCodeExpiresAt() == null || link.getCodeExpiresAt().isBefore(Instant.now(clock))) {
            throw ApiException.badRequest("telegram.codeExpired", "Link code expired");
        }
        telegram.findByChatId(chatId).filter(other -> !other.getId().equals(link.getId())).ifPresent(other -> {
            other.unlink();
            audit.record("system", "TELEGRAM_CHAT_RELINKED", "customer", other.getCustomerId().toString(), Map.of("chatId", mask(String.valueOf(chatId))));
        });
        link.verify(chatId, username, Instant.now(clock));
        audit.record("system", "TELEGRAM_CHAT_LINKED", "customer", link.getCustomerId().toString(), Map.of("chatId", mask(String.valueOf(chatId))));
        return link;
    }

    @Transactional
    public void unlinkTelegram(UUID customerId) {
        telegram.findByCustomerId(customerId).ifPresent(l -> {
            l.unlink();
            audit.record("TELEGRAM_CHAT_UNLINKED", "customer", customerId.toString(), null);
        });
    }

    @Transactional
    public void markTelegramBlocked(long chatId) {
        telegram.findByChatId(chatId).ifPresent(l -> l.markBlocked(Instant.now(clock)));
    }

    /** Viber bot onboarding: one-time code carried in a deep link context or typed in the chat. */
    @Transactional
    public LinkCode issueViberLinkCode(UUID customerId) {
        customers.require(customerId);
        Instant now = Instant.now(clock);
        String code = randomCode(6);
        codes.save(new ChannelLinkCode(code, customerId, Channel.VIBER_BOT, now.plus(Duration.ofHours(24)), now));
        String uri = integrations.viberBot().botUri();
        String deepLink = "viber://pa?chatURI=" + (uri == null || uri.isBlank() ? "<bot-uri>" : uri) + "&context=" + code;
        audit.record("VIBER_LINK_CODE_ISSUED", "customer", customerId.toString(), Map.of("expiresAt", now.plus(Duration.ofHours(24)).toString()));
        return new LinkCode(code, deepLink, QrSvg.render(deepLink), now.plus(Duration.ofHours(24)));
    }

    /** Called by the Viber webhook with the context of a conversation_started event or a code typed in chat. */
    @Transactional
    public Optional<ViberSubscriber> completeViberLink(String code, String viberUserId, String displayName) {
        if (code == null || code.isBlank()) return Optional.empty();
        Instant now = Instant.now(clock);
        return codes.findById(code.trim().toUpperCase()).filter(c -> c.getChannel() == Channel.VIBER_BOT && c.isValid(now)).map(c -> {
            ViberSubscriber s = upsertViberSubscriber(viberUserId, displayName, true);
            s.linkCustomer(c.getCustomerId());
            c.use(now);
            audit.record("system", "VIBER_SUBSCRIBER_LINKED", "customer", c.getCustomerId().toString(), Map.of("viberUserId", mask(viberUserId)));
            return s;
        });
    }

    /** Viber bot: a subscriber record keyed by Viber user id; linking to a customer happens via a phone shared in chat or manually. */
    @Transactional
    public ViberSubscriber upsertViberSubscriber(String viberUserId, String displayName, boolean subscribed) {
        Instant now = Instant.now(clock);
        ViberSubscriber s = viber.findByViberUserId(viberUserId).orElseGet(() -> viber.save(new ViberSubscriber(viberUserId, displayName, now)));
        if (subscribed) s.resubscribe(now); else s.unsubscribe(now);
        return s;
    }

    @Transactional
    public ViberSubscriber linkViberSubscriber(String viberUserId, UUID customerId) {
        customers.require(customerId);
        ViberSubscriber s = viber.findByViberUserId(viberUserId).orElseThrow(() -> ApiException.notFound("Unknown Viber subscriber"));
        s.linkCustomer(customerId);
        audit.record("VIBER_SUBSCRIBER_LINKED", "customer", customerId.toString(), Map.of("viberUserId", mask(viberUserId)));
        return s;
    }

    public String telegramBotUsername() {
        return integrations.telegram().botUsername();
    }

    static String randomCode(int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        return sb.toString();
    }

    static String mask(String s) {
        if (s == null || s.length() < 5) return "***";
        return s.substring(0, 2) + "*".repeat(s.length() - 4) + s.substring(s.length() - 2);
    }

    private static String camel(Channel c) {
        return switch (c) {
            case WHATSAPP -> "whatsapp";
            case VIBER_BUSINESS -> "viberBusiness";
            case TELEGRAM -> "telegram";
            case VIBER_BOT -> "viberBot";
        };
    }
}
