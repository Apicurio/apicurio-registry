package io.apicurio.tests.federation;

import io.apicurio.registry.utils.tests.TestUtils;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Isolated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startables;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static io.apicurio.deployment.Constants.FEDERATION;
import static io.apicurio.tests.kafkasql.startup.infra.Configs.REGISTRY_V3_IMAGE;
import static io.apicurio.tests.kafkasql.startup.infra.Configs.REGISTRY_V3_IMAGE_DEFAULT;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.oneOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Federated agent search across two real registry instances. Deploys its own registry containers
 * on a private network; it does not use the matrix-deployed registry.
 * <p>
 * The {@code local} registry has the {@code remote} one registered as a peer. The tests run in
 * order, because the last one stops the peer. The class is isolated because it asserts on elapsed
 * time, which test classes running next to it would stretch.
 */
@Tag(FEDERATION)
@Isolated
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FederatedSearchMultiInstanceIT {

    private static final Logger log = LoggerFactory.getLogger(FederatedSearchMultiInstanceIT.class);

    private static final long DEADLINE_MS = 4000;
    private static final long PEER_TIMEOUT_MS = 2000;
    // The deadline bounds the work of the local registry. The test measures the whole HTTP round
    // trip from outside the container, so it allows this much on top for a loaded CI runner.
    private static final long ROUND_TRIP_SLACK_MS = 1500;

    private static final String REMOTE_ALIAS = "remote-registry";
    private static final String PEER_ID = "remote";
    private static final String SKILL = "multi-instance-search";

    private static final String VISIBILITY_LABEL = "apicurio.agent.visibility";

    private static final String CARD = """
            {
                "name": "%s",
                "description": "An agent for the multi-instance federated search test",
                "version": "1.0.0",
                "supportedInterfaces": [
                    { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                ],
                "capabilities": { "streaming": false, "pushNotifications": false },
                "skills": [ { "id": "%s", "name": "Search", "description": "A test skill", "tags": ["t"] } ],
                "defaultInputModes": ["text"],
                "defaultOutputModes": ["text"]
            }
            """;

    private static Network network;
    private static GenericContainer<?> remote;
    private static GenericContainer<?> local;

    @BeforeAll
    static void startRegistries() {
        network = Network.newNetwork();
        remote = registry(Map.of()).withNetworkAliases(REMOTE_ALIAS);
        local = registry(Map.of(
                "APICURIO_FEDERATION_ENABLED", "true",
                // The peer is reached over plain http on the container network.
                "APICURIO_FEDERATION_PEER_INSECURE_HTTP_ENABLED", "true",
                "APICURIO_FEDERATION_SEARCH_DEADLINE_MS", String.valueOf(DEADLINE_MS),
                "APICURIO_FEDERATION_SEARCH_PEER_TIMEOUT_MS", String.valueOf(PEER_TIMEOUT_MS),
                // The failures below are deliberate. Keep the circuit breaker from opening on them,
                // so that every search really calls the peer.
                "APICURIO_FEDERATION_SEARCH_BREAKER_REQUEST_VOLUME_THRESHOLD", "100"));
        Startables.deepStart(remote, local).join();
        remote.followOutput(new Slf4jLogConsumer(log).withPrefix("remote"));
        local.followOutput(new Slf4jLogConsumer(log).withPrefix("local"));

        createAgent(local, "local-agent", "public");
        createAgent(remote, "remote-public-agent", "public");
        createAgent(remote, "remote-private-agent", "private");
        // No label: the default visibility is entitled, which is not public either.
        createAgent(remote, "remote-unlabelled-agent", null);

        given().contentType(ContentType.JSON)
                .body(Map.of("peerId", PEER_ID, "url", "http://" + REMOTE_ALIAS + ":8080"))
                .post(url(local) + "/apis/registry/v3/admin/peers")
                .then().statusCode(204);
    }

    @AfterAll
    static void stopRegistries() {
        if (local != null) {
            local.stop();
        }
        if (remote != null) {
            remote.stop();
        }
        if (network != null) {
            network.close();
        }
    }

    @Test
    @Order(1)
    void returnsLocalAndPublicRemoteAgentsEachWithItsSource() {
        // The remote registry does hold all three cards, so the two that are missing from the
        // federated answer below were left out, not absent.
        given().queryParam("skill", SKILL).get(url(remote) + "/.well-known/agents").then()
                .statusCode(200)
                .body("agents.artifactId", containsInAnyOrder("remote-public-agent", "remote-private-agent",
                        "remote-unlabelled-agent"));

        search().statusCode(200)
                .body("agents.source", contains("local", PEER_ID))
                .body("agents.artifactId", contains("local-agent", "remote-public-agent"))
                .body("sources.source", contains("local", PEER_ID))
                .body("sources.outcome", contains("ok", "ok"))
                .body("sources.count", contains(1, 1))
                .body("sources.truncated", contains(false, false))
                .body("sources.reason", contains(nullValue(), nullValue()));
    }

    @Test
    @Order(2)
    void anUnresponsivePeerTimesOutAndLocalResultsArriveWithinTheDeadline() throws Exception {
        var docker = DockerClientFactory.instance().client();

        // A paused container still accepts the connection but never answers, so the call to it can
        // only end at the per-peer timeout.
        docker.pauseContainerCmd(remote.getContainerId()).exec();
        try {
            long startNanos = System.nanoTime();
            ValidatableResponse response = search();
            long elapsedMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

            assertPartialResult(response, "timeout");
            assertTrue(elapsedMs >= PEER_TIMEOUT_MS,
                    "The search answered after " + elapsedMs + " ms, before the per-peer timeout of "
                            + PEER_TIMEOUT_MS + " ms, so it did not wait for the peer.");
            assertWithinDeadline(elapsedMs);
        } finally {
            docker.unpauseContainerCmd(remote.getContainerId()).exec();
        }

        // The peer is back: the next test must start from a search that reaches it.
        TestUtils.retry(() -> search().statusCode(200)
                .body("agents.artifactId", contains("local-agent", "remote-public-agent"))
                .body("sources.outcome", contains("ok", "ok")));
    }

    @Test
    @Order(3)
    void aStoppedPeerIsReportedAsFailedAndLocalResultsArriveWithinTheDeadline() {
        remote.stop();

        long startNanos = System.nanoTime();
        ValidatableResponse response = search();
        long elapsedMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

        assertPartialResult(response, null);
        // Both reasons are correct for a peer that is gone. Which one is reported depends on whether
        // the local registry still has the peer's address cached: with it, the connection attempt
        // runs into the per-peer timeout; without it, the name no longer resolves.
        response.body("sources[1].reason", oneOf("unreachable", "timeout"));
        assertWithinDeadline(elapsedMs);
    }

    /**
     * The local registry answered 200 with its own agent, and reported the peer as failed.
     *
     * @param reason the reason the peer's source entry must carry, or null to leave it unchecked
     */
    private static void assertPartialResult(ValidatableResponse response, String reason) {
        response.statusCode(200)
                .body("agents.source", contains("local"))
                .body("agents.artifactId", contains("local-agent"))
                .body("sources.source", contains("local", PEER_ID))
                .body("sources[0].outcome", equalTo("ok"))
                .body("sources[0].count", equalTo(1))
                .body("sources[1].outcome", equalTo("failed"))
                .body("sources[1].count", equalTo(0));
        if (reason != null) {
            response.body("sources[1].reason", equalTo(reason));
        }
    }

    private static void assertWithinDeadline(long elapsedMs) {
        assertTrue(elapsedMs <= DEADLINE_MS + ROUND_TRIP_SLACK_MS,
                "The search took " + elapsedMs + " ms, more than the deadline of " + DEADLINE_MS
                        + " ms plus " + ROUND_TRIP_SLACK_MS + " ms for the round trip.");
    }

    private static ValidatableResponse search() {
        return given().queryParam("skill", SKILL).get(url(local) + "/apis/registry/v3/search/federated").then();
    }

    private static GenericContainer<?> registry(Map<String, String> env) {
        Map<String, String> allEnv = new HashMap<>(env);
        // Federation and the agent registry are experimental features.
        allEnv.put("APICURIO_FEATURES_EXPERIMENTAL_ENABLED", "true");
        allEnv.put("APICURIO_A2A_ENABLED", "true");
        return new GenericContainer<>(System.getProperty(REGISTRY_V3_IMAGE, REGISTRY_V3_IMAGE_DEFAULT))
                .withEnv(allEnv)
                .withNetwork(network)
                // 8080 serves the API, 9000 is the management interface with the health checks.
                .withExposedPorts(8080, 9000)
                .waitingFor(Wait.forHttp("/health/ready").forPort(9000).forStatusCode(200)
                        .withStartupTimeout(Duration.ofSeconds(120)));
    }

    private static String url(GenericContainer<?> registry) {
        return "http://" + registry.getHost() + ":" + registry.getMappedPort(8080);
    }

    private static void createAgent(GenericContainer<?> registry, String artifactId, String visibility) {
        Map<String, Object> body = new HashMap<>();
        body.put("artifactId", artifactId);
        body.put("artifactType", "AGENT_CARD");
        if (visibility != null) {
            body.put("labels", Map.of(VISIBILITY_LABEL, visibility));
        }
        body.put("firstVersion", Map.of("content",
                Map.of("content", CARD.formatted(artifactId, SKILL), "contentType", "application/json")));

        given().contentType(ContentType.JSON).body(body)
                .post(url(registry) + "/apis/registry/v3/groups/federation-it/artifacts")
                .then().statusCode(200);
    }
}
