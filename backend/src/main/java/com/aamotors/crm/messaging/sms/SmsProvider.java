package com.aamotors.crm.messaging.sms;

/**
 * Outbound SMS gateway abstraction. Implementations: {@link MockSmsProvider} (default,
 * local development and tests) and {@link AwsSmsProvider} (AWS End User Messaging SMS).
 */
public interface SmsProvider {

    String name();

    /** True when the provider has everything it needs to send real messages. */
    boolean isConfigured();

    SmsSendResult send(SmsMessage message);

    record SmsMessage(String idempotencyKey, String recipientE164, String body, String language) {}

    /**
     * Outcome of one provider call. ACCEPTED means the provider took the message; it does
     * NOT mean the handset received it — delivery confirmation arrives asynchronously.
     */
    record SmsSendResult(Outcome outcome, String providerMessageId, String error, boolean retryable) {
        public enum Outcome { ACCEPTED, FAILED, UNCERTAIN }

        public static SmsSendResult accepted(String providerMessageId) { return new SmsSendResult(Outcome.ACCEPTED, providerMessageId, null, false); }
        public static SmsSendResult failed(String error) { return new SmsSendResult(Outcome.FAILED, null, error, false); }
        public static SmsSendResult retry(String error) { return new SmsSendResult(Outcome.FAILED, null, error, true); }
        public static SmsSendResult uncertain(String error) { return new SmsSendResult(Outcome.UNCERTAIN, null, error, false); }
    }
}
