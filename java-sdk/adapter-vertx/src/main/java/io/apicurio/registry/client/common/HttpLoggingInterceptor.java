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
 * <p>Headers listed in {@link #REDACTED_HEADERS} and query parameters listed in
 * {@link #REDACTED_QUERY_PARAMS} never have their value logged, and bodies longer than
 * {@link #MAX_BODY_CHARS} are truncated so that large artifact content does not flood the log.
 * The rest of the request URL is logged as it is sent, so a deployment that puts a credential in
 * a query parameter under some other name still has that credential end up in the log.</p>
 *
 * <p>A single call can produce more than one request record. Each redirect hop is logged
 * separately and tagged with its hop number, and a client that retries a request, for example
 * after refreshing an expired token, logs the retry as another request.</p>
 *
 * <p><strong>Vert.x compatibility:</strong> {@link WebClientInternal} and {@link HttpContext} live
 * in a Vert.x implementation package rather than in its public API, because interceptors are not
 * exposed anywhere else. This class is validated against Vert.x 4.5, so a Vert.x upgrade has to
 * re-check that these types, the {@code ClientPhase} constants used in {@link #handle}, and
 * {@link HttpContext#getRedirectedLocations()} are all still present with the same meaning.</p>
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

    /**
     * Query parameters that carry credentials, and whose values are therefore never logged. These
     * are the names used by OAuth 2.0 and by the deployments that accept a token in the URL; a
     * credential passed under any other name is logged as it is sent.
     */
    private static final Set<String> REDACTED_QUERY_PARAMS = Set.of(
            "access_token", "id_token", "refresh_token", "token", "code", "client_secret",
            "assertion", "api_key", "apikey");

    private static final String REDACTED_VALUE = "<redacted>";

    private static final int MAX_BODY_CHARS = 8192;

    /**
     * Installs the interceptor on the given client. Clients that are not built on top of the Vert.x
     * web client implementation do not support interceptors, in which case logging is skipped.
     *
     * <p>Vert.x has no way to remove an interceptor again, so a client keeps logging its traffic
     * for the rest of its life once this has been called on it.</p>
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
                case DISPATCH_RESPONSE -> log.fine(formatResponse(context));
                default -> {
                    // Other phases carry no request or response details worth logging.
                }
            }
        }
        context.next();
    }

    private static String formatRequest(HttpContext<?> context) {
        var request = context.clientRequest();
        // Populated by the phase that receives a redirect response, so by the time the follow-up
        // request is sent it already holds one entry per hop taken so far.
        var hop = context.getRedirectedLocations().size();
        var out = new StringBuilder("HTTP request");
        if (hop > 0) {
            out.append(" (redirect ").append(hop).append(')');
        }
        out.append(":\n");
        out.append("> ").append(request.getMethod()).append(' ')
                .append(redactUri(request.absoluteURI())).append('\n');
        appendHeaders(out, '>', request.headers());
        appendBody(out, '>', bodyAsText(context.body()));
        return out.toString();
    }

    private static String formatResponse(HttpContext<?> context) {
        HttpResponse<?> response = context.response();
        var hops = context.getRedirectedLocations().size();
        var out = new StringBuilder("HTTP response");
        if (hops > 0) {
            out.append(" (after ").append(hops).append(hops == 1 ? " redirect)" : " redirects)");
        }
        out.append(":\n");
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
     * Replaces the value of every credential-carrying query parameter with {@link #REDACTED_VALUE}.
     * Parameter names are compared as they appear in the URL, so a percent-encoded name is not
     * recognised and its value is logged.
     */
    private static String redactUri(String uri) {
        if (uri == null) {
            return null;
        }
        var queryStart = uri.indexOf('?');
        if (queryStart < 0) {
            return uri;
        }
        var query = uri.substring(queryStart + 1);
        var fragment = "";
        var fragmentStart = query.indexOf('#');
        if (fragmentStart >= 0) {
            fragment = query.substring(fragmentStart);
            query = query.substring(0, fragmentStart);
        }
        var out = new StringBuilder(uri.substring(0, queryStart + 1));
        var parameters = query.split("&", -1);
        for (var i = 0; i < parameters.length; i++) {
            if (i > 0) {
                out.append('&');
            }
            var parameter = parameters[i];
            var separator = parameter.indexOf('=');
            var name = separator < 0 ? parameter : parameter.substring(0, separator);
            if (separator >= 0 && REDACTED_QUERY_PARAMS.contains(name.toLowerCase(Locale.ROOT))) {
                out.append(name).append('=').append(REDACTED_VALUE);
            } else {
                out.append(parameter);
            }
        }
        return out.append(fragment).toString();
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
