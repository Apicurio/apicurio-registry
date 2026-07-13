package io.apicurio.registry.client.common;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.impl.HttpContext;

import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * A Vert.x {@link io.vertx.ext.web.client.WebClient} interceptor that logs raw HTTP request and
 * response details (method, URL, headers, and body) for outgoing Registry calls.
 *
 * <p>Details are logged to the {@code io.apicurio.registry.client.http} logger at {@code FINE}
 * level, so nothing is emitted unless that logger is configured to log {@code FINE} messages.
 * Sensitive headers such as {@code Authorization} are redacted.</p>
 */
class HttpLoggingInterceptor implements Handler<HttpContext<?>> {

    static final String LOGGER_NAME = "io.apicurio.registry.client.http";

    private static final Logger log = Logger.getLogger(LOGGER_NAME);

    private static final Set<String> REDACTED_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie");

    private static final String REDACTED = "<redacted>";

    @Override
    public void handle(HttpContext<?> context) {
        switch (context.phase()) {
            case SEND_REQUEST -> logRequest(context);
            case DISPATCH_RESPONSE -> logResponse(context);
            default -> {
                // Only the request-send and response-dispatch phases are logged.
            }
        }
        context.next();
    }

    private static void logRequest(HttpContext<?> context) {
        HttpClientRequest request = context.clientRequest();
        if (request == null) {
            return;
        }
        // Supplier form defers the (potentially expensive) message building until it is known
        // that the logger will actually emit at FINE.
        log.fine(() -> {
            var sb = new StringBuilder("--> ")
                    .append(request.getMethod()).append(' ').append(request.absoluteURI()).append('\n');
            appendHeaders(sb, request.headers());
            appendBody(sb, context.body());
            return sb.toString();
        });
    }

    private static void logResponse(HttpContext<?> context) {
        HttpResponse<?> response = context.response();
        if (response == null) {
            return;
        }
        log.fine(() -> {
            var sb = new StringBuilder("<-- ")
                    .append(response.statusCode()).append(' ').append(response.statusMessage()).append('\n');
            appendHeaders(sb, response.headers());
            appendBody(sb, safeBody(response));
            return sb.toString();
        });
    }

    private static void appendHeaders(StringBuilder sb, MultiMap headers) {
        if (headers == null) {
            return;
        }
        for (Map.Entry<String, String> header : headers) {
            var value = REDACTED_HEADERS.contains(header.getKey().toLowerCase(java.util.Locale.ROOT))
                    ? REDACTED : header.getValue();
            sb.append(header.getKey()).append(": ").append(value).append('\n');
        }
    }

    private static void appendBody(StringBuilder sb, Object body) {
        var text = bodyToString(body);
        if (text != null && !text.isEmpty()) {
            sb.append('\n').append(text).append('\n');
        }
    }

    private static String bodyToString(Object body) {
        if (body == null) {
            return null;
        }
        if (body instanceof Buffer buffer) {
            return buffer.toString();
        }
        if (body instanceof byte[] bytes) {
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
        return body.toString();
    }

    private static Buffer safeBody(HttpResponse<?> response) {
        try {
            return response.bodyAsBuffer();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
