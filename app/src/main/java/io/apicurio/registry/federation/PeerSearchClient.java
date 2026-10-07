package io.apicurio.registry.federation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.federation.PeerAddressPolicy.AddressVerdict;
import io.apicurio.registry.rest.v3.beans.AgentSearchResults;
import io.apicurio.registry.storage.dto.PeerDto;
import io.apicurio.registry.storage.error.InvalidPeerException;
import io.netty.channel.ConnectTimeoutException;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ConnectionPoolTooBusyException;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.SocketAddress;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Calls the agent search of a peer registry, asking for public agents only.
 * <p>
 * The call is anonymous: nothing identifies the caller or this registry to the peer, and a
 * credential reference on the peer is not used. Redirects are not followed. The peer must confirm
 * that it applied the public-only mode in its answer; an answer that does not is discarded, since a
 * registry that predates the mode ignores the request for it and may return agents that are not
 * public.
 * <p>
 * The peer's host name is resolved here, every address it resolves to is checked against
 * {@link PeerAddressPolicy}, and the connection is then made to the address that was checked, with
 * the host name still used for the {@code Host} header and for TLS server name and certificate
 * verification. Resolving once to check and again inside the HTTP client would leave a window in
 * which a DNS change moves the connection to an address that was never checked.
 * <p>
 * Each call is bounded by the per-peer timeout, the size of the answer is bounded, and the
 * connections and the queue behind them are bounded per peer.
 */
@ApplicationScoped
public class PeerSearchClient {

    private static final Logger log = LoggerFactory.getLogger(PeerSearchClient.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    static final String AGENTS_PATH = "/.well-known/agents";

    private static final int CONNECTIONS_PER_PEER = 4;
    private static final int QUEUED_REQUESTS_PER_PEER = 16;
    private static final int KEEP_ALIVE_SECONDS = 30;

    @Inject
    Vertx vertx;

    @Inject
    FederationConfig config;

    /** Name resolution, replaceable so that a test can decide what a host name resolves to. */
    @FunctionalInterface
    interface Resolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    // Test hooks. Production leaves the defaults: the system resolver, and a client that verifies
    // the certificate chain and the host name against the JVM trust store.
    Resolver resolver = InetAddress::getAllByName;
    Consumer<HttpClientOptions> optionsCustomizer = options -> {
    };

    private HttpClient httpClient;
    private ThreadPoolExecutor setupExecutor;

    @PostConstruct
    void init() {
        HttpClientOptions options = new HttpClientOptions()
                .setMaxPoolSize(CONNECTIONS_PER_PEER)
                .setMaxWaitQueueSize(QUEUED_REQUESTS_PER_PEER)
                .setKeepAliveTimeout(KEEP_ALIVE_SECONDS)
                .setConnectTimeout((int) Math.min(config.getSearchPeerTimeoutMs(), Integer.MAX_VALUE))
                .setVerifyHost(true);
        optionsCustomizer.accept(options);
        httpClient = vertx.createHttpClient(options);

        // Name resolution blocks, so it runs here and not on an event loop. The pool has no queue:
        // when every thread is busy, for example behind lookups that hang, the call is refused.
        int threads = Math.max(1, config.getSearchMaxConcurrentCalls());
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "federation-peer-call-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        setupExecutor = new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS, new SynchronousQueue<>(),
                threadFactory);
        setupExecutor.allowCoreThreadTimeOut(true);
    }

    @PreDestroy
    void close() {
        if (setupExecutor != null) {
            setupExecutor.shutdownNow();
        }
        if (httpClient != null) {
            httpClient.close();
        }
    }

    /**
     * Starts a public-only agent search against the peer. Never throws: every failure is carried
     * by the returned call's future as a {@link PeerSearchException}.
     */
    PeerCall search(PeerDto peer, PeerQuery query) {
        CompletableFuture<PeerSearchResponse> future = new CompletableFuture<>();
        AtomicReference<InFlight> inFlight = new AtomicReference<>();

        long timeoutMs = config.getSearchPeerTimeoutMs();
        long timer = vertx.setTimer(timeoutMs, id -> future.completeExceptionally(
                new PeerSearchException.Timeout("The peer did not answer within " + timeoutMs + " ms.")));
        future.whenComplete((response, failure) -> {
            vertx.cancelTimer(timer);
            if (failure != null) {
                reset(inFlight.get());
            }
        });

        try {
            setupExecutor.execute(() -> call(peer, query, future, inFlight));
        } catch (RejectedExecutionException ex) {
            future.completeExceptionally(
                    new PeerSearchException.CapacityExceeded("Too many peer calls are in progress."));
        }
        return new PeerCall(future);
    }

