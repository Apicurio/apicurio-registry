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
     * Posts a JSON request to the given URL and returns the parsed response.
     *
     * <p>All HTTP 2xx status codes are treated as success. A {@code 204 No Content} response
     * or any 2xx response with a {@code null} body will return {@code null} rather than
     * attempting JSON deserialization. Callers that require a non-null response should
     * handle this case accordingly.
     *
     * @throws HttpClientException on non-2xx responses or communication errors
     * @throws HttpClientInterruptException if the calling thread is interrupted (aborts retry)
     */
    @Retry(maxRetries = 8, delay = 100, jitter = 50, abortOn = HttpClientInterruptException.class)
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
            int status = httpResponse.statusCode();
            if (status >= 200 && status < 300) {
                if (status == 204 || httpResponse.body() == null || httpResponse.body().length() == 0) {
                    return null;
                }
                return (O) httpResponse.bodyAsJson(outputClass);
            } else {
                throw new HttpClientException("Webhook request failed (" + status + "): " + httpResponse.statusMessage());
            }
        } catch (ExecutionException e) {
            throw new HttpClientException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HttpClientInterruptException(e);
        }
    }
}
