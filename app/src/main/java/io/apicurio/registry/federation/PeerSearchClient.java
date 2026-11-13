package io.apicurio.registry.federation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.http.OutboundAddressPolicy.AddressVerdict;
import io.apicurio.registry.rest.v3.beans.AgentSearchResults;
import io.apicurio.registry.storage.dto.PeerDto;
import io.apicurio.registry.storage.error.InvalidPeerException;
import io.netty.channel.ConnectTimeoutException;
import io.vertx.core.Context;
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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

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
 * {@link PeerAddressPolicy}, and the connection is then made to an address that was checked, with
 * the host name still used for the {@code Host} header and for TLS server name and certificate
 * verification. The addresses are tried in the order they were resolved, moving on to the next one
 * when a connection cannot be made, so that a peer with both IPv4 and IPv6 addresses is still
 * reached from a host that can use only one of them. Resolving once to check and again inside the HTTP client would leave a window in
 * which a DNS change moves the connection to an address that was never checked.
 * <p>
 * Each call is bounded by the per-peer timeout, the size of the answer is bounded, and the
 * connections and the queue behind them are bounded per peer. Across all peers, the number of calls
 * in progress is bounded from the name lookup until the answer has been read or the call has been
 * cancelled; a call beyond it is refused as {@code capacity_exceeded}.
 * <p>
 * The system resolver has no timeout of its own, and a call keeps its place under that bound until
 * its name lookup has ended, even after the call has timed out. So that a peer whose name lookups
 * hang cannot take every place, one peer can have only a few name lookups in progress at once; a
 * call to it beyond them is refused as {@code capacity_exceeded}, without a lookup.
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
    private static final int LOOKUPS_PER_PEER = 4;

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

    private final KeyedPermits lookups = new KeyedPermits();

    private HttpClient httpClient;
    private Semaphore slots;
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

        int maxCalls = Math.max(1, config.getSearchMaxConcurrentCalls());
        slots = new Semaphore(maxCalls);

        // Name resolution blocks, so it runs here and not on an event loop. There is a thread for
        // every slot, so a call never waits for one; the queue only covers the moment between a
        // thread giving its slot back and returning to the pool.
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "federation-peer-call-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        setupExecutor = new ThreadPoolExecutor(maxCalls, maxCalls, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
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
        if (!lookups.tryAcquire(peer.getPeerId(), LOOKUPS_PER_PEER)) {
            return refused("Too many name lookups for this peer are in progress.");
        }
        Lookup lookup = new Lookup(lookups, peer.getPeerId());
        if (!slots.tryAcquire()) {
            lookup.end();
            return refused("Too many peer calls are in progress.");
        }
        Slot slot = new Slot(slots);
        CompletableFuture<PeerSearchResponse> future = new CompletableFuture<>();
        CompletableFuture<PeerSearchResponse> result = new CompletableFuture<>();
        AtomicReference<InFlight> inFlight = new AtomicReference<>();

        long timeoutMs = config.getSearchPeerTimeoutMs();
        long timer = vertx.setTimer(timeoutMs, id -> future.completeExceptionally(
                new PeerSearchException.Timeout("The peer did not answer within " + timeoutMs + " ms.")));
        future.whenComplete((response, failure) -> {
            vertx.cancelTimer(timer);
            if (failure != null) {
                reset(inFlight.get());
            }
            slot.release();
            if (failure != null) {
                result.completeExceptionally(failure);
            } else {
                result.complete(response);
            }
        });

        try {
            setupExecutor.execute(() -> call(peer, query, future, inFlight, slot, lookup));
        } catch (RejectedExecutionException ex) {
            lookup.end();
            slot.release();
            future.completeExceptionally(
                    new PeerSearchException.CapacityExceeded("No thread is left to set up the call."));
        }
        return new PeerCall(future, result);
    }

    private static PeerCall refused(String why) {
        CompletableFuture<PeerSearchResponse> refused = CompletableFuture
                .failedFuture(new PeerSearchException.CapacityExceeded(why));
        return new PeerCall(refused, refused);
    }

    private void call(PeerDto peer, PeerQuery query, CompletableFuture<PeerSearchResponse> future,
            AtomicReference<InFlight> inFlight, Slot slot, Lookup lookup) {
        boolean setUp = false;
        try {
            if (future.isDone()) {
                return;
            }
            URI uri = checkedUri(peer);
            boolean ssl = "https".equals(uri.getScheme().toLowerCase(Locale.ROOT));
            int port = uri.getPort() != -1 ? uri.getPort() : (ssl ? 443 : 80);
            String host = uri.getHost();
            List<InetAddress> addresses;
            try {
                addresses = resolve(host);
            } finally {
                lookup.end();
            }
            if (future.isDone()) {
                return;
            }

            String requestUri = requestUri(uri, query);
            Function<InetAddress, RequestOptions> options = address -> new RequestOptions()
                    .setMethod(HttpMethod.GET)
                    .setHost(unbracketed(host))
                    .setPort(port)
                    .setServer(SocketAddress.inetSocketAddress(port, address.getHostAddress()))
                    .setSsl(ssl)
                    .setFollowRedirects(false)
                    .setURI(requestUri)
                    .putHeader("Accept", "application/json")
                    .putHeader(FederatedSearchService.HOP_HEADER, "1");

            // The name lookup is over. From here the call's future alone holds the slot, until the
            // answer has been read or the request has been reset.
            setUp = true;
            slot.release();
            connect(addresses, 0, options, query, future, inFlight);
        } catch (PeerSearchException ex) {
            future.completeExceptionally(ex);
        } catch (RuntimeException ex) {
            log.warn("Unexpected failure calling peer {}", peer.getPeerId(), ex);
            future.completeExceptionally(new PeerSearchException.Unreachable("The call failed.", ex));
        } finally {
            lookup.end();
            if (!setUp) {
                slot.release();
            }
        }
    }

    /**
     * Connects to the checked addresses in turn, until a connection is made or none is left, and
     * sends the request over the first connection made.
     */
    private void connect(List<InetAddress> addresses, int index, Function<InetAddress, RequestOptions> options,
            PeerQuery query, CompletableFuture<PeerSearchResponse> future, AtomicReference<InFlight> inFlight) {
        httpClient.request(options.apply(addresses.get(index))).onComplete(connected -> {
            if (connected.failed()) {
                Throwable failure = connected.cause();
                // A full queue is about this peer, not about one of its addresses.
                if (index + 1 < addresses.size() && !future.isDone()
                        && !(failure instanceof ConnectionPoolTooBusyException)) {
                    log.debug("Could not connect to a peer address, trying the next one: {}", failure.toString());
                    connect(addresses, index + 1, options, query, future, inFlight);
                } else {
                    future.completeExceptionally(translate(failure));
                }
                return;
            }
            inFlight.set(new InFlight(connected.result(), Vertx.currentContext()));
            if (future.isDone()) {
                reset(inFlight.get());
                return;
            }
            connected.result().send().onSuccess(response -> read(response, query, future))
                    .onFailure(failure -> future.completeExceptionally(translate(failure)));
        });
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

    private List<InetAddress> resolve(String host) {
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
        if (addresses.length == 0) {
            throw new PeerSearchException.Unreachable("The peer's host name resolves to no address.", null);
        }
        return List.of(addresses);
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
            } catch (RuntimeException ex) {
                // Anything else decoding throws is still the answer's fault, and is told at once
                // rather than when the timeout fires.
                future.completeExceptionally(
                        new PeerSearchException.InvalidResponse("The peer's answer could not be decoded.", ex));
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
            JsonNode count = root.path("count");
            if (!root.path("agents").isArray() || !count.isIntegralNumber() || !count.canConvertToLong()
                    || count.longValue() < 0) {
                throw new PeerSearchException.InvalidResponse("The peer's answer is incomplete.", null);
            }
            for (JsonNode entry : root.path("agents")) {
                if (!isAgent(entry)) {
                    throw new PeerSearchException.InvalidResponse(
                            "The peer's answer has an entry that is not an agent.", null);
                }
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

    /** An object that names an artifact, in a group or in none. */
    private static boolean isAgent(JsonNode entry) {
        if (!entry.isObject()) {
            return false;
        }
        JsonNode artifactId = entry.path("artifactId");
        JsonNode groupId = entry.path("groupId");
        return artifactId.isTextual() && !artifactId.asText().isBlank()
                && (groupId.isMissingNode() || groupId.isNull() || groupId.isTextual());
    }

    private static String requestUri(URI uri, PeerQuery query) {
        // A peer url may carry non-ASCII path characters; they go on the wire percent-encoded.
        String rawPath = URI.create(uri.toASCIIString()).getRawPath();
        String basePath = rawPath == null ? "" : rawPath;
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

    /** A call's place among the name lookups of its peer, given back once, when the lookup ends. */
    private static final class Lookup {
        private final KeyedPermits lookups;
        private final String peerId;
        private final AtomicBoolean ended = new AtomicBoolean();

        Lookup(KeyedPermits lookups, String peerId) {
            this.lookups = lookups;
            this.peerId = peerId;
        }

        void end() {
            if (ended.compareAndSet(false, true)) {
                lookups.release(peerId);
            }
        }
    }

    /**
     * One call's place under the limit on calls in progress. Two parties hold it, the call's future
     * and the thread setting the call up, and it is given back once both have let go: a call that
     * times out while its name lookup is still running keeps its place until the lookup ends, since
     * the thread it occupies is not free either.
     */
    private static final class Slot {
        private final Semaphore slots;
        private final AtomicInteger holders = new AtomicInteger(2);

        Slot(Semaphore slots) {
            this.slots = slots;
        }

        void release() {
            if (holders.decrementAndGet() == 0) {
                slots.release();
            }
        }
    }
}
