package io.apicurio.registry.perftest.simulations;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

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
 */
public class RegistryApiSimulation extends Simulation {

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

    private static String envOrDefault(String name, String def) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? def : v;
    }

    private static final String AVRO_SCHEMA = "{"
            + "\"type\":\"record\",\"name\":\"PerfTestRecord\",\"namespace\":\"io.apicurio.registry.perftest\","
            + "\"fields\":[{\"name\":\"id\",\"type\":\"string\"},{\"name\":\"value\",\"type\":\"long\"}]}";

    HttpProtocolBuilder httpProtocol = http.baseUrl(REGISTRY_URL)
            .acceptHeader("application/json").contentTypeHeader("application/json")
            .userAgentHeader("apicurio-registry-perf-tests/gatling");

    /**
     * If OAuth is configured (checked once, at simulation-build time, since it's an
     * environment-level setting rather than something that varies per-request), fetch a
     * client-credentials token once per virtual user and inject it as an Authorization header on
     * every subsequent request in the scenario.
     */
    ScenarioBuilder scn = buildScenario();

    private static ScenarioBuilder buildScenario() {
        var builder = scenario("Registry REST API").exec(
                session -> session.set("groupId", "perf-test-group-" + GROUP_COUNTER.incrementAndGet()));
        if (OAUTH_ENABLED) {
            builder = builder.exec(http("Fetch OAuth token").post(TOKEN_ENDPOINT)
                    .formParam("grant_type", "client_credentials").formParam("client_id", CLIENT_ID)
                    .formParam("client_secret", CLIENT_SECRET)
                    .check(jmesPath("access_token").saveAs("accessToken")));
        }
        return builder
                .exec(http("Create artifact").post("/groups/#{groupId}/artifacts")
                        .queryParam("ifExists", "FAIL")
                        .header("Authorization", RegistryApiSimulation::authHeader)
                        .body(StringBody(RegistryApiSimulation::createArtifactBody)).asJson()
                        .check(status().is(200), jmesPath("id").saveAs("artifactId")))
                .exec(http("Get artifact content").get("/groups/#{groupId}/artifacts/#{artifactId}")
                        .header("Authorization", RegistryApiSimulation::authHeader)
                        .check(status().is(200)))
                .exec(http("Get artifact metadata")
                        .get("/groups/#{groupId}/artifacts/#{artifactId}/meta")
                        .header("Authorization", RegistryApiSimulation::authHeader)
                        .check(status().is(200)))
                .exec(http("Search artifacts").get("/search/artifacts").queryParam("limit", "20")
                        .header("Authorization", RegistryApiSimulation::authHeader)
                        .check(status().is(200)))
                .pause(Duration.ofMillis(50), Duration.ofMillis(250));
    }

    private static String authHeader(io.gatling.javaapi.core.Session session) {
        if (session.contains("accessToken")) {
            return "Bearer " + session.getString("accessToken");
        }
        return "";
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
