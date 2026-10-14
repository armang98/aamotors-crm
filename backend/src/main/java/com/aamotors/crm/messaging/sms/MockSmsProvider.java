package com.aamotors.crm.messaging.sms;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Simulates a gateway. Accepts everything except two magic suffixes used by tests
 * and demos: numbers ending in {@code 0000} fail permanently, numbers ending in
 * {@code 9999} fail with a retryable error, and {@code 8888} times out (uncertain).
 * Sent messages are kept in memory so the UI and tests can inspect them.
 */
public class MockSmsProvider implements SmsProvider {

    public record Sent(String providerMessageId, String recipient, String body, String language, java.time.Instant at) {}

    private final List<Sent> sent = new CopyOnWriteArrayList<>();

    @Override public String name() { return "MOCK"; }
    @Override public boolean isConfigured() { return true; }

    @Override
    public SmsSendResult send(SmsMessage m) {
        if (m.recipientE164().endsWith("0000")) return SmsSendResult.failed("MOCK: invalid destination (simulated)");
        if (m.recipientE164().endsWith("9999")) return SmsSendResult.retry("MOCK: throttled (simulated)");
        if (m.recipientE164().endsWith("8888")) return SmsSendResult.uncertain("MOCK: timeout (simulated)");
        String id = "mock-" + UUID.randomUUID();
        sent.add(new Sent(id, m.recipientE164(), m.body(), m.language(), java.time.Instant.now()));
        return SmsSendResult.accepted(id);
    }

    public List<Sent> sentMessages() { return List.copyOf(sent); }
    public void clear() { sent.clear(); }
}
