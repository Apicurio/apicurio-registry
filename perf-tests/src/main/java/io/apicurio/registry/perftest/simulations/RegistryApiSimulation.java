package io.apicurio.registry.perftest.simulations;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * End-to-end performance simulation for Apicurio Registry, intended to run against a realistic
 * deployment: registry installed via the operator, backed by PostgreSQL, secured with Keycloak
 * (OAuth2 client-credentials), while a separate Kafka client (see {@link
 * io.apicurio.registry.perftest.kafka.KafkaLoadGenerator}) drives concurrent serde-based traffic
 * against the same registry instance.
 *
 * <p>Configuration is entirely environment-variable driven so this can run unmodified in CI
 * (perf-main.yaml) or locally against any deployment:
 *
 * <ul>
 *   <li>{@code REGISTRY_URL} - base URL of the registry REST API (e.g.
 *       {@code http://localhost:8080/apis/registry/v3})
 *   <li>{@code AUTH_TOKEN_ENDPOINT} - OIDC token endpoint (Keycloak realm token URL). If unset,
 *       requests are sent without an Authorization header (useful for anonymous-read setups).
 *   <li>{@code AUTH_CLIENT_ID} / {@code AUTH_CLIENT_SECRET} - OAuth2 client-credentials
 *   <li>{@code PERF_USERS} - number of concurrent virtual users (default 20)
 *   <li>{@code PERF_DURATION_SECONDS} - how long to sustain the load (default 120)
 * </ul>
 *
 * <p><b>Token handling:</b> a real client (e.g. the Kafka serde's registry REST client - see
 * {@code KafkaLoadGenerator}) obtains one OAuth token per client instance and caches/reuses it
 * across every subsequent call, only refreshing when it's close to expiry (confirmed against
 * both of the java-sdk's HTTP adapters). This simulation mirrors that: the token is fetched once
 * up front (and refreshed periodically in the background, well before expiry) rather than once
 * per Gatling virtual user/iteration - fetching per-iteration would model an unrealistic client
 * and would put artificial load on Keycloak rather than the registry under test.
 */
public class RegistryApiSimulation extends Simulation {

    private static final Logger log = LoggerFactory.getLogger(RegistryApiSimulation.class);

    private static final String REGISTRY_URL = envOrDefault("REGISTRY_URL",
            "http://localhost:8080/apis/registry/v3");
    private static final String TOKEN_ENDPOINT = envOrDefault("AUTH_TOKEN_ENDPOINT", "");
    private static final boolean OAUTH_ENABLED = !TOKEN_ENDPOINT.isBlank();
    private static final String CLIENT_ID = System.getenv("AUTH_CLIENT_ID");
    private static final String CLIENT_SECRET = System.getenv("AUTH_CLIENT_SECRET");

    private static final int USERS = Integer.parseInt(envOrDefault("PERF_USERS", "20"));
    private static final int DURATION_SECONDS = Integer
            .parseInt(envOrDefault("PERF_DURATION_SECONDS", "120"));

    private static final AtomicInteger GROUP_COUNTER = new AtomicInteger();

    // Cached, shared across all virtual users - see class Javadoc "Token handling".
    private static final AtomicReference<String> CACHED_TOKEN = new AtomicReference<>();
    private static final HttpClient TOKEN_HTTP_CLIENT = HttpClient.newHttpClient();
    private static final Pattern ACCESS_TOKEN_PATTERN = Pattern
            .compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");

    private static String envOrDefault(String name, String def) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? def : v;
    }

    private static final String AVRO_SCHEMA = "{"
            + "\"type\":\"record\",\"name\":\"PerfTestRecord\",\"namespace\":\"io.apicurio.registry.perftest\","
            + "\"fields\":[{\"name\":\"id\",\"type\":\"string\"},{\"name\":\"value\",\"type\":\"long\"}]}";

    // Note: deliberately no default contentTypeHeader() here - JSON-bodied requests set their
    // own content type via .asJson().
    HttpProtocolBuilder httpProtocol = http.baseUrl(REGISTRY_URL).acceptHeader("application/json")
            .userAgentHeader("apicurio-registry-perf-tests/gatling");

    ScenarioBuilder scn = buildScenario();

    private static ScenarioBuilder buildScenario() {
        var builder = scenario("Registry REST API").exec(
                session -> session.set("groupId", "perf-test-group-" + GROUP_COUNTER.incrementAndGet()));
        return builder
                .exec(http("Create artifact").post("/groups/#{groupId}/artifacts")
                        .queryParam("ifExists", "FAIL")
                        .header("Authorization", RegistryApiSimulation::authHeader)
                        .body(StringBody(RegistryApiSimulation::createArtifactBody)).asJson()
                        .check(status().is(200), jmesPath("artifact.artifactId").saveAs("artifactId")))
                .exec(http("Get artifact content")
                        .get("/groups/#{groupId}/artifacts/#{artifactId}/versions/branch=latest/content")
                        .header("Authorization", RegistryApiSimulation::authHeader)
                        .check(status().is(200)))
                .exec(http("Get artifact metadata").get("/groups/#{groupId}/artifacts/#{artifactId}")
                        .header("Authorization", RegistryApiSimulation::authHeader)
                        .check(status().is(200)))
                .exec(http("Search artifacts").get("/search/artifacts").queryParam("limit", "20")
                        .header("Authorization", RegistryApiSimulation::authHeader)
                        .check(status().is(200)))
                .pause(Duration.ofMillis(50), Duration.ofMillis(250));
    }

    private static String authHeader(io.gatling.javaapi.core.Session session) {
        String token = CACHED_TOKEN.get();
        if (token != null) {
            return "Bearer " + token;
        }
        return "";
    }

    /**
     * Fetches a fresh client-credentials token and caches it, mirroring the real serde client's
     * behavior (see class Javadoc). Called once at startup and then periodically in the
     * background, well before the token's reported expiry.
     */
    private static void refreshToken() {
        try {
            String form = "grant_type=client_credentials&client_id=" + CLIENT_ID + "&client_secret="
                    + CLIENT_SECRET;
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(TOKEN_ENDPOINT))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build();
            HttpResponse<String> response = TOKEN_HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("Failed to fetch OAuth token: HTTP {} - {}", response.statusCode(),
                        response.body());
                return;
            }
            Matcher tokenMatcher = ACCESS_TOKEN_PATTERN.matcher(response.body());
            if (!tokenMatcher.find()) {
                log.error("OAuth token response did not contain an access_token: {}", response.body());
                return;
            }
            CACHED_TOKEN.set(tokenMatcher.group(1));
            log.info("Refreshed OAuth token (cached for reuse across all virtual users)");
        } catch (Exception e) {
            log.error("Failed to fetch OAuth token", e);
        }
    }

    private static String createArtifactBody(io.gatling.javaapi.core.Session session) {
        String artifactId = "perf-" + session.userId() + "-"
                + ThreadLocalRandom.current().nextInt(1_000_000);
        return "{"
                + "\"artifactId\":\"" + artifactId + "\","
                + "\"artifactType\":\"AVRO\","
                + "\"firstVersion\":{\"content\":{\"content\":"
                + jsonEscape(AVRO_SCHEMA) + ",\"contentType\":\"application/json\"}}"
                + "}";
    }

    private static String jsonEscape(String raw) {
        return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    {
        if (OAUTH_ENABLED) {
            refreshToken();
            if (CACHED_TOKEN.get() == null) {
                throw new IllegalStateException(
                        "OAuth is configured (AUTH_TOKEN_ENDPOINT set) but the initial token fetch "
                                + "failed - see logs above. Aborting rather than running the whole "
                                + "load test unauthenticated.");
            }
            ScheduledExecutorService refreshExecutor = Executors.newSingleThreadScheduledExecutor();
            // Refresh well before the realm's access-token lifespan (300s in the perf-main
            // Keycloak realm) expires - a fixed conservative interval rather than parsing
            // expires_in per-refresh keeps this simple and safe for typical realm configs.
            refreshExecutor.scheduleAtFixedRate(RegistryApiSimulation::refreshToken, 60, 60,
                    TimeUnit.SECONDS);
        }

        setUp(scn.injectOpen(rampUsers(USERS).during(Duration.ofSeconds(10)),
                constantUsersPerSec(Math.max(1, USERS / 2)).during(Duration.ofSeconds(DURATION_SECONDS))))
                .protocols(httpProtocol)
                .assertions(global().failedRequests().percent().lte(1.0));
    }
}

