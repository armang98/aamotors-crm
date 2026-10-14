package com.aamotors.crm.messaging.http;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class JavaJsonHttpClient implements JsonHttpClient {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Override
    public Response postJson(String url, Map<String, String> headers, String jsonBody) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json").header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        headers.forEach(b::header);
        return send(b.build());
    }

    @Override
    public Response postForm(String url, Map<String, String> headers, Map<String, String> form) {
        String body = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded").header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        headers.forEach(b::header);
        return send(b.build());
    }

    @Override
    public Response get(String url, Map<String, String> headers) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET();
        headers.forEach(b::header);
        return send(b.build());
    }

    private Response send(HttpRequest req) {
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(res.statusCode(), res.body());
        } catch (IOException e) {
            throw new TransportException("HTTP transport error: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransportException("Interrupted", e);
        }
    }
}
