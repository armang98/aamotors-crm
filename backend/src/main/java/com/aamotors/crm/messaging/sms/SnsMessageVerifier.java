package com.aamotors.crm.messaging.sms;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Verifies Amazon SNS message signatures as documented in
 * "Verifying the signatures of Amazon SNS messages": the signing certificate must
 * be served from an amazonaws.com SNS host over HTTPS, and the canonical string is
 * rebuilt from the message fields in the documented order.
 */
@Component
public class SnsMessageVerifier {

    private static final Logger log = LoggerFactory.getLogger(SnsMessageVerifier.class);
    private static final Pattern CERT_HOST = Pattern.compile("^sns\\.[a-z0-9-]+\\.amazonaws\\.com(\\.cn)?$");
    private static final Map<String, X509Certificate> CERT_CACHE = new ConcurrentHashMap<>();

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public boolean verify(JsonNode msg) {
        try {
            String certUrl = msg.path("SigningCertURL").asText("");
            URI uri = URI.create(certUrl);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || !CERT_HOST.matcher(uri.getHost()).matches() || !certUrl.endsWith(".pem")) {
                return false;
            }
            String version = msg.path("SignatureVersion").asText("1");
            String algo = "2".equals(version) ? "SHA256withRSA" : "SHA1withRSA";
            X509Certificate cert = CERT_CACHE.computeIfAbsent(certUrl, this::download);
            if (cert == null) return false;
            cert.checkValidity();
            Signature sig = Signature.getInstance(algo);
            sig.initVerify(cert.getPublicKey());
            sig.update(canonical(msg).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return sig.verify(Base64.getDecoder().decode(msg.path("Signature").asText("")));
        } catch (Exception e) {
            log.warn("SNS signature verification error: {}", e.getMessage());
            return false;
        }
    }

    public void confirmSubscription(String subscribeUrl) {
        try {
            URI uri = URI.create(subscribeUrl);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || !CERT_HOST.matcher(uri.getHost()).matches()) {
                log.warn("Ignoring SNS SubscribeURL on unexpected host");
                return;
            }
            http.send(HttpRequest.newBuilder(uri).GET().timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.discarding());
            log.info("Confirmed SNS subscription");
        } catch (Exception e) {
            log.warn("Could not confirm SNS subscription: {}", e.getMessage());
        }
    }

    static String canonical(JsonNode msg) {
        List<String> keys = "Notification".equals(msg.path("Type").asText())
                ? List.of("Message", "MessageId", "Subject", "Timestamp", "TopicArn", "Type")
                : List.of("Message", "MessageId", "SubscribeURL", "Timestamp", "Token", "TopicArn", "Type");
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            if (msg.hasNonNull(k)) {
                sb.append(k).append('\n').append(msg.get(k).asText()).append('\n');
            }
        }
        return sb.toString();
    }

    private X509Certificate download(String url) {
        try {
            HttpResponse<InputStream> res = http.send(HttpRequest.newBuilder(URI.create(url)).GET().timeout(Duration.ofSeconds(10)).build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = res.body()) {
                return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
            }
        } catch (Exception e) {
            log.warn("Could not download SNS signing certificate: {}", e.getMessage());
            return null;
        }
    }
}
