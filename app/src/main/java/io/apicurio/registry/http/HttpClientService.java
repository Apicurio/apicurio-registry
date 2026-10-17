package io.apicurio.registry.http;

import io.apicurio.registry.types.ContentTypes;
import io.smallrye.faulttolerance.api.ExponentialBackoff;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Sends the outbound HTTP requests made by the configured (webhook) artifact type providers.
 *
 * <p>The fault tolerance annotations below belong on exactly one public entry point. ArC applies
 * interceptors by subclassing the bean, so a call on {@code this} is still intercepted and the
 * retry budgets would nest. Any overload added here should delegate to a shared private method
 * rather than to the other public one.
 */
@ApplicationScoped
public class HttpClientService {

    private static final Logger log = LoggerFactory.getLogger(HttpClientService.class);

    @Inject
    WebClient webClient;

    /**
     * Sends an HTTP POST request to the given URL, applying the supplied headers, and deserializes
     * the response body.
     *
     * <p>Any HTTP 2xx status code is treated as success. If {@code outputClass} is {@code Void.class},
     * this method always returns {@code null} without deserializing the response. For other return types,
     * an empty response body (such as {@code 204 No Content}) or malformed JSON results in
     * {@link HttpClientContentException} (which is not retried). HTTP 4xx client errors throw
     * {@link HttpClientErrorException} (which is also not retried). Only transient 5xx/transport
     * errors are retried.
     *
     * <p>The headers are typically configured on a webhook provider so that the Registry can
     * authenticate itself to the endpoint it is calling. A {@code null} or empty map is a no-op, as
     * are entries with a {@code null} value or one the HTTP layer rejects.
     *
     * <p>{@code Content-Type} is always {@code application/json} and cannot be overridden by the
     * supplied headers: the body is serialized as JSON by {@code sendJson}, so a header from
     * configuration would otherwise mislabel the payload.
     *
     * @param headers headers to attach to the request; may be {@code null} or empty
     * @throws HttpClientContentException if an empty body or malformed JSON is returned when a
     *                                    non-{@code Void} return type was expected
     * @throws HttpClientErrorException   if the server returns a 4xx client error status
     * @throws HttpClientInterruptedException if the request is interrupted
     * @throws HttpClientException        if the server returns a 5xx status or any other I/O error occurs
     */
    @Retry(maxRetries = 8, delay = 100, jitter = 50, abortOn = {
            HttpClientInterruptedException.class,
            HttpClientContentException.class,
            HttpClientErrorException.class,
            InterruptedException.class
    })
    @ExponentialBackoff
    @Timeout(value = 10, unit = ChronoUnit.SECONDS)
    public <I, O> O post(String url, Map<String, String> headers, I body, Class<O> outputClass) throws HttpClientException {
        try {
            // Configured headers go on first so the Content-Type below wins. Vert.x only defaults
            // Content-Type when absent, so one from configuration would mislabel the JSON body.
            HttpRequest<Buffer> request = webClient.postAbs(url);
            applyHeaders(request, headers);
            request.putHeader("Content-Type", ContentTypes.APPLICATION_JSON).followRedirects(true);
            Future<HttpResponse<Buffer>> future = request.sendJson(body);

            // Wait for the response (vert.x is async).
            HttpResponse<Buffer> httpResponse = future.toCompletionStage().toCompletableFuture().get();
            return processResponse(httpResponse, outputClass);
        } catch (ExecutionException e) {
            throw new HttpClientException(e);
        } catch (InterruptedException e) {
            // Restore the interrupt flag before rethrowing so callers and
            // fault-tolerance retry machinery (@Retry) can observe the interruption.
            // abortOn = HttpClientInterruptedException.class prevents retrying an interrupted call,
            // while allowing 5xx server errors to still be retried.
            Thread.currentThread().interrupt();
            throw new HttpClientInterruptedException(e);
        }
    }

    private void applyHeaders(HttpRequest<Buffer> request, Map<String, String> headers) {
        if (headers == null) {
            return;
        }
        headers.forEach((name, value) -> {
            // Values are nullable: the map comes from operator-supplied configuration.
            if (value == null) {
                return;
            }
            try {
                request.putHeader(name, value);
            } catch (IllegalArgumentException e) {
                // Skip rather than propagate: the exception would be retried pointlessly, and its
                // message quotes the rejected value, which for these headers is a credential.
                log.warn("Skipping invalid webhook header '{}', rejected by the HTTP layer.", name);
            }
        });
    }

    private <O> O processResponse(HttpResponse<Buffer> httpResponse, Class<O> outputClass) throws HttpClientException {
        int statusCode = httpResponse.statusCode();
        if (statusCode >= 200 && statusCode < 300) {
            return handleSuccess(httpResponse, outputClass, statusCode);
        }
        handleFailure(httpResponse, statusCode);
        return null;
    }

    private <O> O handleSuccess(HttpResponse<Buffer> httpResponse, Class<O> outputClass, int statusCode) throws HttpClientException {
        // If Void is expected, return null directly without attempting deserialization.
        if (outputClass == Void.class) {
            return null;
        }
        Buffer bodyBuffer = httpResponse.body();
        if (bodyBuffer == null || bodyBuffer.length() == 0) {
            throw new HttpClientContentException("Webhook returned " + statusCode + " with empty body, expected " + outputClass.getSimpleName());
        }
        return decodeResponse(httpResponse, outputClass, statusCode);
    }

    private void handleFailure(HttpResponse<Buffer> httpResponse, int statusCode) throws HttpClientException {
        String msg = extractStatusMessage(httpResponse, statusCode);
        if (statusCode >= 400 && statusCode < 500) {
            throw new HttpClientErrorException("Webhook request failed (" + statusCode + "): " + msg);
        }
        throw new HttpClientException("Webhook request failed (" + statusCode + "): " + msg);
    }

    private String extractStatusMessage(HttpResponse<Buffer> httpResponse, int statusCode) {
        String statusMessage = httpResponse.statusMessage();
        if (statusMessage != null && !statusMessage.isBlank()) {
            return statusMessage;
        }
        return "HTTP status " + statusCode;
    }

    @SuppressWarnings("unchecked")
    private <O> O decodeResponse(HttpResponse<Buffer> httpResponse, Class<O> outputClass, int statusCode) throws HttpClientException {
        try {
            return (O) httpResponse.bodyAsJson(outputClass);
        } catch (io.vertx.core.json.DecodeException e) {
            throw new HttpClientContentException("Failed to decode JSON response from " + statusCode, e);
        }
    }
}
