package io.apicurio.registry.federation;

import io.apicurio.registry.storage.dto.PeerDto;
import io.vertx.core.Vertx;
import io.vertx.core.net.SelfSignedCertificate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The call to one peer registry, against stub peers on local ports.
 */
class PeerSearchClientTest {

    private static final PeerQuery QUERY = new PeerQuery(null, List.of(), List.of(), List.of(), List.of(), 10);

    private Vertx vertx;
    private FederationConfig config;
    private PeerSearchClient client;
    private final Set<PeerSearchClient> initialized = Collections.newSetFromMap(new IdentityHashMap<>());

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        config = new FederationConfig();
        config.insecureHttpEnabled = true;
        config.loopbackEnabled = true;
        config.searchPeerTimeoutMs = 2000;
        config.searchMaxConcurrentCalls = 8;
        config.searchMaxResponseBytes = 4096;
        client = newClient();
    }

    @AfterEach
    void tearDown() throws Exception {
        client.close();
        vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    private PeerSearchClient newClient() {
        PeerSearchClient created = new PeerSearchClient();
        created.vertx = vertx;
        created.config = config;
        return created;
    }

    private PeerSearchClient started(PeerSearchClient created) {
        if (initialized.add(created)) {
            created.init();
        }
        return created;
    }

    private static PeerDto peer(String url) {
        return PeerDto.builder().peerId("test-peer").url(url).enabled(true).build();
    }

    private PeerSearchResponse succeeded(PeerCall call) throws Exception {
        return call.future().get(10, TimeUnit.SECONDS);
    }

    private static PeerSearchException failed(PeerCall call) throws Exception {
        try {
            call.future().get(10, TimeUnit.SECONDS);
        } catch (ExecutionException ex) {
            return assertInstanceOf(PeerSearchException.class, ex.getCause());
        }
        return fail("Expected the call to fail.");
    }

    private PeerCall search(String url) {
        return started(client).search(peer(url), QUERY);
    }

    @Test
    void asksForPublicAgentsAnonymouslyAndReturnsThem() throws Exception {
        try (StubPeer stub = StubPeer.start(vertx, (request, response) -> StubPeer.publicAgents(response, 3, "a", "b"))) {
            PeerQuery query = new PeerQuery("refund", List.of("pay now", "x&y"), List.of("streaming:false"),
                    List.of("text"), List.of("image"), 5);
            PeerSearchResponse result = succeeded(started(client).search(peer(stub.url() + "/"), query));

            assertEquals(2, result.agents().size());
            assertEquals("a", result.agents().get(0).getArtifactId());
            assertEquals(3, result.reportedCount());

            StubPeer.Seen seen = stub.requests().get(0);
            assertEquals("/.well-known/agents", seen.path());
            assertEquals("name=refund&skill=pay+now&skill=x%26y&capability=streaming%3Afalse&inputMode=text"
                    + "&outputMode=image&offset=0&limit=5&publicOnly=true", seen.query());
            assertNull(seen.header("Authorization"));
            assertNull(seen.header("Cookie"));
            assertEquals("application/json", seen.header("Accept"));
            assertEquals("1", seen.header("X-Federation-Hop"));
        }
    }

    @Test
    void keepsThePathOfAPeerThatIsServedUnderOne() throws Exception {
        try (StubPeer stub = StubPeer.start(vertx, (request, response) -> StubPeer.publicAgents(response, 0))) {
            succeeded(search(stub.url() + "/registry/"));
            assertEquals("/registry/.well-known/agents", stub.requests().get(0).path());
        }
    }

    @Test
    void discardsAnAnswerThatDoesNotConfirmThePublicOnlyMode() throws Exception {
        String legacyAnswer = "{\"count\":1,\"agents\":[{\"groupId\":\"g\",\"artifactId\":\"private-card\"}]}";
        try (StubPeer stub = StubPeer.start(vertx, (request, response) -> StubPeer.json(response, legacyAnswer))) {
            assertInstanceOf(PeerSearchException.Unsupported.class, failed(search(stub.url())));
        }
    }

    @Test
    void aMissingSearchIsUnsupportedAndNotZeroMatches() throws Exception {
        try (StubPeer stub = StubPeer.start(vertx, (request, response) -> response.setStatusCode(404).end())) {
            assertInstanceOf(PeerSearchException.Unsupported.class, failed(search(stub.url())));
        }
    }

    @Test
    void authorizationFailuresAreTold401And403() throws Exception {
        for (int status : new int[] { 401, 403 }) {
            try (StubPeer stub = StubPeer.start(vertx, (request, response) -> response.setStatusCode(status).end())) {
                assertInstanceOf(PeerSearchException.Unauthorized.class, failed(search(stub.url())));
            }
        }
    }

    @Test
    void serverErrorsAreTheirOwnFailure() throws Exception {
        try (StubPeer stub = StubPeer.start(vertx, (request, response) -> response.setStatusCode(503).end())) {
            assertInstanceOf(PeerSearchException.PeerError.class, failed(search(stub.url())));
        }
    }

    @Test
    void doesNotFollowARedirect() throws Exception {
        try (StubPeer target = StubPeer.start(vertx, (request, response) -> StubPeer.publicAgents(response, 0));
                StubPeer redirecting = StubPeer.start(vertx, (request, response) -> response.setStatusCode(302)
                        .putHeader("Location", target.url() + "/.well-known/agents").end())) {
            assertInstanceOf(PeerSearchException.PeerError.class, failed(search(redirecting.url())));
            assertTrue(target.requests().isEmpty(), "The redirect target must not be requested.");
        }
    }

    @Test
    void rejectsAnswersThatAreNotWhatWasAskedFor() throws Exception {
        String notJson = "text/plain";
        try (StubPeer stub = StubPeer.start(vertx, (request, response) -> response
                .putHeader("Content-Type", notJson).end("{\"publicOnly\":true}"))) {
            assertInstanceOf(PeerSearchException.InvalidResponse.class, failed(search(stub.url())));
        }
        try (StubPeer stub = StubPeer.start(vertx, (request, response) -> StubPeer.json(response, "{not json"))) {
            assertInstanceOf(PeerSearchException.InvalidResponse.class, failed(search(stub.url())));
        }
        try (StubPeer stub = StubPeer.start(vertx, (request, response) -> StubPeer.json(response,
                "{\"publicOnly\":true,\"count\":1}"))) {
            assertInstanceOf(PeerSearchException.InvalidResponse.class, failed(search(stub.url())));
        }
        try (StubPeer stub = StubPeer.start(vertx, (request, response) -> response.setStatusCode(204).end())) {
            assertInstanceOf(PeerSearchException.InvalidResponse.class, failed(search(stub.url())));
        }
    }

    @Test
    void rejectsMoreAgentsThanWereAskedFor() throws Exception {
        PeerQuery one = new PeerQuery(null, null, null, null, null, 1);
        try (StubPeer stub = StubPeer.start(vertx, (request, response) -> StubPeer.publicAgents(response, 2, "a", "b"))) {
            PeerCall call = started(client).search(peer(stub.url()), one);
            assertInstanceOf(PeerSearchException.InvalidResponse.class, failed(call));
        }
    }

    @Test
    void rejectsACountBelowTheAgentsReturned() throws Exception {
        try (StubPeer stub = StubPeer.start(vertx, (request, response) -> StubPeer.publicAgents(response, 1, "a", "b"))) {
            assertInstanceOf(PeerSearchException.InvalidResponse.class, failed(search(stub.url())));
        }
    }

    @Test
    void rejectsAnAnswerLargerThanTheLimitWhetherOrNotItDeclaresItsLength() throws Exception {
        config.searchMaxResponseBytes = 200;
        String big = "{\"publicOnly\":true,\"count\":0,\"agents\":[],\"padding\":\"" + "x".repeat(1000) + "\"}";
        try (StubPeer stub = StubPeer.start(vertx, (request, response) -> StubPeer.json(response, big))) {
            assertInstanceOf(PeerSearchException.InvalidResponse.class, failed(search(stub.url())));
        }
        try (StubPeer stub = StubPeer.start(vertx, (request, response) -> response
                .putHeader("Content-Type", "application/json").setChunked(true).write(big.substring(0, 100))
                .onSuccess(ignored -> response.end(big.substring(100))))) {
            assertInstanceOf(PeerSearchException.InvalidResponse.class, failed(search(stub.url())));
        }
    }

    @Test
    void aPeerThatDoesNotAnswerTimesOut() throws Exception {
        config.searchPeerTimeoutMs = 300;
        try (StubPeer stub = StubPeer.start(vertx, (request, response) -> {
        })) {
            assertInstanceOf(PeerSearchException.Timeout.class, failed(search(stub.url())));
        }
    }

    @Test
    void cancellingACallClosesItsConnection() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        try (StubPeer stub = StubPeer.start(vertx, (request, response) -> {
            request.connection().closeHandler(ignored -> closed.countDown());
            received.countDown();
        })) {
            PeerCall call = search(stub.url());
            assertTrue(received.await(10, TimeUnit.SECONDS));

            call.cancel();

            assertInstanceOf(PeerSearchException.Cancelled.class, failed(call));
            assertTrue(closed.await(10, TimeUnit.SECONDS), "The connection should be closed on cancellation.");
        }
    }

    @Test
    void aPeerThatCannotBeConnectedToIsUnreachable() throws Exception {
        int unusedPort;
        try (StubPeer stub = StubPeer.start(vertx, (request, response) -> response.end())) {
            unusedPort = stub.port();
        }
        assertInstanceOf(PeerSearchException.Unreachable.class, failed(search("http://127.0.0.1:" + unusedPort)));
    }

    @Test
    void refusesAnAddressThePolicyDoesNotAllow() throws Exception {
        config.loopbackEnabled = false;
        assertInstanceOf(PeerSearchException.AddressRefused.class, failed(search("http://127.0.0.1:9")));

        config.loopbackEnabled = true;
        config.insecureHttpEnabled = false;
        assertInstanceOf(PeerSearchException.AddressRefused.class, failed(search("http://127.0.0.1:9")));
    }

    @Test
    void refusesAHostNameThatResolvesToAnAddressThePolicyDoesNotAllow() throws Exception {
        config.loopbackEnabled = false;
        started(client);
        client.resolver = host -> new InetAddress[] { InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 }) };
        assertInstanceOf(PeerSearchException.AddressRefused.class, failed(client.search(peer("http://peer.test:9"), QUERY)));

        // One refused address among allowed ones is enough, whatever order they come back in.
        client.resolver = host -> new InetAddress[] { InetAddress.getByAddress(new byte[] { 10, 0, 0, 5 }),
                InetAddress.getByAddress(new byte[] { (byte) 169, (byte) 254, (byte) 169, (byte) 254 }) };
        assertInstanceOf(PeerSearchException.AddressRefused.class, failed(client.search(peer("http://peer.test:9"), QUERY)));

        // An IPv4-mapped IPv6 address is judged as the IPv4 address it wraps.
        client.resolver = host -> new InetAddress[] { InetAddress.getByName("::ffff:127.0.0.1") };
        assertInstanceOf(PeerSearchException.AddressRefused.class, failed(client.search(peer("http://peer.test:9"), QUERY)));
    }

    @Test
    void connectsToTheAddressThatWasCheckedAndKeepsTheHostName() throws Exception {
        AtomicInteger lookups = new AtomicInteger();
        try (StubPeer stub = StubPeer.start(vertx, (request, response) -> StubPeer.publicAgents(response, 1, "a"))) {
            started(client);
            // "peer.test" resolves nowhere but here, so the request can only arrive by connecting to
            // the address this resolver returned, and it must have been resolved only once.
            client.resolver = host -> {
                lookups.incrementAndGet();
                assertEquals("peer.test", host);
                return new InetAddress[] { InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 }) };
            };

            PeerSearchResponse result = succeeded(client.search(peer("http://peer.test:" + stub.port()), QUERY));

            assertEquals(1, result.agents().size());
            assertEquals(1, lookups.get());
            assertEquals("peer.test:" + stub.port(), stub.requests().get(0).header("Host"));
        }
    }

    @Test
    void aHostThatCannotBeResolvedIsUnreachable() throws Exception {
        started(client);
        client.resolver = host -> {
            throw new UnknownHostException(host);
        };
        assertInstanceOf(PeerSearchException.Unreachable.class, failed(client.search(peer("http://peer.test:9"), QUERY)));
    }

    @Test
    void refusesACallWhenNoThreadIsLeftToSetItUp() throws Exception {
        config.searchMaxConcurrentCalls = 1;
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch resolving = new CountDownLatch(1);
        started(client);
        client.resolver = host -> {
            resolving.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            throw new UnknownHostException(host);
        };

        PeerCall first = client.search(peer("http://peer.test:9"), QUERY);
        assertTrue(resolving.await(10, TimeUnit.SECONDS));
        PeerCall second = client.search(peer("http://other.test:9"), QUERY);

        assertInstanceOf(PeerSearchException.CapacityExceeded.class, failed(second));
        assertFalse(first.future().isDone());
        release.countDown();
        assertInstanceOf(PeerSearchException.Unreachable.class, failed(first));
    }

    @Test
    void verifiesTheCertificateAgainstTheNameOfThePeerAndNotItsAddress() throws Exception {
        SelfSignedCertificate certificate = SelfSignedCertificate.create("peer.test");
        try (StubPeer stub = StubPeer.startTls(vertx, certificate,
                (request, response) -> StubPeer.publicAgents(response, 1, "a"))) {
            client.close();
            client = newClient();
            client.optionsCustomizer = options -> options.setTrustOptions(certificate.trustOptions());
            started(client);
            client.resolver = host -> new InetAddress[] { InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 }) };

            // The certificate is for peer.test and the connection is to 127.0.0.1: this only works
            // if the name, not the address, is what the certificate is checked against.
            assertEquals(1, succeeded(client.search(peer("https://peer.test:" + stub.port()), QUERY)).agents().size());
        }
    }

    @Test
    void rejectsACertificateForAnotherName() throws Exception {
        SelfSignedCertificate certificate = SelfSignedCertificate.create("other.test");
        try (StubPeer stub = StubPeer.startTls(vertx, certificate,
                (request, response) -> StubPeer.publicAgents(response, 1, "a"))) {
            client.close();
            client = newClient();
            client.optionsCustomizer = options -> options.setTrustOptions(certificate.trustOptions());
            started(client);
            client.resolver = host -> new InetAddress[] { InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 }) };

            assertInstanceOf(PeerSearchException.Unreachable.class,
                    failed(client.search(peer("https://peer.test:" + stub.port()), QUERY)));
        }
    }

    @Test
    void rejectsACertificateThatIsNotTrusted() throws Exception {
        SelfSignedCertificate certificate = SelfSignedCertificate.create("peer.test");
        try (StubPeer stub = StubPeer.startTls(vertx, certificate,
                (request, response) -> StubPeer.publicAgents(response, 1, "a"))) {
            started(client);
            client.resolver = host -> new InetAddress[] { InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 }) };

            assertInstanceOf(PeerSearchException.Unreachable.class,
                    failed(client.search(peer("https://peer.test:" + stub.port()), QUERY)));
        }
    }
}
