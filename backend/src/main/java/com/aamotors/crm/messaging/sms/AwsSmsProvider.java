package com.aamotors.crm.messaging.sms;

import com.aamotors.crm.common.phone.PhoneNumberNormalizer;
import com.aamotors.crm.settings.IntegrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.pinpointsmsvoicev2.PinpointSmsVoiceV2Client;
import software.amazon.awssdk.services.pinpointsmsvoicev2.model.*;

/**
 * AWS End User Messaging SMS adapter (AWS SDK for Java v2, Pinpoint SMS Voice V2
 * {@code SendTextMessage}). Credentials come from the default provider chain
 * (environment, profile, IAM role). Nothing is sent unless {@code SMS_ENABLED=true},
 * {@code SMS_PROVIDER=AWS} and an origination identity registered for Armenia is set —
 * see docs/aws-sms-setup.md.
 */
public class AwsSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(AwsSmsProvider.class);

    private final PinpointSmsVoiceV2Client client;
    private final IntegrationProperties.Aws aws;
    private final boolean enabled;

    public AwsSmsProvider(PinpointSmsVoiceV2Client client, IntegrationProperties.Aws aws, boolean enabled) {
        this.client = client;
        this.aws = aws;
        this.enabled = enabled;
    }

    @Override public String name() { return "AWS"; }
    @Override public boolean isConfigured() { return aws.isSmsConfigured(); }

    @Override
    public SmsSendResult send(SmsMessage m) {
        if (!enabled) {
            return SmsSendResult.failed("AWS SMS sending is disabled (SMS_ENABLED=false)");
        }
        if (!isConfigured()) {
            return SmsSendResult.failed("AWS SMS is not configured (region / origination identity missing)");
        }
        SendTextMessageRequest.Builder req = SendTextMessageRequest.builder()
                .destinationPhoneNumber(m.recipientE164())
                .originationIdentity(aws.smsOriginationIdentity())
                .messageBody(m.body())
                .messageType(MessageType.TRANSACTIONAL);
        if (aws.smsConfigurationSet() != null && !aws.smsConfigurationSet().isBlank()) {
            req.configurationSetName(aws.smsConfigurationSet());
        }
        // Context keys appear in delivery events and let the webhook correlate without exposing content.
        req.context(java.util.Map.of("idempotencyKey", m.idempotencyKey()));
        try {
            SendTextMessageResponse res = client.sendTextMessage(req.build());
            log.info("AWS SMS accepted for {} messageId={}", PhoneNumberNormalizer.mask(m.recipientE164()), res.messageId());
            return SmsSendResult.accepted(res.messageId());
        } catch (ThrottlingException | ServiceQuotaExceededException | InternalServerException e) {
            return SmsSendResult.retry("AWS " + e.getClass().getSimpleName() + ": " + safe(e.getMessage()));
        } catch (ValidationException | ResourceNotFoundException | AccessDeniedException | ConflictException e) {
            return SmsSendResult.failed("AWS " + e.getClass().getSimpleName() + ": " + safe(e.getMessage()));
        } catch (SdkClientException e) {
            // network / timeout: the request may or may not have reached AWS
            return SmsSendResult.uncertain("AWS client error: " + safe(e.getMessage()));
        } catch (PinpointSmsVoiceV2Exception e) {
            return e.statusCode() >= 500 ? SmsSendResult.retry("AWS " + e.statusCode() + ": " + safe(e.getMessage()))
                    : SmsSendResult.failed("AWS " + e.statusCode() + ": " + safe(e.getMessage()));
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s.replaceAll("\\+?\\d{8,}", "<phone>");
    }
}
