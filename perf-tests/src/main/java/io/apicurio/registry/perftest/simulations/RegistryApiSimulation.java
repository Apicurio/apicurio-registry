package io.apicurio.registry.perftest.simulations;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.Choice;
import io.gatling.javaapi.core.FeederBuilder;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 *   <li>{@code PERF_WRITE_RATIO} - fraction of iterations that register a *new* artifact, as
 *       opposed to reading a pre-existing one (default {@code 0.05} = 5%). Real schema registry
 *       traffic is overwhelmingly read-heavy - producers/consumers resolving already-registered
 *       schemas by ID on (almost) every message, with new schema registration happening rarely
 *       (on deploy). The previous default of this simulation - 100% writes, a brand-new artifact
 *       every iteration - was the least representative traffic pattern a real registry sees. Set
 *       to {@code 1.0} to restore the old all-writes behavior.
 *   <li>{@code PERF_SEED_ARTIFACTS} - how many artifacts to pre-register before the timed run
 *       starts, forming the pool that read iterations pick from at random (default 200)
 *   <li>{@code PERF_LARGE_SCHEMA} - if {@code true}, use a much larger (~8KB, ~150-field) Avro
 *       schema instead of the tiny default one, to exercise payload-size-sensitive code paths
 *       (parsing, canonicalization, storage) under more extreme conditions (default false)
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
    private static final double WRITE_RATIO = Double.parseDouble(envOrDefault("PERF_WRITE_RATIO", "0.05"));
    private static final int SEED_ARTIFACTS = Integer
            .parseInt(envOrDefault("PERF_SEED_ARTIFACTS", "200"));
    private static final boolean LARGE_SCHEMA = Boolean
            .parseBoolean(envOrDefault("PERF_LARGE_SCHEMA", "false"));

    private static final String SEED_GROUP = "perf-test-seed";

    private static final AtomicInteger GROUP_COUNTER = new AtomicInteger();

    // Cached, shared across all virtual users - see class Javadoc "Token handling".
    private static final AtomicReference<String> CACHED_TOKEN = new AtomicReference<>();
    private static final HttpClient SETUP_HTTP_CLIENT = HttpClient.newHttpClient();
    private static final Pattern ACCESS_TOKEN_PATTERN = Pattern
            .compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");

    private static String envOrDefault(String name, String def) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? def : v;
    }

    private static final String SMALL_AVRO_SCHEMA = "{"
            + "\"type\":\"record\",\"name\":\"PerfTestRecord\",\"namespace\":\"io.apicurio.registry.perftest\","
            + "\"fields\":[{\"name\":\"id\",\"type\":\"string\"},{\"name\":\"value\",\"type\":\"long\"}]}";

    private static final String AVRO_SCHEMA = LARGE_SCHEMA ? buildLargeAvroSchema() : SMALL_AVRO_SCHEMA;

    /**
     * Builds a large (~150 field, several KB) Avro record schema, to exercise payload-size-
     * sensitive code paths (JSON parsing, canonicalization, content hashing/storage) under more
     * extreme conditions than the tiny 2-field default schema.
     */
    private static String buildLargeAvroSchema() {
        StringBuilder fields = new StringBuilder();
        for (int i = 0; i < 150; i++) {
            if (i > 0) {
                fields.append(',');
            }
            fields.append("{\"name\":\"field").append(i).append("\",\"type\":[\"null\",\"string\"],")
                    .append("\"default\":null}");
        }
        return "{\"type\":\"record\",\"name\":\"PerfTestLargeRecord\","
                + "\"namespace\":\"io.apicurio.registry.perftest\",\"fields\":[" + fields + "]}";
    }

    // Note: deliberately no default contentTypeHeader() here - JSON-bodied requests set their
    // own content type via .asJson().
    HttpProtocolBuilder httpProtocol = http.baseUrl(REGISTRY_URL).acceptHeader("application/json")
            .userAgentHeader("apicurio-registry-perf-tests/gatling");

    // Must run before `scn = buildScenario()` below: seedArtifactRecords() (called from
    // buildScenario()) needs CACHED_TOKEN already populated to authenticate its seeding calls.
    // Field initializers and instance-init blocks run in textual/declaration order, so this
    // block being written before the `scn` field guarantees that ordering.
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
    }

    ScenarioBuilder scn = buildScenario();

    private static ScenarioBuilder buildScenario() {
        FeederBuilder<Object> seedFeeder = listFeeder(seedArtifactRecords()).random();

        ChainBuilder writeChain = exec(
                session -> session.set("groupId", "perf-test-group-" + GROUP_COUNTER.incrementAndGet()))
                .exec(http("Create artifact").post("/groups/#{groupId}/artifacts")
                        .queryParam("ifExists", "FAIL")
                        .header("Authorization", RegistryApiSimulation::authHeader)
                        .body(StringBody(RegistryApiSimulation::createArtifactBody)).asJson()
                        .check(status().is(200), jmesPath("artifact.artifactId").saveAs("artifactId")))
                .exec(http("Get artifact content (write path)")
                        .get("/groups/#{groupId}/artifacts/#{artifactId}/versions/branch=latest/content")
                        .header("Authorization", RegistryApiSimulation::authHeader)
                        .check(status().is(200)))
                .exec(http("Get artifact metadata (write path)")
                        .get("/groups/#{groupId}/artifacts/#{artifactId}")
                        .header("Authorization", RegistryApiSimulation::authHeader)
                        .check(status().is(200)));

        // The realistic path: resolve an already-registered schema, the way a Kafka
        // producer/consumer does on (close to) every message - see PERF_WRITE_RATIO javadoc.
        ChainBuilder readChain = feed(seedFeeder)
                .exec(http("Get artifact content (read path)")
                        .get("/groups/" + SEED_GROUP + "/artifacts/#{seedArtifactId}/versions/branch=latest/content")
                        .header("Authorization", RegistryApiSimulation::authHeader)
                        .check(status().is(200)))
                .exec(http("Get artifact metadata (read path)")
                        .get("/groups/" + SEED_GROUP + "/artifacts/#{seedArtifactId}")
                        .header("Authorization", RegistryApiSimulation::authHeader)
                        .check(status().is(200)));

        return scenario("Registry REST API")
                .randomSwitch().on(new Choice.WithWeight(WRITE_RATIO * 100, writeChain),
                        new Choice.WithWeight((1 - WRITE_RATIO) * 100, readChain))
                .exec(http("Search artifacts").get("/search/artifacts").queryParam("limit", "20")
                        .header("Authorization", RegistryApiSimulation::authHeader)
                        .check(status().is(200)))
                .pause(Duration.ofMillis(50), Duration.ofMillis(250));
    }

    /**
     * Pre-registers {@code PERF_SEED_ARTIFACTS} artifacts (blocking, before the timed run starts,
     * parallelized since each call is independent I/O-bound work) so the read-heavy path has a
     * realistic pool of already-existing schemas to resolve by ID - a real registry accumulates
     * schemas over time; a brand new empty instance with only just-created artifacts wouldn't
     * exercise lookup paths (e.g. index/cache behavior over a non-trivial data set) realistically.
     */
    private static List<Map<String, Object>> seedArtifactRecords() {
        log.info("Seeding {} artifacts in group '{}' for the read-heavy scenario...", SEED_ARTIFACTS,
                SEED_GROUP);
        int parallelism = Math.min(20, Math.max(1, SEED_ARTIFACTS));
        ExecutorService seedExecutor = Executors.newFixedThreadPool(parallelism);
        List<Future<Map<String, Object>>> futures = new ArrayList<>();
        for (int i = 0; i < SEED_ARTIFACTS; i++) {
            int index = i;
            futures.add(seedExecutor.submit(() -> seedOneArtifact(index)));
        }
        List<Map<String, Object>> records = new ArrayList<>();
        for (Future<Map<String, Object>> future : futures) {
            try {
                Map<String, Object> record = future.get();
                if (record != null) {
                    records.add(record);
                }
            } catch (Exception e) {
                log.warn("Failed to seed an artifact", e);
            }
        }
        seedExecutor.shutdown();
        log.info("Seeded {} of {} requested artifacts.", records.size(), SEED_ARTIFACTS);
        return records;
    }

    private static Map<String, Object> seedOneArtifact(int index) {
        String artifactId = "perf-seed-" + index;
        try {
            String body = "{\"artifactId\":\"" + artifactId + "\",\"artifactType\":\"AVRO\","
                    + "\"firstVersion\":{\"content\":{\"content\":" + jsonEscape(AVRO_SCHEMA)
                    + ",\"contentType\":\"application/json\"}}}";
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(REGISTRY_URL + "/groups/" + SEED_GROUP
                            + "/artifacts?ifExists=FIND_OR_CREATE_VERSION"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            String token = CACHED_TOKEN.get();
            if (token != null) {
                requestBuilder.header("Authorization", "Bearer " + token);
            }
            HttpResponse<Void> response = SETUP_HTTP_CLIENT.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 200) {
                log.warn("Failed to seed artifact {}: HTTP {}", artifactId, response.statusCode());
                return null;
            }
        } catch (Exception e) {
            log.warn("Failed to seed artifact {}", artifactId, e);
            return null;
        }
        Map<String, Object> record = new HashMap<>();
        record.put("seedArtifactId", artifactId);
        return record;
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
            HttpResponse<String> response = SETUP_HTTP_CLIENT.send(request,
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
        setUp(scn.injectOpen(rampUsers(USERS).during(Duration.ofSeconds(10)),
                constantUsersPerSec(Math.max(1, USERS / 2)).during(Duration.ofSeconds(DURATION_SECONDS))))
                .protocols(httpProtocol)
                .assertions(global().failedRequests().percent().lte(1.0));
    }
}


