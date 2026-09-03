package io.apicurio.registry.perfcomparison.simulations;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.FeederBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.feed;
import static io.gatling.javaapi.core.CoreDsl.listFeeder;
import static io.gatling.javaapi.core.CoreDsl.regex;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class ConfluentApiSimulation extends Simulation {

    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String FEEDER_SUBJECT = "subject";
    private static final String FEEDER_SCHEMA = "schema";
    private static final String FEEDER_SCHEMA_ID = "schemaId";
    private static final String URL = env("SCHEMA_REGISTRY_URL", "http://localhost:8081");
    private static final String OPERATION = env("PERF_OPERATION", "READ_ID");
    private static final int USERS = integer("PERF_USERS", 100);
    private static final int WARMUP_SECONDS = integer("PERF_WARMUP_SECONDS", 60);
    private static final int DURATION_SECONDS = integer("PERF_DURATION_SECONDS", 180);
    private static final int SEED_SCHEMAS = integer("PERF_SEED_SCHEMAS", 1000);
    private static final String RUN_ID = env("PERF_RUN_ID", Long.toString(System.currentTimeMillis()));
    private static final String AUTHORIZATION = authorization();
    private static final String MEDIA_TYPE = "application/vnd.schemaregistry.v1+json";
    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
    private static final HttpClient SETUP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final AtomicLong COUNTER = new AtomicLong();

    private final List<Map<String, Object>> seeds = seed();
    private final FeederBuilder<Object> feeder = listFeeder(seeds).random();
    private final HttpProtocolBuilder protocol = protocol();

    {
        verifyConformance();
        if (OPERATION.equals("REGISTER_NEW_VERSION")) {
            requireSuccess(send("PUT", "/config", "{\"compatibility\":\"NONE\"}"),
                    "disable compatibility for unconstrained version throughput");
        }
        ScenarioBuilder warmup = phase("Warmup").during(Duration.ofSeconds(WARMUP_SECONDS)).on(operation("Warmup"));
        ScenarioBuilder measured = phase("Measured").during(Duration.ofSeconds(DURATION_SECONDS)).on(operation("Measured"));
        // Each phase injects exactly USERS long-lived virtual users. The scenario loop, not the
        // injector, controls phase duration so Gatling never replaces completed users with fresh
        // connections and never extends a configured phase by another injector-duration window.
        setUp(warmup.injectOpen(atOnceUsers(USERS)).andThen(measured.injectOpen(atOnceUsers(USERS))))
                .protocols(protocol).assertions(details("Measured " + OPERATION).failedRequests().percent().lte(1.0));
    }

    private ScenarioBuilder phase(String name) {
        return scenario(name + " " + OPERATION);
    }

    private ChainBuilder operation(String phase) {
        return switch (OPERATION) {
            case "READ_ID" -> feed(feeder).exec(http(phase + " READ_ID").get("/schemas/ids/#{schemaId}")
                    .check(status().is(200)));
            case "READ_VERSION" -> feed(feeder).exec(http(phase + " READ_VERSION")
                    .get("/subjects/#{" + FEEDER_SUBJECT + "}/versions/#{version}").check(status().is(200)));
            case "REGISTER_IDEMPOTENT" -> feed(feeder).exec(http(phase + " REGISTER_IDEMPOTENT")
                    .post("/subjects/#{" + FEEDER_SUBJECT + "}/versions").body(StringBody(session -> body((String) session.get(FEEDER_SCHEMA))))
                    .asJson().check(status().is(200)));
            case "COMPATIBILITY" -> feed(feeder).exec(http(phase + " COMPATIBILITY")
                    .post("/compatibility/subjects/#{subject}/versions/latest")
                    .body(StringBody(session -> body((String) session.get("compatibleSchema")))).asJson()
                    .check(status().is(200), regex("\\\"is_compatible\\\"\\s*:\\s*true").exists()));
            case "REGISTER_NEW_VERSION" -> feed(feeder).exec(http(phase + " REGISTER_NEW_VERSION")
                    .post("/subjects/#{subject}/versions")
                    .body(StringBody(session -> body(uniqueSchema(COUNTER.incrementAndGet())))).asJson()
                    .check(status().is(200)));
            case "REGISTER_NEW_SUBJECT" -> exec(session -> {
                long id = COUNTER.incrementAndGet();
                return session.set("writeId", id).set("writeSchema", uniqueSchema(id));
            }).exec(http(phase + " REGISTER_NEW_SUBJECT")
                    .post("/subjects/bench-" + RUN_ID + "-write-#{writeId}/versions")
                    .body(StringBody(session -> body((String) session.get("writeSchema")))).asJson()
                    .check(status().is(200)));
            default -> throw new IllegalArgumentException("Unsupported PERF_OPERATION: " + OPERATION);
        };
    }

    private static HttpProtocolBuilder protocol() {
        HttpProtocolBuilder result = http.baseUrl(URL).acceptHeader(MEDIA_TYPE).contentTypeHeader(MEDIA_TYPE)
                .userAgentHeader("apicurio-product-neutral-benchmark/1");
        if (!AUTHORIZATION.isEmpty()) {
            result = result.header(HEADER_AUTHORIZATION, AUTHORIZATION);
        }
        return result;
    }

    private List<Map<String, Object>> seed() {
        requireSuccess(send("PUT", "/config", "{\"compatibility\":\"BACKWARD\"}"), "set compatibility");
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < SEED_SCHEMAS; i++) {
            String subject = "bench-" + RUN_ID + "-seed-" + i;
            String schema = uniqueSchema(i);
            HttpResponse<String> response = send("POST", "/subjects/" + subject + "/versions", body(schema));
            Matcher matcher = ID_PATTERN.matcher(response.body());
            if (response.statusCode() / 100 != 2 || !matcher.find()) {
                throw new IllegalStateException("Seed failed: HTTP " + response.statusCode() + " " + response.body());
            }
            result.add(Map.of(FEEDER_SUBJECT, subject, "version", 1, FEEDER_SCHEMA_ID, matcher.group(1), FEEDER_SCHEMA, schema,
                    "compatibleSchema", compatibleSchema(i)));
        }
        return result;
    }

    private void verifyConformance() {
        Map<String, Object> sample = seeds.get(0);
        requireSuccess(send("GET", "/schemas/ids/" + sample.get(FEEDER_SCHEMA_ID), null), "ID lookup");
        requireSuccess(send("GET", "/subjects/" + sample.get(FEEDER_SUBJECT) + "/versions/1", null), "version lookup");
        HttpResponse<String> compatibility = send("POST", "/compatibility/subjects/" + sample.get(FEEDER_SUBJECT)
                + "/versions/latest", body((String) sample.get("compatibleSchema")));
        requireSuccess(compatibility, "compatibility");
        if (!compatibility.body().contains("true")) {
            throw new IllegalStateException("Compatibility response was not true: " + compatibility.body());
        }
        if (OPERATION.equals("REGISTER_IDEMPOTENT")) {
            HttpResponse<String> response = send("POST", "/subjects/" + sample.get(FEEDER_SUBJECT) + "/versions",
                    body((String) sample.get(FEEDER_SCHEMA)));
            requireSuccess(response, "idempotent registration");
            if (!response.body().contains(sample.get(FEEDER_SCHEMA_ID).toString())) {
                throw new IllegalStateException("Idempotent registration returned a different schema ID: "
                        + response.body());
            }
        } else if (OPERATION.equals("REGISTER_NEW_VERSION")) {
            requireSuccess(send("POST", "/subjects/" + sample.get(FEEDER_SUBJECT) + "/versions",
                    body(uniqueSchema(COUNTER.incrementAndGet()))), "new-version registration");
        } else if (OPERATION.equals("REGISTER_NEW_SUBJECT")) {
            long id = COUNTER.incrementAndGet();
            requireSuccess(send("POST", "/subjects/bench-" + RUN_ID + "-conformance-" + id + "/versions",
                    body(uniqueSchema(id))), "new-subject registration");
        }
    }

    private static HttpResponse<String> send(String method, String path, String body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(URL + path)).timeout(Duration.ofSeconds(30))
                    .header("Accept", MEDIA_TYPE).header("Content-Type", MEDIA_TYPE);
            if (!AUTHORIZATION.isEmpty()) {
                builder.header(HEADER_AUTHORIZATION, AUTHORIZATION);
            }
            builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            return SETUP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalStateException("Request failed: " + method + " " + path, e);
        }
    }

    private static void requireSuccess(HttpResponse<String> response, String operation) {
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException(operation + " failed: HTTP " + response.statusCode() + " " + response.body());
        }
    }

    private static String body(String schema) {
        return "{\"schemaType\":\"AVRO\",\"schema\":" + json(schema) + "}";
    }

    private static String uniqueSchema(long id) {
        return "{\"type\":\"record\",\"name\":\"Record" + id + "\",\"namespace\":\"benchmark\","
                + "\"fields\":[{\"name\":\"id\",\"type\":\"long\"}]}";
    }

    private static String compatibleSchema(long id) {
        return "{\"type\":\"record\",\"name\":\"Record" + id + "\",\"namespace\":\"benchmark\","
                + "\"fields\":[{\"name\":\"id\",\"type\":\"long\"},{\"name\":\"note\","
                + "\"type\":[\"null\",\"string\"],\"default\":null}]}";
    }

    private static String json(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String authorization() {
        String user = System.getenv("BASIC_AUTH_USERNAME");
        String password = System.getenv("BASIC_AUTH_PASSWORD");
        if (user == null || password == null) {
            return "";
        }
        return "Basic " + Base64.getEncoder()
                .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int integer(String name, int fallback) {
        return Integer.parseInt(env(name, Integer.toString(fallback)));
    }
}
