package io.apicurio.registry.federation;

import io.apicurio.registry.rest.v3.beans.AgentSearchResults;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Calls a peer registry's agent discovery endpoint.
 *
 * <p>This is deliberately a separate CDI bean. {@link Timeout} and {@link CircuitBreaker} are
 * interceptors, and an interceptor only fires when the call crosses a bean boundary. Inlining this
 * method into the fan-out service would mean calling it as {@code this.search(...)}, which bypasses
 * the proxy and silently disables both annotations.
 */
@ApplicationScoped
public class PeerClient {

    private static final Logger log = LoggerFactory.getLogger(PeerClient.class);

    /**
     * Marks a request as already being part of a federated search. A registry receiving this header
     * serves only its own agents and does not fan out again, which makes federation non-transitive
     * and stops two mutually-peered registries from recursing indefinitely.
     */
    public static final String FEDERATION_HEADER = "X-Apicurio-Federated";

    @Inject
    WebClient webClient;

    /**
     * Queries a peer for agents matching the given criteria.
     *
     * <p>Asks the peer for its first {@code offset + limit} results rather than for its own slice
     * at {@code offset}. Each source numbers only its own results, so a peer's position N is not
     * position N of the merged set. The caller applies the real offset after merging.
     */
    @Timeout(value = 5, unit = ChronoUnit.SECONDS)
    public AgentSearchResults search(String peerUrl, String name, List<String> skills,
            List<String> capabilities, int offset, int limit) throws PeerClientException {

        String url = buildUrl(peerUrl, name, skills, capabilities, offset + limit);
        log.debug("Federating agent search to peer: {}", url);

        try {
            // Redirects are not followed on purpose. The peer URL is validated when it is
            // registered; honouring a redirect would let the peer send this request somewhere that
            // was never validated, including link-local and internal addresses.
            HttpRequest<Buffer> request = webClient.getAbs(url)
                    .putHeader(FEDERATION_HEADER, "true")
                    .putHeader("Accept", "application/json")
                    .followRedirects(false);

            HttpResponse<Buffer> response = request.send()
                    .toCompletionStage().toCompletableFuture().get();

            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new PeerClientException("Peer " + peerUrl + " returned HTTP " + status);
            }

            Buffer body = response.body();
            if (body == null || body.length() == 0) {
                throw new PeerClientException("Peer " + peerUrl + " returned an empty body");
            }

            return body.toJsonObject().mapTo(AgentSearchResults.class);

        } catch (ExecutionException e) {
            throw new PeerClientException("Failed to query peer " + peerUrl, e);
        } catch (InterruptedException e) {
            // Restore the interrupt flag so the caller can still observe the interruption.
            Thread.currentThread().interrupt();
            throw new PeerClientException("Interrupted while querying peer " + peerUrl, e);
        }
    }

    private String buildUrl(String peerUrl, String name, List<String> skills,
            List<String> capabilities, int fetchSize) {
        StringBuilder url = new StringBuilder(trimTrailingSlash(peerUrl))
                .append("/.well-known/agents?offset=0&limit=")
                .append(fetchSize);

        if (name != null && !name.isBlank()) {
            url.append("&name=").append(encode(name));
        }
        appendRepeated(url, "skill", skills);
        appendRepeated(url, "capability", capabilities);
        return url.toString();
    }

    private void appendRepeated(StringBuilder url, String param, List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            url.append("&").append(param).append("=").append(encode(value));
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
