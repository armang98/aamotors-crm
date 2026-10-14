package com.aamotors.crm.messaging.channel;

import com.aamotors.crm.customer.Customer;

/**
 * One outbound channel. Implementations decide eligibility (consent, identity,
 * configuration) and perform a single send. They never fall back to another channel —
 * that is the orchestrator's job.
 */
public interface ChannelProvider {

    Channel channel();

    /** True when real credentials are present (regardless of the enabled flag). */
    boolean isConfigured();

    /** True when sending is switched on. Mock providers are always enabled. */
    boolean isEnabled();

    boolean isMock();

    /** Human-readable note for the UI (e.g. "awaiting partner credentials"). */
    String note();

    Eligibility eligibility(Customer customer);

    SendResult send(OutboundMessage message);

    record Eligibility(boolean eligible, String reason) {
        public static Eligibility ok() { return new Eligibility(true, null); }
        public static Eligibility no(String reason) { return new Eligibility(false, reason); }
    }

    record OutboundMessage(String idempotencyKey, Customer customer, String text, String imageUrl, String language) {}

    /**
     * ACCEPTED: provider took the message. FAILED: conclusive rejection → next channel.
     * RETRY: transient provider condition (rate limit, 5xx) → same channel later, bounded.
     * UNCERTAIN: outcome unknown (timeout) → stop, manual reconciliation.
     */
    record SendResult(Outcome outcome, String providerMessageId, String error) {
        public enum Outcome { ACCEPTED, FAILED, RETRY, UNCERTAIN }
        public static SendResult accepted(String id) { return new SendResult(Outcome.ACCEPTED, id, null); }
        public static SendResult failed(String error) { return new SendResult(Outcome.FAILED, null, error); }
        public static SendResult retry(String error) { return new SendResult(Outcome.RETRY, null, error); }
        public static SendResult uncertain(String error) { return new SendResult(Outcome.UNCERTAIN, null, error); }
    }

    // reason codes shared with the frontend
    String NO_CONSENT = "NO_CONSENT";
    String OPTED_OUT = "OPTED_OUT";
    String NOT_CONFIGURED = "NOT_CONFIGURED";
    String DISABLED = "DISABLED";
    String NO_IDENTITY = "NO_IDENTITY";
    String BLOCKED = "BLOCKED";
    String NO_PHONE = "NO_PHONE";
    String TEMPLATE_REQUIRED = "TEMPLATE_REQUIRED";
}