    private void call(PeerDto peer, PeerQuery query, CompletableFuture<PeerSearchResponse> future,
            AtomicReference<InFlight> inFlight) {
        try {
            if (future.isDone()) {
                return;
            }
            URI uri = checkedUri(peer);
            boolean ssl = "https".equals(uri.getScheme().toLowerCase(Locale.ROOT));
            int port = uri.getPort() != -1 ? uri.getPort() : (ssl ? 443 : 80);
            String host = uri.getHost();
            InetAddress address = resolve(host);
            if (future.isDone()) {
                return;
            }

            RequestOptions options = new RequestOptions()
                    .setMethod(HttpMethod.GET)
                    .setHost(unbracketed(host))
                    .setPort(port)
                    .setServer(SocketAddress.inetSocketAddress(port, address.getHostAddress()))
                    .setSsl(ssl)
                    .setFollowRedirects(false)
                    .setURI(requestUri(uri, query))
                    .putHeader("Accept", "application/json");

            httpClient.request(options).compose(request -> {
                inFlight.set(new InFlight(request, Vertx.currentContext()));
                if (future.isDone()) {
                    reset(inFlight.get());
                    return Future.<HttpClientResponse>failedFuture(new PeerSearchException.Cancelled());
                }
                return request.send();
            }).onSuccess(response -> read(response, query, future))
                    .onFailure(failure -> future.completeExceptionally(translate(failure)));
        } catch (PeerSearchException ex) {
            future.completeExceptionally(ex);
        } catch (RuntimeException ex) {
            log.warn("Unexpected failure calling peer {}", peer.getPeerId(), ex);
            future.completeExceptionally(new PeerSearchException.Unreachable("The call failed.", ex));
        }
    }

    private URI checkedUri(PeerDto peer) {
        try {
            PeerAddressPolicy.validateUrl(peer.getUrl(), config.isInsecureHttpEnabled(),
                    config.isLoopbackEnabled());
        } catch (InvalidPeerException ex) {
            throw new PeerSearchException.AddressRefused(ex.getMessage());
        }
        return URI.create(peer.getUrl());
    }

