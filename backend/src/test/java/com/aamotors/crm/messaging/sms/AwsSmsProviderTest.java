package com.aamotors.crm.messaging.sms;

import com.aamotors.crm.settings.IntegrationProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.pinpointsmsvoicev2.PinpointSmsVoiceV2Client;
import software.amazon.awssdk.services.pinpointsmsvoicev2.model.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Unit tests with a mocked AWS SDK client — no AWS account needed. */
class AwsSmsProviderTest {

    private final IntegrationProperties.Aws aws = new IntegrationProperties.Aws("eu-central-1", "arn:aws:sms-voice:eu-central-1:123456789012:sender-id/AAMOTORS/AM", "crm-sms-events");
    private final SmsProvider.SmsMessage message = new SmsProvider.SmsMessage("n1:1", "+37494889881", "Հարգելի հաճախորդ, Ձեր Toyota Camry պատրաստ է։", "hy");

    @Test
    void sendsTransactionalMessageWithOriginationIdentityAndConfigurationSet() {
        PinpointSmsVoiceV2Client client = mock(PinpointSmsVoiceV2Client.class);
        when(client.sendTextMessage(any(SendTextMessageRequest.class)))
                .thenReturn(SendTextMessageResponse.builder().messageId("aws-msg-123").build());

        var result = new AwsSmsProvider(client, aws, true).send(message);

        assertThat(result.outcome()).isEqualTo(SmsProvider.SmsSendResult.Outcome.ACCEPTED);
        assertThat(result.providerMessageId()).isEqualTo("aws-msg-123");
        ArgumentCaptor<SendTextMessageRequest> captor = ArgumentCaptor.forClass(SendTextMessageRequest.class);
        verify(client).sendTextMessage(captor.capture());
        SendTextMessageRequest req = captor.getValue();
        assertThat(req.destinationPhoneNumber()).isEqualTo("+37494889881");
        assertThat(req.originationIdentity()).isEqualTo(aws.smsOriginationIdentity());
        assertThat(req.messageType()).isEqualTo(MessageType.TRANSACTIONAL);
        assertThat(req.configurationSetName()).isEqualTo("crm-sms-events");
        assertThat(req.messageBody()).contains("Toyota Camry");
        assertThat(req.context()).containsEntry("idempotencyKey", "n1:1");
    }

    @Test
    void throttlingIsRetryable_validationIsPermanent_clientErrorIsUncertain() {
        PinpointSmsVoiceV2Client client = mock(PinpointSmsVoiceV2Client.class);
        when(client.sendTextMessage(any(SendTextMessageRequest.class)))
                .thenThrow(ThrottlingException.builder().message("slow down").build())
                .thenThrow(ValidationException.builder().message("bad number +37494889881").build())
                .thenThrow(SdkClientException.create("Read timed out"));
        var p = new AwsSmsProvider(client, aws, true);

        var r1 = p.send(message);
        assertThat(r1.outcome()).isEqualTo(SmsProvider.SmsSendResult.Outcome.FAILED);
        assertThat(r1.retryable()).isTrue();

        var r2 = p.send(message);
        assertThat(r2.outcome()).isEqualTo(SmsProvider.SmsSendResult.Outcome.FAILED);
        assertThat(r2.retryable()).isFalse();
        assertThat(r2.error()).doesNotContain("+37494889881"); // phone numbers are masked in stored errors

        var r3 = p.send(message);
        assertThat(r3.outcome()).isEqualTo(SmsProvider.SmsSendResult.Outcome.UNCERTAIN);
    }

    @Test
    void neverCallsAwsWhenDisabledOrUnconfigured() {
        PinpointSmsVoiceV2Client client = mock(PinpointSmsVoiceV2Client.class);
        var disabled = new AwsSmsProvider(client, aws, false).send(message);
        assertThat(disabled.outcome()).isEqualTo(SmsProvider.SmsSendResult.Outcome.FAILED);
        var unconfigured = new AwsSmsProvider(client, new IntegrationProperties.Aws("eu-central-1", "", ""), true).send(message);
        assertThat(unconfigured.outcome()).isEqualTo(SmsProvider.SmsSendResult.Outcome.FAILED);
        verifyNoInteractions(client);
    }
}
