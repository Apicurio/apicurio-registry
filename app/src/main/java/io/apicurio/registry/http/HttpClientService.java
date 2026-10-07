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

import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutionException;

@ApplicationScoped
public class HttpClientService {

    @Inject
    WebClient webClient;

    /**
     * Sends an HTTP POST request to the given URL and deserializes the response body.
     *
     * <p>Any HTTP 2xx status code is treated as success. If {@code outputClass} is {@code Void.class},
     * this method always returns {@code null} without deserializing the response. For other return types,
     * an empty response body (such as {@code 204 No Content}) or malformed JSON results in
     * {@link HttpClientContentException} (which is not retried). HTTP 4xx client errors throw
     * {@link HttpClientErrorException} (which is also not retried). Only transient 5xx/transport
     * errors are retried.
     *
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
    public <I, O> O post(String url, I body, Class<O> outputClass) throws HttpClientException {
        try {
            // POST the request to the webhook endpoint
            HttpRequest<Buffer> request = webClient.postAbs(url).putHeader("Content-Type", ContentTypes.APPLICATION_JSON)
                    .followRedirects(true);
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