    private InetAddress resolve(String host) {
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(unbracketed(host));
        } catch (UnknownHostException ex) {
            throw new PeerSearchException.Unreachable("The peer's host name could not be resolved.", ex);
        }
        // Every address is checked, not only the one connected to, so that a name that resolves to
        // an address this registry must not reach is refused whatever order it comes back in.
        for (InetAddress address : addresses) {
            AddressVerdict verdict = PeerAddressPolicy.classify(address, config.isLoopbackEnabled());
            if (verdict != AddressVerdict.ALLOWED) {
                throw new PeerSearchException.AddressRefused(
                        "The peer's host resolves to " + verdict.getDescription() + ".");
            }
        }
        return addresses[0];
    }

    private void read(HttpClientResponse response, PeerQuery query,
            CompletableFuture<PeerSearchResponse> future) {
        PeerSearchException early = checkStatusAndHeaders(response);
        if (early != null) {
            future.completeExceptionally(early);
            return;
        }
        int maxBytes = config.getSearchMaxResponseBytes();
        Buffer body = Buffer.buffer();
        response.exceptionHandler(failure -> future.completeExceptionally(translate(failure)));
        response.handler(chunk -> {
            if ((long) body.length() + chunk.length() > maxBytes) {
                future.completeExceptionally(new PeerSearchException.InvalidResponse(
                        "The peer's answer is larger than " + maxBytes + " bytes.", null));
                return;
            }
            body.appendBuffer(chunk);
        });
        response.endHandler(ignored -> {
            try {
                future.complete(decode(body, query));
            } catch (PeerSearchException ex) {
                future.completeExceptionally(ex);
            }
        });
    }

    private PeerSearchException checkStatusAndHeaders(HttpClientResponse response) {
        int status = response.statusCode();
        if (status == 401 || status == 403) {
            return new PeerSearchException.Unauthorized("The peer answered " + status + ".");
        }
        if (status == 404) {
            return new PeerSearchException.Unsupported("The peer has no agent search.");
        }
        if (status >= 300 && status < 400) {
            return new PeerSearchException.PeerError("The peer answered with a redirect, which is not followed.");
        }
        if (status >= 500) {
            return new PeerSearchException.PeerError("The peer answered " + status + ".");
        }
        if (status != 200) {
            return new PeerSearchException.InvalidResponse("The peer answered " + status + ".", null);
        }
        String contentType = response.getHeader("Content-Type");
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("application/json")) {
            return new PeerSearchException.InvalidResponse("The peer's answer is not JSON.", null);
        }
        String contentLength = response.getHeader("Content-Length");
        if (contentLength != null) {
            try {
                if (Long.parseLong(contentLength.trim()) > config.getSearchMaxResponseBytes()) {
                    return new PeerSearchException.InvalidResponse("The peer's answer is too large.", null);
                }
            } catch (NumberFormatException ex) {
                return new PeerSearchException.InvalidResponse("The peer's answer has an invalid length.", ex);
            }
        }
        return null;
    }

    private PeerSearchResponse decode(Buffer body, PeerQuery query) {
        AgentSearchResults results;
        try {
            JsonNode root = MAPPER.readTree(body.getBytes());
            if (root == null || !root.isObject()) {
                throw new PeerSearchException.InvalidResponse("The peer's answer is not a JSON object.", null);
            }
            // The bean defaults a missing list to an empty one, which would pass for "no matches".
            if (!root.path("agents").isArray() || !root.path("count").isNumber()) {
                throw new PeerSearchException.InvalidResponse("The peer's answer is incomplete.", null);
            }
            results = MAPPER.treeToValue(root, AgentSearchResults.class);
        } catch (IOException ex) {
            throw new PeerSearchException.InvalidResponse("The peer's answer is not a valid agent search result.",
                    ex);
        }
        // Checked before anything else is trusted: an answer without the confirmation may have
        // been filtered by nobody.
        if (!Boolean.TRUE.equals(results.getPublicOnly())) {
            throw new PeerSearchException.Unsupported("The peer did not confirm the public-only mode.");
        }
        if (results.getAgents().size() > query.limit()) {
            throw new PeerSearchException.InvalidResponse("The peer returned more agents than were asked for.",
                    null);
        }
        if (results.getCount() < results.getAgents().size()) {
            throw new PeerSearchException.InvalidResponse("The peer's count is below the agents it returned.",
                    null);
        }
        return new PeerSearchResponse(List.copyOf(results.getAgents()), results.getCount());
    }

    private static String requestUri(URI uri, PeerQuery query) {
        String basePath = uri.getRawPath() == null ? "" : uri.getRawPath();
        if (basePath.endsWith("/")) {
            basePath = basePath.substring(0, basePath.length() - 1);
        }
        StringBuilder queryString = new StringBuilder();
        append(queryString, "name", query.name());
        query.skills().forEach(value -> append(queryString, "skill", value));
        query.capabilities().forEach(value -> append(queryString, "capability", value));
        query.inputModes().forEach(value -> append(queryString, "inputMode", value));
        query.outputModes().forEach(value -> append(queryString, "outputMode", value));
        append(queryString, "offset", "0");
        append(queryString, "limit", Integer.toString(query.limit()));
        append(queryString, "publicOnly", "true");
        return basePath + AGENTS_PATH + "?" + queryString;
    }

    private static void append(StringBuilder queryString, String name, String value) {
        if (value == null) {
            return;
        }
        if (queryString.length() > 0) {
            queryString.append('&');
        }
        queryString.append(name).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    private static String unbracketed(String host) {
        return host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
    }

    private static PeerSearchException translate(Throwable failure) {
        if (failure instanceof PeerSearchException) {
            return (PeerSearchException) failure;
        }
        if (failure instanceof ConnectionPoolTooBusyException) {
            return new PeerSearchException.CapacityExceeded("Too many calls to this peer are queued.");
        }
        if (failure instanceof ConnectTimeoutException) {
            return new PeerSearchException.Timeout("The connection to the peer timed out.");
        }
        log.debug("Call to a peer failed: {}", failure.toString());
        return new PeerSearchException.Unreachable("The peer could not be reached.", failure);
    }

    private static void reset(InFlight inFlight) {
        if (inFlight != null) {
            inFlight.context().runOnContext(ignored -> inFlight.request().reset());
        }
    }

    private record InFlight(HttpClientRequest request, Context context) {
    }
}
