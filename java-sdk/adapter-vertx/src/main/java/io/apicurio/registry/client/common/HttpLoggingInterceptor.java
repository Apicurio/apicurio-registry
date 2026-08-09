package io.apicurio.registry.client.common;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.impl.HttpContext;
import io.vertx.ext.web.client.impl.WebClientInternal;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link WebClient} interceptor that logs the raw HTTP request and response of every call.
 *
 * <p>The request is logged just before it is sent, so the headers include everything added by
 * the client itself, and the response is logged when it is dispatched back to the caller.
 * Requests are prefixed with {@code >} and responses with {@code <}.</p>
 *
 * <p>Headers listed in {@link #REDACTED_HEADERS} never have their value logged, and bodies longer
 * than {@link #MAX_BODY_CHARS} are truncated so that large artifact content does not flood the log.</p>
 */
final class HttpLoggingInterceptor implements Handler<HttpContext<?>> {

    /**
     * Dedicated logger name, so that HTTP traffic can be enabled or quieted independently of the
     * rest of the SDK.
     */
    static final String LOGGER_NAME = "io.apicurio.registry.client.http";

    private static final Logger log = Logger.getLogger(LOGGER_NAME);

    /** Headers that carry credentials, and whose values are therefore never logged. */
    private static final Set<String> REDACTED_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie");

    private static final String REDACTED_VALUE = "<redacted>";

    private static final int MAX_BODY_CHARS = 8192;

    /**
     * Installs the interceptor on the given client. Clients that are not built on top of the Vert.x
     * web client implementation do not support interceptors, in which case logging is skipped.
     *
     * @param webClient the client to log the traffic of
     */
    static void install(WebClient webClient) {
        if (webClient instanceof WebClientInternal internal) {
            internal.addInterceptor(new HttpLoggingInterceptor());
        } else {
            log.log(Level.WARNING, "HTTP logging was enabled, but the web client implementation {0} "
                    + "does not support interceptors. No HTTP traffic will be logged.",
                    webClient.getClass().getName());
        }
    }

    @Override
    public void handle(HttpContext<?> context) {
        if (log.isLoggable(Level.FINE)) {
            switch (context.phase()) {
                case SEND_REQUEST -> log.fine(formatRequest(context));
                case DISPATCH_RESPONSE -> log.fine(formatResponse(context.response()));
                default -> {
                    // Other phases carry no request or response details worth logging.
                }
            }
        }
        context.next();
    }

    private static String formatRequest(HttpContext<?> context) {
        var request = context.clientRequest();
        var out = new StringBuilder("HTTP request:\n");
        out.append("> ").append(request.getMethod()).append(' ').append(request.absoluteURI()).append('\n');
        appendHeaders(out, '>', request.headers());
        appendBody(out, '>', bodyAsText(context.body()));
        return out.toString();
    }

    private static String formatResponse(HttpResponse<?> response) {
        var out = new StringBuilder("HTTP response:\n");
        out.append("< ").append(response.statusCode());
        if (response.statusMessage() != null) {
            out.append(' ').append(response.statusMessage());
        }
        out.append('\n');
        appendHeaders(out, '<', response.headers());
        appendBody(out, '<', bodyAsText(response.bodyAsBuffer()));
        return out.toString();
    }

    private static void appendHeaders(StringBuilder out, char prefix, MultiMap headers) {
        if (headers == null) {
            return;
        }
        for (Map.Entry<String, String> header : headers) {
            var value = REDACTED_HEADERS.contains(header.getKey().toLowerCase(Locale.ROOT))
                    ? REDACTED_VALUE : header.getValue();
            out.append(prefix).append(' ').append(header.getKey()).append(": ").append(value).append('\n');
        }
    }

    private static void appendBody(StringBuilder out, char prefix, String body) {
        if (body == null || body.isEmpty()) {
            return;
        }
        out.append(prefix).append('\n');
        body.lines().forEach(line -> out.append(prefix).append(' ').append(line).append('\n'));
    }

    /**
     * Renders a request or response body as text. Bodies that are not buffered, for example a
     * streamed upload, are represented by their type only, because consuming them here would
     * take them away from the client.
     */
    private static String bodyAsText(Object body) {
        if (body == null) {
            return null;
        }
        if (!(body instanceof Buffer buffer)) {
            return "<" + body.getClass().getSimpleName() + ">";
        }
        var text = buffer.toString(StandardCharsets.UTF_8);
        if (text.length() > MAX_BODY_CHARS) {
            return text.substring(0, MAX_BODY_CHARS)
                    + "... (truncated, " + text.length() + " characters total)";
        }
        return text;
    }
}
