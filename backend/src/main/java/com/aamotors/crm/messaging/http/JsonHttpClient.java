package com.aamotors.crm.messaging.http;

import java.util.Map;

/** Thin HTTP abstraction so provider adapters can be unit-tested without network access. */
public interface JsonHttpClient {

    record Response(int status, String body) {
        public boolean is2xx() { return status >= 200 && status < 300; }
    }

    Response postJson(String url, Map<String, String> headers, String jsonBody);

    Response postForm(String url, Map<String, String> headers, Map<String, String> form);

    Response get(String url, Map<String, String> headers);

    /** Thrown for transport-level problems (timeouts, DNS, connection reset): the request outcome is unknown. */
    class TransportException extends RuntimeException {
        public TransportException(String message, Throwable cause) { super(message, cause); }
    }
}
