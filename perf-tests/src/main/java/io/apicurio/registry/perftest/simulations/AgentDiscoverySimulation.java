package io.apicurio.registry.perftest.simulations;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static io.apicurio.registry.perftest.simulations.RegistryApiSimulation.CACHED_TOKEN;
import static io.apicurio.registry.perftest.simulations.RegistryApiSimulation.REGISTRY_URL;
import static io.apicurio.registry.perftest.simulations.RegistryApiSimulation.SETUP_HTTP_CLIENT;
import static io.apicurio.registry.perftest.simulations.RegistryApiSimulation.SETUP_REQUEST_TIMEOUT;
import static io.apicurio.registry.perftest.simulations.RegistryApiSimulation.envOrDefault;
import static io.apicurio.registry.perftest.simulations.RegistryApiSimulation.jsonEscape;
import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.constantConcurrentUsers;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.rampConcurrentUsers;
import static io.gatling.javaapi.core.CoreDsl.repeat;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Agent registry scale baseline (#11093): discovery latency and MCP import throughput at a stated
 * artifact count. The numbers are evidence for the graduation plan, not service levels.
 * <p>
 * Setup seeds {@code PERF_AGENT_SEED} Agent Cards (default 1000) and {@code PERF_TOOL_SEED} MCP
 * tools (default 1000). Then:
 * <ol>
 *   <li><b>Discovery</b>: {@code PERF_USERS} concurrent users (default 20) for
 *       {@code PERF_DURATION_SECONDS} (default 120) each repeatedly send, in turn: agent discovery
 *       filtered by skill, ARD search by text, MCP tool search by name.</li>
 *   <li><b>Import</b>: afterwards, one user imports {@code PERF_IMPORT_RUNS} times (default 10)
 *       from the MCP server started by {@code PerfTestRunner}, which lists
 *       {@code PERF_IMPORT_TOOLS} tools (default 500), each time into a new server artifact.
 *       Import throughput is the tool count divided by the import response time.</li>
 * </ol>
 * Requires the registry to run with A2A, MCP tools, AI Catalog, ARD and MCP import enabled, and
 * {@code PERF_MCP_URL} (default {@code http://perf-mcp.default.svc:8099/mcp}) allowed for import.
 */
public class AgentDiscoverySimulation extends Simulation {

    private static final Logger log = LoggerFactory.getLogger(AgentDiscoverySimulation.class);

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String ROOT_URL = REGISTRY_URL.replaceFirst("/apis/registry/v3/?$", "");

    private static final int USERS = Integer.parseInt(envOrDefault("PERF_USERS", "20"));
    private static final long DURATION_SECONDS = Long.parseLong(envOrDefault("PERF_DURATION_SECONDS", "120"));
    private static final long RAMP_SECONDS = 10L;
    private static final int AGENT_SEED = Integer.parseInt(envOrDefault("PERF_AGENT_SEED", "1000"));
    private static final int TOOL_SEED = Integer.parseInt(envOrDefault("PERF_TOOL_SEED", "1000"));
    private static final int SKILLS = 50;
    public static final int IMPORT_TOOLS = Integer.parseInt(envOrDefault("PERF_IMPORT_TOOLS", "500"));
    private static final int IMPORT_RUNS = Integer.parseInt(envOrDefault("PERF_IMPORT_RUNS", "10"));
    private static final String MCP_URL = envOrDefault("PERF_MCP_URL", "http://perf-mcp.default.svc:8099/mcp");

    private static final String AGENT_GROUP = "perf-agents";
    private static final String TOOL_GROUP = "perf-tools";
    private static final String IMPORT_GROUP = "perf-import";

    private static final AtomicInteger IMPORT_COUNTER = new AtomicInteger();
    // Server artifacts of each run get new IDs, so a rerun against the same registry imports again
    private static final String RUN_ID = Long.toString(System.currentTimeMillis(), 36);

    // Caching disabled: every request must reach the registry for its latency to be measured
    HttpProtocolBuilder httpProtocol = http.baseUrl(ROOT_URL).acceptHeader("application/json")
            .userAgentHeader("apicurio-registry-perf-tests/gatling").disableCaching();

    {
        RegistryApiSimulation.startTokenRefresh();
        seed("Agent Card", AGENT_SEED, AgentDiscoverySimulation::createAgentCard);
        seed("MCP tool", TOOL_SEED, AgentDiscoverySimulation::createMcpTool);
    }

    ScenarioBuilder discovery = scenario("Agent discovery").during(Duration.ofSeconds(DURATION_SECONDS + RAMP_SECONDS))
            .on(exec(session -> session.set("n", ThreadLocalRandom.current().nextInt(Math.max(1, AGENT_SEED)))
                    .set("skill", "skill-" + ThreadLocalRandom.current().nextInt(SKILLS)))
                    // Each iteration sends all three requests, so they are measured equally often
                    .exec(http("Discover agents by skill")
                            .get("/.well-known/agents?skill=#{skill}&limit=20")
                            .header(AUTHORIZATION_HEADER, RegistryApiSimulation::authHeader)
                            .check(status().is(200)))
                    .exec(http("ARD search by text")
                            .post("/.well-known/ard/search")
                            .header(AUTHORIZATION_HEADER, RegistryApiSimulation::authHeader)
                            .body(StringBody("{\"query\":{\"text\":\"perf-agent-#{n}\"}}")).asJson()
                            .check(status().is(200)))
                    .exec(http("Search MCP tools by name")
                            .get("/.well-known/mcp-tools?name=perf-tool-#{n}&limit=20")
                            .header(AUTHORIZATION_HEADER, RegistryApiSimulation::authHeader)
                            .check(status().is(200))));

    ScenarioBuilder imports = scenario("MCP import").exec(repeat(IMPORT_RUNS).on(importOnce()));

    private static ChainBuilder importOnce() {
        String path = "/apis/registry/v3/groups/" + IMPORT_GROUP + "/artifacts";
        return exec(session -> session.set("server", "perf-server-" + RUN_ID + "-" + IMPORT_COUNTER.incrementAndGet()))
                .exec(http("Create MCP server artifact").post(path)
                        .header(AUTHORIZATION_HEADER, RegistryApiSimulation::authHeader)
                        .body(StringBody("{\"artifactId\":\"#{server}\",\"artifactType\":\"MCP_SERVER\","
                                + "\"firstVersion\":{\"content\":{\"contentType\":\"application/json\",\"content\":"
                                + jsonEscape("{\"name\":\"io.github.perf/#{server}\",\"description\":\"Perf\","
                                        + "\"version\":\"1.0.0\"}")
                                + "}}}")).asJson()
                        .check(status().is(200)))
                .exec(http("MCP import (" + IMPORT_TOOLS + " tools)").post(path + "/#{server}/mcp-import")
                        .header(AUTHORIZATION_HEADER, RegistryApiSimulation::authHeader)
                        .body(StringBody("{\"url\":\"" + MCP_URL + "\"}")).asJson()
                        .check(status().is(200)));
    }

    {
        setUp(discovery.injectClosed(rampConcurrentUsers(1).to(USERS).during(Duration.ofSeconds(RAMP_SECONDS)),
                constantConcurrentUsers(USERS).during(Duration.ofSeconds(DURATION_SECONDS)))
                .andThen(imports.injectOpen(atOnceUsers(1))))
                .protocols(httpProtocol)
                .maxDuration(Duration.ofSeconds(RAMP_SECONDS + DURATION_SECONDS + 900))
                .assertions(global().failedRequests().percent().lte(1.0));
    }

    private interface Seeder {
        void create(int index) throws Exception;
    }

    private static void seed(String what, int count, Seeder seeder) {
        log.info("Seeding {} {} artifacts...", count, what);
        ExecutorService executor = Executors.newFixedThreadPool(20);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int index = i;
            futures.add(executor.submit(() -> {
                // Creation is idempotent (FIND_OR_CREATE_VERSION), so a transient failure is retried
                for (int attempt = 1;; attempt++) {
                    try {
                        seeder.create(index);
                        return null;
                    } catch (Exception e) {
                        if (attempt == 3) {
                            log.warn("Seeding {} {} failed: {}", what, index, e.toString());
                            throw e;
                        }
                    }
                }
            }));
        }
        int failed = 0;
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                failed++;
            }
        }
        executor.shutdown();
        if (failed > 0) {
            throw new IllegalStateException(failed + " of " + count + " " + what + " artifacts could not be seeded.");
        }
        log.info("Seeded {} {} artifacts.", count, what);
    }

    private static void createAgentCard(int index) throws Exception {
        int skill = index % SKILLS;
        String card = "{\"name\":\"perf-agent-" + index + "\",\"description\":\"Performance test agent " + index
                + "\",\"version\":\"1.0.0\",\"supportedInterfaces\":[{\"url\":\"https://agents.example.com/" + index
                + "\",\"protocolBinding\":\"JSONRPC\",\"protocolVersion\":\"1.0\"}],\"capabilities\":{},"
                + "\"skills\":[{\"id\":\"skill-" + skill + "\",\"name\":\"Skill " + skill
                + "\",\"description\":\"Skill " + skill + "\",\"tags\":[\"perf\"]}],"
                + "\"defaultInputModes\":[\"text\"],\"defaultOutputModes\":[\"text\"]}";
        create(AGENT_GROUP, "perf-agent-" + index, "AGENT_CARD", card);
    }

    private static void createMcpTool(int index) throws Exception {
        String tool = "{\"name\":\"perf_tool_" + index + "\",\"description\":\"Performance test tool " + index
                + "\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}}";
        create(TOOL_GROUP, "perf-tool-" + index, "MCP_TOOL", tool);
    }

    private static void create(String group, String artifactId, String type, String content) throws Exception {
        String body = "{\"artifactId\":\"" + artifactId + "\",\"artifactType\":\"" + type + "\","
                + "\"firstVersion\":{\"content\":{\"content\":" + jsonEscape(content)
                + ",\"contentType\":\"application/json\"}}}";
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(REGISTRY_URL + "/groups/" + group + "/artifacts?ifExists=FIND_OR_CREATE_VERSION"))
                .header("Content-Type", "application/json").timeout(SETUP_REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        String token = CACHED_TOKEN.get();
        if (token != null) {
            request.header(AUTHORIZATION_HEADER, "Bearer " + token);
        }
        HttpResponse<String> response = SETUP_HTTP_CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.warn("Seeding {}/{} failed: HTTP {} {}", group, artifactId, response.statusCode(), response.body());
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
    }
}
