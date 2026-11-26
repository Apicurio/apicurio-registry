package io.apicurio.registry.agents.a2a.signature;

import io.apicurio.registry.http.OutboundAddressPolicy;
import io.apicurio.registry.http.OutboundAddressPolicy.AddressVerdict;
import io.apicurio.registry.http.OutboundConfig;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Fetches JSON Web Key Sets under the server-wide outbound rules: HTTPS unless
 * {@code apicurio.outbound.insecure-http.enabled}, no user information, no redirects, and no
 * connection to link-local, unspecified, multicast, reserved or cloud metadata addresses, nor to loopback
 * unless {@code apicurio.outbound.loopback.enabled} (see {@link OutboundAddressPolicy}). The whole
 * fetch, from connecting to the last byte of the body, must finish within
 * {@code apicurio.a2a.signature-verification.fetch.timeout.seconds}, and the body must not exceed
 * {@code apicurio.a2a.signature-verification.fetch.max-size.bytes}; otherwise the fetch fails and the key
 * set contributes no keys.
 * <p>
 * Known limitations: the host is resolved with the system resolver before the deadline is enforced, and
 * that lookup has no timeout of its own; and the HTTP client resolves the host again when it connects, so a
 * DNS answer that changes between the check and the connection is not caught (as for the MCP import).
 * </p>
 */
@ApplicationScoped
public class JwksFetcher {

    @Inject
    AgentCardSignatureConfig config;

    @Inject
    OutboundConfig outboundConfig;

    private HttpClient httpClient;

    @PostConstruct
    void init() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getFetchTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * A key set could not be fetched. The message names the reason, never the response content.
     */
    public static class JwksFetchException extends Exception {

        private static final long serialVersionUID = 6390265612417735302L;

        public JwksFetchException(String message) {
            super(message);
        }
    }

    /**
     * @return the response body
     */
    public String fetch(String url) throws JwksFetchException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(config.getFetchTimeoutSeconds());
        URI uri = checkTarget(url);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(config.getFetchTimeoutSeconds()))
                .header("Accept", "application/jwk-set+json, application/json")
                .GET()
                .build();
        int max = config.getFetchMaxSizeBytes();
        CompletableFuture<HttpResponse<byte[]>> pending = httpClient.sendAsync(request,
                info -> new LimitedBodySubscriber(max));
        HttpResponse<byte[]> response;
        try {
            response = pending.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            pending.cancel(true);
            throw new JwksFetchException("not fetched within " + config.getFetchTimeoutSeconds() + " seconds");
        } catch (InterruptedException e) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new JwksFetchException("interrupted");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ResponseTooLargeException) {
                throw new JwksFetchException("response larger than " + max + " bytes");
            }
            throw new JwksFetchException("request failed");
        }
        if (response.statusCode() != 200) {
            throw new JwksFetchException("HTTP status " + response.statusCode());
        }
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    /** The body was larger than allowed; reading it was cancelled. */
    private static final class ResponseTooLargeException extends IOException {

        private static final long serialVersionUID = -4381127389712532447L;
    }

    /**
     * Collects the response body, cancelling the exchange as soon as it exceeds the size limit.
     */
    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {

        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private final int max;
        private Flow.Subscription subscription;

        private LimitedBodySubscriber(int max) {
            this.max = max;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if ((long) body.size() + buffer.remaining() > max) {
                    subscription.cancel();
                    result.completeExceptionally(new ResponseTooLargeException());
                    return;
                }
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                body.writeBytes(bytes);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            result.complete(body.toByteArray());
        }
    }

    private URI checkTarget(String url) throws JwksFetchException {
        URI uri;
        try {
            uri = new URI(url);
        } catch (Exception e) {
            throw new JwksFetchException("not a valid URI");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) && !("http".equals(scheme) && outboundConfig.isInsecureHttpEnabled())) {
            throw new JwksFetchException("must use https");
        }
        if (uri.getRawUserInfo() != null) {
            throw new JwksFetchException("must not contain user information");
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new JwksFetchException("must be an absolute URI with a host");
        }
        boolean loopback = outboundConfig.isLoopbackEnabled();
        try {
            AddressVerdict verdict = OutboundAddressPolicy.classifyHost(host, loopback);
            if (verdict != AddressVerdict.ALLOWED) {
                throw new JwksFetchException("host is " + verdict.getDescription());
            }
        } catch (OutboundAddressPolicy.InvalidHostException e) {
            throw new JwksFetchException("ambiguous host");
        }
        String name = host.startsWith("[") ? host.substring(1, host.length() - 1) : host;
        try {
            for (InetAddress address : InetAddress.getAllByName(name)) {
                AddressVerdict verdict = OutboundAddressPolicy.classify(address, loopback);
                if (verdict != AddressVerdict.ALLOWED) {
                    throw new JwksFetchException("host resolves to " + verdict.getDescription());
                }
            }
        } catch (UnknownHostException | SecurityException e) {
            throw new JwksFetchException("host does not resolve");
        }
        return uri;
    }
}
