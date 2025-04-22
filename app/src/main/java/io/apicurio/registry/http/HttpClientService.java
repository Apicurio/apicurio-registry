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

    @Retry(maxRetries = 8, delay = 100, jitter = 50)
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
            if (httpResponse.statusCode() == 200) {
                return (O) httpResponse.bodyAsJson(outputClass);
            } else {
                throw new HttpClientException("Webhook request failed (" + httpResponse.statusCode() + "): " + httpResponse.statusMessage());
            }
        } catch (ExecutionException e) {
            throw new HttpClientException(e);
        } catch (InterruptedException e) {
            throw new HttpClientException(e);
        }
    }
}
