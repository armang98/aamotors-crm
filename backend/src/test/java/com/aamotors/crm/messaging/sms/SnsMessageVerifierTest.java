package com.aamotors.crm.messaging.sms;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SnsMessageVerifierTest {

    @Test
    void rejectsCertificatesNotHostedByAmazonSns() throws Exception {
        var verifier = new SnsMessageVerifier();
        var json = new ObjectMapper().readTree("""
                {"Type":"Notification","MessageId":"1","TopicArn":"arn:aws:sns:eu-central-1:1:t","Message":"{}",
                 "Timestamp":"2026-10-12T00:00:00.000Z","SignatureVersion":"1","Signature":"AAAA",
                 "SigningCertURL":"https://evil.example.com/SimpleNotificationService-abc.pem"}
                """);
        assertThat(verifier.verify(json)).isFalse();
        var http = new ObjectMapper().readTree("""
                {"Type":"Notification","SigningCertURL":"http://sns.eu-central-1.amazonaws.com/x.pem","Signature":"AAAA"}
                """);
        assertThat(verifier.verify(http)).isFalse();
    }

    @Test
    void canonicalStringFollowsDocumentedOrder() throws Exception {
        var json = new ObjectMapper().readTree("""
                {"Type":"Notification","MessageId":"m","TopicArn":"t","Message":"body","Timestamp":"ts","Subject":"s"}
                """);
        assertThat(SnsMessageVerifier.canonical(json))
                .isEqualTo("Message\nbody\nMessageId\nm\nSubject\ns\nTimestamp\nts\nTopicArn\nt\nType\nNotification\n");
    }
}
