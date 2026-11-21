package io.apicurio.registry.agents.mcpimport;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rbac.MockAuditLogService;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.ValidatableResponse;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Imports against an in-process MCP server. The methods share that server, and assert on deltas
 * of app-wide metrics, so they run one at a time and hold {@link #LOCK} against other MCP import
 * test classes.
 */
@QuarkusTest
@TestProfile(McpImportTestProfile.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(McpImportTest.LOCK)
public class McpImportTest extends AbstractResourceTestBase {

    static final String LOCK = "mcp-import";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private static final String SERVER = "srv";

    McpTestServer mcp;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    McpImportConfig importConfig;

    @Inject
    MockAuditLogService auditLogService;

    @Inject
    McpToolImportService importService;

    @Inject
    McpImportGateHook gateHook;

    String group;

    @BeforeAll
    void startMcp() throws Exception {
        mcp = new McpTestServer();
    }

    @AfterAll
    void stopMcp() {
        if (mcp != null) {
            mcp.close();
        }
    }

    @BeforeEach
    void freshServer() throws Exception {
        mcp.redirectTo = null;
        mcp.delayMillis = 0;
        mcp.paddingBytes = 0;
        mcp.release = null;
        mcp.receivedHeaders.clear();
        group = TestUtils.generateGroupId();
        createArtifact(group, SERVER, ArtifactType.MCP_SERVER,
                "{\"name\":\"io.github.test/srv\",\"description\":\"A server\",\"version\":\"1.0.0\"}",
                ContentTypes.APPLICATION_JSON);
    }

    /**
     * A background import releases its permit just after it records its outcome, so the next test
     * waits for it rather than being refused as busy.
     */
    @AfterEach
    void awaitBackgroundImports() {
        gateHook.reset();
        await().atMost(Duration.ofSeconds(30))
                .until(() -> importService.availablePermits() == importConfig.getMaxConcurrent());
    }

    /** Moves the test to a new group whose tool writes {@link McpImportGateHook} can hold. */
    private void useGatedGroup() throws Exception {
        group = McpImportGateHook.GROUP_PREFIX + TestUtils.generateGroupId();
        createArtifact(group, SERVER, ArtifactType.MCP_SERVER,
                "{\"name\":\"io.github.test/srv\",\"description\":\"A server\",\"version\":\"1.0.0\"}",
                ContentTypes.APPLICATION_JSON);
    }

    private String path(String artifactId) {
        return registryV3ApiUrl + "/groups/" + group + "/artifacts/" + artifactId;
    }

    private ValidatableResponse importTools(Map<String, Object> request) {
        return importInto(SERVER, request);
    }

    private ValidatableResponse importInto(String serverArtifactId, Map<String, Object> request) {
        Map<String, Object> body = new HashMap<>(request);
        body.putIfAbsent("url", mcp.url());
        return given().contentType(ContentTypes.APPLICATION_JSON).body(body)
                .post(path(serverArtifactId) + "/mcp-import").then();
    }

    /**
     * MCP_SERVER artifacts besides {@link #SERVER}, one per import to hold: imports into one artifact
     * cannot overlap.
     */
    private List<String> otherServers(int count) throws Exception {
        List<String> servers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            servers.add("held" + i);
            createArtifact(group, "held" + i, ArtifactType.MCP_SERVER,
                    "{\"name\":\"io.github.test/held\",\"description\":\"A server\",\"version\":\"1.0.0\"}",
                    ContentTypes.APPLICATION_JSON);
        }
        return servers;
    }

    private ValidatableResponse importTools() {
        return importTools(Map.of());
    }

    private JsonNode content(String artifactId, String version) throws Exception {
        return MAPPER.readTree(given().get(path(artifactId) + "/versions/" + version + "/content").then()
                .statusCode(200).extract().asString());
    }

    /** Each reference of a server version, as "name=artifactId@version", sorted. */
    private List<String> references(String version) {
        List<Map<String, String>> refs = given().get(path(SERVER) + "/versions/" + version + "/references").then()
                .statusCode(200).extract().jsonPath().getList("");
        return refs.stream().map(r -> r.get("name") + "=" + r.get("artifactId") + "@" + r.get("version"))
                .sorted().toList();
    }

    private int versionCount(String artifactId) {
        return given().get(path(artifactId) + "/versions").then().statusCode(200).extract().path("count");
    }

    private double count(String metric, String tag, String value) {
        Counter counter = meterRegistry.find(metric).tag(tag, value).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    void uiConfigOffersTheImport() {
        given().get(registryV3ApiUrl + "/system/uiConfig").then().statusCode(200)
                .body("features.mcpImport", equalTo(true));
    }

    @Test
    void importCreatesToolsAndLinksThemFromANewServerVersion() throws Exception {
        mcp.tools(McpTestServer.tool("search", "string"), McpTestServer.tool("fetch", "string"));
        double created = count(McpToolImportService.METRIC_TOOLS, "result", "created");

        importTools().statusCode(200)
                .body("status", equalTo("NEW_VERSION")).body("version", equalTo("2"))
                .body("tools", hasSize(2))
                .body("tools[0].name", equalTo("search")).body("tools[0].status", equalTo("CREATED"))
                .body("tools[0].groupId", equalTo(group)).body("tools[0].artifactId", equalTo("srv.search"))
                .body("tools[0].version", equalTo("1"))
                .body("tools[1].artifactId", equalTo("srv.fetch")).body("tools[1].status", equalTo("CREATED"));

        assertEquals(MAPPER.readTree(McpTestServer.tool("search", "string").encode()), content("srv.search", "1"));
        assertEquals(List.of("fetch=srv.fetch@1", "search=srv.search@1"), references("2"));
        assertEquals(2, count(McpToolImportService.METRIC_TOOLS, "result", "created") - created);
    }

    @Test
    void reimportOfTheSameToolsChangesNothing() {
        mcp.tools(McpTestServer.tool("search", "string"));
        importTools().statusCode(200).body("version", equalTo("2"));

        importTools().statusCode(200)
                .body("status", equalTo("UNCHANGED")).body("version", equalTo("2"))
                .body("tools[0].status", equalTo("UNCHANGED")).body("tools[0].version", equalTo("1"));

        assertEquals(2, versionCount(SERVER));
        assertEquals(1, versionCount("srv.search"));
    }

    @Test
    void breakingChangeIsRejectedAndTheAcceptedVersionKept() throws Exception {
        mcp.tools(McpTestServer.tool("search", "string"), McpTestServer.tool("fetch", "string"));
        importTools().statusCode(200);
        given().contentType(ContentTypes.APPLICATION_JSON)
                .body(Map.of("ruleType", "COMPATIBILITY", "config", "BACKWARD"))
                .post(registryV3ApiUrl + "/groups/" + group + "/rules").then().statusCode(204);

        mcp.tools(McpTestServer.tool("search", "integer"), McpTestServer.tool("fetch", "string"));
        importTools().statusCode(200)
                .body("tools[0].status", equalTo("REJECTED")).body("tools[0].version", nullValue())
                .body("tools[0].causes[0].context", equalTo("/inputSchema/properties/query/type"))
                .body("tools[1].status", equalTo("UNCHANGED"))
                .body("status", equalTo("NEW_VERSION")).body("version", equalTo("3"));

        assertEquals(1, versionCount("srv.search"));
        assertEquals(MAPPER.readTree(McpTestServer.tool("search", "string").encode()), content("srv.search", "1"));
        // The server version describes what the server offers that is stored: the rejected tool is not
        // referenced, because no stored version matches it.
        assertEquals(List.of("fetch=srv.fetch@1"), references("3"));
    }

    @Test
    void compatibleChangeAddsAToolVersion() throws Exception {
        mcp.tools(McpTestServer.tool("search", "string"));
        importTools().statusCode(200);

        JsonObject described = McpTestServer.tool("search", "string").put("description", "Searches better");
        mcp.tools(described);
        importTools().statusCode(200)
                .body("tools[0].status", equalTo("NEW_VERSION")).body("tools[0].version", equalTo("2"))
                .body("status", equalTo("NEW_VERSION")).body("version", equalTo("3"));

        assertEquals("Searches better", content("srv.search", "2").get("description").asText());
        assertEquals(List.of("search=srv.search@2"), references("3"));
        assertEquals(List.of("search=srv.search@1"), references("2"));
    }

    /**
     * Matching must compare the content exactly: these two bounds round to the same double, so a
     * lossy comparison would report the changed tool as unchanged and skip its rules.
     */
    @Test
    void decimalChangeBeyondDoublePrecisionIsANewVersion() throws Exception {
        JsonObject first = McpTestServer.tool("search", "number");
        first.getJsonObject("inputSchema").getJsonObject("properties").getJsonObject("query")
                .put("maximum", new BigDecimal("1.00000000000000001"));
        mcp.tools(first);
        importTools().statusCode(200);

        JsonObject second = McpTestServer.tool("search", "number");
        second.getJsonObject("inputSchema").getJsonObject("properties").getJsonObject("query")
                .put("maximum", new BigDecimal("1.00000000000000002"));
        mcp.tools(second);
        importTools().statusCode(200)
                .body("tools[0].status", equalTo("NEW_VERSION")).body("tools[0].version", equalTo("2"));

        assertEquals("1.00000000000000002", content("srv.search", "2").at("/inputSchema/properties/query/maximum")
                .decimalValue().toPlainString());
    }

    /**
     * A schema the compatibility rule cannot compare (here a pattern) rejects that tool only: the
     * tools after it are still imported.
     */
    @Test
    void schemaTheRuleCannotCompareRejectsOnlyThatTool() throws Exception {
        JsonObject patterned = McpTestServer.tool("patterned", "string");
        patterned.getJsonObject("inputSchema").getJsonObject("properties").getJsonObject("query")
                .put("pattern", "^a.*");
        mcp.tools(patterned, McpTestServer.tool("plain", "string"));
        importTools().statusCode(200);
        given().contentType(ContentTypes.APPLICATION_JSON)
                .body(Map.of("ruleType", "COMPATIBILITY", "config", "BACKWARD"))
                .post(registryV3ApiUrl + "/groups/" + group + "/rules").then().statusCode(204);

        mcp.tools(patterned.copy().put("description", "Changed"),
                McpTestServer.tool("plain", "string").put("description", "Changed"));
        importTools().statusCode(200)
                .body("tools[0].status", equalTo("REJECTED")).body("tools[0].version", nullValue())
                .body("tools[0].causes[0].description", containsString("pattern"))
                .body("tools[1].status", equalTo("NEW_VERSION")).body("tools[1].version", equalTo("2"));

        assertEquals(1, versionCount("srv.patterned"));
        assertEquals(List.of("plain=srv.plain@2"), references("3"));
    }

    @Test
    void dryRunOfANewImportWritesNothing() {
        mcp.tools(McpTestServer.tool("search", "string"));
        double created = count(McpToolImportService.METRIC_TOOLS, "result", "created");

        importTools(Map.of("dryRun", true)).statusCode(200)
                .body("status", equalTo("NEW_VERSION")).body("version", nullValue())
                .body("tools[0].status", equalTo("CREATED")).body("tools[0].version", nullValue());

        given().get(path("srv.search")).then().statusCode(404);
        assertEquals(1, versionCount(SERVER));
        assertEquals(created, count(McpToolImportService.METRIC_TOOLS, "result", "created"));
    }

    @Test
    void dryRunOverExistingToolsReportsEveryOutcomeWithoutWriting() throws Exception {
        mcp.tools(McpTestServer.tool("same", "string"), McpTestServer.tool("changed", "string"),
                McpTestServer.tool("broken", "string"));
        importTools().statusCode(200).body("version", equalTo("2"));
        given().contentType(ContentTypes.APPLICATION_JSON)
                .body(Map.of("ruleType", "COMPATIBILITY", "config", "BACKWARD"))
                .post(registryV3ApiUrl + "/groups/" + group + "/rules").then().statusCode(204);

        mcp.tools(McpTestServer.tool("same", "string"),
                McpTestServer.tool("changed", "string").put("description", "Changed"),
                McpTestServer.tool("broken", "integer"));
        List<String> results = List.of("unchanged", "new-version", "rejected");
        List<Double> before = results.stream().map(r -> count(McpToolImportService.METRIC_TOOLS, "result", r))
                .toList();
        importTools(Map.of("dryRun", true)).statusCode(200)
                .body("tools[0].status", equalTo("UNCHANGED")).body("tools[0].version", equalTo("1"))
                .body("tools[1].status", equalTo("NEW_VERSION")).body("tools[1].version", nullValue())
                .body("tools[2].status", equalTo("REJECTED")).body("tools[2].version", nullValue())
                .body("tools[2].causes[0].context", equalTo("/inputSchema/properties/query/type"))
                .body("status", equalTo("NEW_VERSION")).body("version", nullValue());

        assertEquals(before, results.stream().map(r -> count(McpToolImportService.METRIC_TOOLS, "result", r))
                .toList());

        assertEquals(1, versionCount("srv.changed"));
        assertEquals(1, versionCount("srv.broken"));
        assertEquals(2, versionCount(SERVER));
        assertEquals("The changed tool", content("srv.changed", "1").get("description").asText());
    }

    /**
     * The edge case agreed for this change: a tool the server stops listing is reported, and is not
     * deleted or changed. The new server version no longer references it.
     */
    @Test
    void toolRemovedBetweenImportsIsReportedAndKept() throws Exception {
        mcp.tools(McpTestServer.tool("search", "string"), McpTestServer.tool("fetch", "string"));
        importTools().statusCode(200).body("version", equalTo("2"));
        double removed = count(McpToolImportService.METRIC_TOOLS, "result", "removed");

        mcp.tools(McpTestServer.tool("search", "string"));
        importTools().statusCode(200)
                .body("tools", hasSize(2))
                .body("tools[0].status", equalTo("UNCHANGED"))
                .body("tools[1].name", equalTo("fetch")).body("tools[1].status", equalTo("REMOVED"))
                .body("tools[1].artifactId", equalTo("srv.fetch")).body("tools[1].version", nullValue())
                .body("status", equalTo("NEW_VERSION")).body("version", equalTo("3"));

        assertEquals(List.of("search=srv.search@1"), references("3"));
        assertEquals(List.of("fetch=srv.fetch@1", "search=srv.search@1"), references("2"));
        assertEquals(1, versionCount("srv.fetch"));
        assertEquals(MAPPER.readTree(McpTestServer.tool("fetch", "string").encode()), content("srv.fetch", "1"));
        assertEquals(1, count(McpToolImportService.METRIC_TOOLS, "result", "removed") - removed);
    }

    @Test
    void duplicateToolNamesWriteNothing() {
        mcp.tools(McpTestServer.tool("first", "string"), McpTestServer.tool("dup", "string"),
                McpTestServer.tool("dup", "integer"));
        double protocol = count(McpToolImportService.METRIC_FAILURES, "reason", "protocol");

        importTools().statusCode(502)
                .body("title", equalTo("The MCP server lists two tools with the same name."));

        given().get(path("srv.first")).then().statusCode(404);
        given().get(path("srv.dup")).then().statusCode(404);
        assertEquals(1, versionCount(SERVER));
        assertEquals(1, count(McpToolImportService.METRIC_FAILURES, "reason", "protocol") - protocol);
    }

    @Test
    void existingArtifactOfAnotherTypeIsNotOverwritten() throws Exception {
        createArtifact(group, "srv.search", ArtifactType.JSON, "{\"type\":\"string\"}", ContentTypes.APPLICATION_JSON);
        mcp.tools(McpTestServer.tool("search", "string"));

        importTools().statusCode(200)
                .body("tools[0].status", equalTo("REJECTED"))
                .body("tools[0].causes[0].context", equalTo("/artifactType"));

        assertEquals(1, versionCount("srv.search"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"caf\u00e9", "a%2Fb", "../../etc", "two words", ""})
    void toolWithAnInvalidNameIsRejectedAndTheOthersImported(String name) {
        mcp.tools(McpTestServer.tool("search", "string"), McpTestServer.tool(name, "string"));

        importTools().statusCode(200)
                .body("status", equalTo("NEW_VERSION"))
                .body("tools[0].status", equalTo("CREATED"))
                .body("tools[1].status", equalTo("REJECTED"))
                .body("tools[1].causes[0].context", equalTo("/name"));

        assertEquals(List.of("search=srv.search@1"), references("2"));
        given().get(registryV3ApiUrl + "/search/artifacts?groupId=" + group).then().statusCode(200)
                .body("count", equalTo(2));
    }

    @Test
    void toolNameOverTheMaximumLengthIsRejected() {
        String longest = "t".repeat(McpToolImportService.TOOL_NAME_MAX_LENGTH);
        mcp.tools(McpTestServer.tool(longest, "string"), McpTestServer.tool(longest + "x", "string"));

        importTools().statusCode(200)
                .body("tools[0].status", equalTo("CREATED"))
                .body("tools[1].status", equalTo("REJECTED"));
    }

    @Test
    void toolsCanGoToAnotherGroup() {
        String toolGroup = TestUtils.generateGroupId();
        mcp.tools(McpTestServer.tool("search", "string"));

        importTools(Map.of("toolGroupId", toolGroup)).statusCode(200).body("tools[0].groupId", equalTo(toolGroup));

        given().get(registryV3ApiUrl + "/groups/" + toolGroup + "/artifacts/srv.search").then().statusCode(200);
        given().get(path(SERVER) + "/versions/2/references").then().statusCode(200)
                .body("[0].groupId", equalTo(toolGroup)).body("[0].artifactId", equalTo("srv.search"));
    }

    @Test
    void onlyAnMcpServerArtifactCanImport() throws Exception {
        int before = mcp.requests.get();
        createArtifact(group, "not-a-server", ArtifactType.JSON, "{\"type\":\"string\"}",
                ContentTypes.APPLICATION_JSON);
        given().contentType(ContentTypes.APPLICATION_JSON).body(Map.of("url", mcp.url()))
                .post(path("not-a-server") + "/mcp-import").then().statusCode(400);
        given().contentType(ContentTypes.APPLICATION_JSON).body(Map.of("url", mcp.url()))
                .post(path("missing") + "/mcp-import").then().statusCode(404);
        assertEquals(before, mcp.requests.get());
    }

    @Test
    void refusedHostsAreNeverContacted() {
        int before = mcp.requests.get();
        double denied = count(McpToolImportService.METRIC_FAILURES, "reason", "host-denied");
        // Literal addresses only, so the outcome does not depend on how the machine resolves names.
        for (String url : List.of("http://192.0.2.1/mcp", "http://169.254.169.254/latest/meta-data/",
                "http://127.0.0.2:8080/mcp", "http://[::1]:8080/mcp")) {
            importTools(Map.of("url", url)).statusCode(400)
                    .body("title", equalTo("The MCP server URL is not allowed by this registry."));
        }
        assertEquals(before, mcp.requests.get());
        assertEquals(4, count(McpToolImportService.METRIC_FAILURES, "reason", "host-denied") - denied);
    }

    @Test
    void headersReachTheServerOnlyAndAreNotReturnedOrAudited() {
        mcp.tools(McpTestServer.tool("search", "string"));
        int auditBefore = auditLogService.getAuditLogs().size();

        String body = importTools(Map.of("headers", Map.of("Authorization", "Bearer s3cr3t-token")))
                .statusCode(200).extract().asString();

        assertFalse(mcp.receivedHeaders.isEmpty());
        assertTrue(mcp.receivedHeaders.stream().allMatch(h -> "Bearer s3cr3t-token".equals(h.get("authorization"))));
        assertFalse(body.contains("s3cr3t-token"));
        List<Map<String, String>> audit = auditLogService.getAuditLogs().subList(auditBefore,
                auditLogService.getAuditLogs().size());
        assertTrue(audit.stream().anyMatch(a -> "importMcpTools".equals(a.get("action"))
                && group.equals(a.get("group_id"))), () -> "audit: " + audit);
        assertTrue(audit.stream().noneMatch(a -> a.toString().contains("s3cr3t-token")));
    }

    @Test
    void importOverTheConcurrencyLimitIsRefused() throws Exception {
        // Fill every permit with an import held at the MCP server until released. The limit is read
        // from the configuration: the profiles of the subclasses set it differently.
        int limit = importConfig.getMaxConcurrent();
        mcp.tools(McpTestServer.tool("search", "string"));
        CompletableFuture<Void> release = new CompletableFuture<>();
        mcp.release = release;
        int before = mcp.requests.get();
        double busy = count(McpToolImportService.METRIC_FAILURES, "reason", "busy");
        List<String> servers = otherServers(limit);
        ExecutorService executor = Executors.newFixedThreadPool(limit);
        List<CompletableFuture<Integer>> held = new ArrayList<>();
        try {
            for (String server : servers) {
                held.add(CompletableFuture.supplyAsync(() -> importInto(server, Map.of()).extract().statusCode(),
                        executor));
            }
            // Each import takes its permit before it contacts the MCP server.
            await().atMost(Duration.ofSeconds(10)).until(() -> mcp.requests.get() >= before + limit);
            int inFlight = mcp.requests.get();

            importTools().statusCode(429)
                    .body("title", equalTo("Too many MCP imports are running. Try again later."));

            assertEquals(inFlight, mcp.requests.get(), "the refused import must not contact the MCP server");
            assertEquals(1, count(McpToolImportService.METRIC_FAILURES, "reason", "busy") - busy);
        } finally {
            mcp.release = null;
            release.complete(null);
            executor.shutdown();
        }
        for (CompletableFuture<Integer> importing : held) {
            assertEquals(200, importing.get(30, TimeUnit.SECONDS));
        }
        importTools().statusCode(200);
    }

    @Test
    void theServerContactedIsAuditedWithoutPathQueryOrHeaders() {
        mcp.tools(McpTestServer.tool("search", "string"));
        int auditBefore = auditLogService.getAuditLogs().size();
        String target = mcp.url().replace("http://", "").replace("/mcp", "");

        importTools(Map.of("url", mcp.url() + "?key=s3cr3t-query",
                "headers", Map.of("Authorization", "Bearer s3cr3t-token"))).statusCode(200);
        importTools(Map.of("url", "https://not-allowed.example.com/mcp")).statusCode(400);

        List<Map<String, String>> audit = auditLogService.getAuditLogs().subList(auditBefore,
                auditLogService.getAuditLogs().size()).stream()
                .filter(a -> McpToolImportService.AUDIT_ACTION.equals(a.get("action"))).toList();
        assertEquals(2, audit.size(), () -> "audit: " + audit);
        assertEquals(Map.of("action", "mcpImportTarget", "result", "success", "group_id", group,
                "artifact_id", SERVER, "target", target), withoutPrincipal(audit.get(0)));
        assertEquals(Map.of("action", "mcpImportTarget", "result", "failure", "group_id", group,
                "artifact_id", SERVER, "target", "not-allowed.example.com", "reason", "host-denied"),
                withoutPrincipal(audit.get(1)));
    }

    private static Map<String, String> withoutPrincipal(Map<String, String> entry) {
        Map<String, String> copy = new HashMap<>(entry);
        copy.keySet().removeIf(k -> !Set.of("action", "result", "group_id", "artifact_id", "target", "reason")
                .contains(k));
        return copy;
    }

    @Test
    void reservedOrInvalidHeadersAreRefusedBeforeConnecting() {
        int before = mcp.requests.get();
        importTools(Map.of("headers", Map.of("Host", "elsewhere"))).statusCode(400)
                .body("title", containsString("'Host'"));
        importTools(Map.of("headers", Map.of("X-Test", "a\r\nInjected: b"))).statusCode(400)
                .body("title", not(containsString("Injected")));
        importTools(Map.of("headers", Map.of("X-Count", 5))).statusCode(400);
        // Printable, but not valid in an HTTP field name.
        importTools(Map.of("headers", Map.of("X(Test)", "a"))).statusCode(400);
        importTools(Map.of("headers", Map.of("X=Test", "a"))).statusCode(400);
        assertEquals(before, mcp.requests.get());
    }

    @Test
    void slowServerIsABadGatewayWithoutDetails() {
        mcp.tools(McpTestServer.tool("search", "string"));
        mcp.delayMillis = 4000;
        double timeouts = count(McpToolImportService.METRIC_FAILURES, "reason", "timeout");
        int auditBefore = auditLogService.getAuditLogs().size();

        String body = importTools(Map.of("headers", Map.of("Authorization", "Bearer s3cr3t-token")))
                .statusCode(502)
                .body("title", equalTo("The MCP server did not respond in time."))
                // The error mapper reports the root cause; the exception has none, so nothing from
                // the connection (address, server text) can appear here.
                .body("detail", equalTo("McpImportException: The MCP server did not respond in time."))
                .extract().asString();

        assertFalse(body.contains("s3cr3t-token"));
        assertFalse(body.contains("127.0.0.1"));
        List<Map<String, String>> audit = auditLogService.getAuditLogs().subList(auditBefore,
                auditLogService.getAuditLogs().size());
        assertTrue(audit.stream().anyMatch(a -> "importMcpTools".equals(a.get("action"))
                && group.equals(a.get("group_id")) && "failure".equals(a.get("result"))), () -> "audit: " + audit);
        assertTrue(audit.stream().noneMatch(a -> a.toString().contains("s3cr3t-token")));
        assertEquals(1, count(McpToolImportService.METRIC_FAILURES, "reason", "timeout") - timeouts);
        assertEquals(1, versionCount(SERVER));
    }

    @Test
    void redirectIsABadGatewayAndIsNotFollowed() throws Exception {
        try (McpTestServer target = new McpTestServer()) {
            target.tools(McpTestServer.tool("hidden", "string"));
            mcp.redirectTo = target.url();
            double protocol = count(McpToolImportService.METRIC_FAILURES, "reason", "protocol");

            importTools().statusCode(502);

            assertEquals(0, target.requests.get());
            assertEquals(1, count(McpToolImportService.METRIC_FAILURES, "reason", "protocol") - protocol);
            given().get(path("srv.hidden")).then().statusCode(404);
        }
    }

    @Test
    void oversizeResponseIsABadGateway() {
        // Larger than the default apicurio.mcp-import.response.max-size of 1 MiB.
        mcp.tools(McpTestServer.tool("search", "string"));
        mcp.paddingBytes = 2 * 1024 * 1024;
        double oversize = count(McpToolImportService.METRIC_FAILURES, "reason", "oversize");

        importTools().statusCode(502)
                .body("title", equalTo("A message from the MCP server is larger than this registry accepts."));

        assertEquals(1, count(McpToolImportService.METRIC_FAILURES, "reason", "oversize") - oversize);
        given().get(path("srv.search")).then().statusCode(404);
    }

    @Test
    void tooManyToolsIsABadGateway() {
        // One more than the default apicurio.mcp-import.tools.max-size of 500.
        mcp.tools(IntStream.range(0, 501).mapToObj(i -> McpTestServer.tool("t" + i, "string"))
                .toArray(JsonObject[]::new));
        double tooMany = count(McpToolImportService.METRIC_FAILURES, "reason", "too-many-tools");

        importTools().statusCode(502).body("title", containsString("(500)"));

        assertEquals(1, count(McpToolImportService.METRIC_FAILURES, "reason", "too-many-tools") - tooMany);
        given().get(path("srv.t0")).then().statusCode(404);
    }

    private ValidatableResponse importStatus() {
        return given().get(path(SERVER) + "/mcp-import/status").then();
    }

    /** Waits until the last import is no longer running, and returns its status. */
    private ValidatableResponse awaitFinished() {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> importStatus().statusCode(200).body("state", not(equalTo("RUNNING"))));
        return importStatus().statusCode(200);
    }

    private Map<String, String> labels() {
        return given().get(path(SERVER)).then().statusCode(200).extract().jsonPath().getMap("labels");
    }

    private void setLabels(Map<String, String> labels) {
        given().contentType(ContentTypes.APPLICATION_JSON).body(Map.of("labels", labels)).put(path(SERVER))
                .then().statusCode(204);
    }

    @Test
    void asyncImportRunsInTheBackgroundAndReportsItsOutcome() throws Exception {
        mcp.tools(McpTestServer.tool("search", "string"), McpTestServer.tool("fetch", "string"));
        double created = count(McpToolImportService.METRIC_TOOLS, "result", "created");
        int auditBefore = auditLogService.getAuditLogs().size();

        String location = importTools(Map.of("async", true)).statusCode(202)
                .header("Location", endsWith("/apis/registry/v3/groups/" + group + "/artifacts/" + SERVER
                        + "/mcp-import/status"))
                .body("state", equalTo("RUNNING")).body("startedOn", not(nullValue()))
                .body("finishedOn", nullValue())
                .extract().header("Location");

        awaitFinished().body("state", equalTo("COMPLETED")).body("version", equalTo("2"))
                .body("counts", equalTo(Map.of("CREATED", 2))).body("reason", nullValue())
                .body("finishedOn", not(nullValue()));
        given().get(location).then().statusCode(200).body("state", equalTo("COMPLETED"));
        assertEquals(MAPPER.readTree(McpTestServer.tool("search", "string").encode()), content("srv.search", "1"));
        assertEquals(List.of("fetch=srv.fetch@1", "search=srv.search@1"), references("2"));
        assertEquals(2, count(McpToolImportService.METRIC_TOOLS, "result", "created") - created);
        List<Map<String, String>> audit = auditLogService.getAuditLogs().subList(auditBefore,
                auditLogService.getAuditLogs().size()).stream()
                .filter(a -> McpToolImportService.AUDIT_ACTION.equals(a.get("action"))).toList();
        assertEquals(1, audit.size(), () -> "audit: " + audit);
        assertEquals("success", audit.get(0).get("result"));
        assertEquals(group, audit.get(0).get("group_id"));
    }

    @Test
    void syncImportRecordsItsStatusAndADryRunDoesNot() {
        mcp.tools(McpTestServer.tool("search", "string"));
        importTools(Map.of("dryRun", true)).statusCode(200);
        importStatus().statusCode(404).body("title", equalTo("No MCP import was recorded for this artifact."));

        importTools().statusCode(200).body("version", equalTo("2"));
        importStatus().statusCode(200).body("state", equalTo("COMPLETED")).body("version", equalTo("2"))
                .body("counts", equalTo(Map.of("CREATED", 1)));

        mcp.tools(McpTestServer.tool("fetch", "string"));
        importTools().statusCode(200).body("version", equalTo("3"));
        importStatus().statusCode(200).body("version", equalTo("3"))
                .body("counts", equalTo(Map.of("CREATED", 1, "REMOVED", 1)));
    }

    @Test
    void statusIsNotFoundBeforeAnyImport() {
        importStatus().statusCode(404).body("title", equalTo("No MCP import was recorded for this artifact."));
        given().get(path("missing") + "/mcp-import/status").then().statusCode(404);
    }

    @Test
    void asyncImportRefusedBeforeStartingRecordsNothing() throws Exception {
        int before = mcp.requests.get();
        double denied = count(McpToolImportService.METRIC_FAILURES, "reason", "host-denied");

        importTools(Map.of("async", true, "url", "http://192.0.2.1/mcp")).statusCode(400)
                .body("title", equalTo("The MCP server URL is not allowed by this registry."));
        importTools(Map.of("async", true, "dryRun", true)).statusCode(400)
                .body("title", equalTo("An asynchronous import cannot be a dry run."));
        importTools(Map.of("async", true, "headers", Map.of("Host", "a"))).statusCode(400);
        createArtifact(group, "not-a-server", ArtifactType.JSON, "{\"type\":\"string\"}",
                ContentTypes.APPLICATION_JSON);
        given().contentType(ContentTypes.APPLICATION_JSON).body(Map.of("url", mcp.url(), "async", true))
                .post(path("not-a-server") + "/mcp-import").then().statusCode(400);
        given().contentType(ContentTypes.APPLICATION_JSON).body(Map.of("url", mcp.url(), "async", true))
                .post(path("missing") + "/mcp-import").then().statusCode(404);

        assertEquals(before, mcp.requests.get());
        assertEquals(1, count(McpToolImportService.METRIC_FAILURES, "reason", "host-denied") - denied);
        importStatus().statusCode(404);
        given().get(path("not-a-server") + "/mcp-import/status").then().statusCode(404);
        assertEquals(1, versionCount(SERVER));
    }

    @Test
    void asyncImportThatFailsIsReportedWithAFixedReason() {
        mcp.tools(McpTestServer.tool("search", "string"));
        mcp.delayMillis = 4000;
        double timeouts = count(McpToolImportService.METRIC_FAILURES, "reason", "timeout");

        importTools(Map.of("async", true, "headers", Map.of("Authorization", "Bearer s3cr3t-token")))
                .statusCode(202);

        String body = awaitFinished().body("state", equalTo("FAILED"))
                .body("reason", equalTo("The MCP server did not respond in time."))
                .body("version", nullValue()).body("counts", nullValue()).body("finishedOn", not(nullValue()))
                .extract().asString();
        assertFalse(body.contains("127.0.0.1"));
        assertFalse(body.contains("s3cr3t-token"));
        assertFalse(body.contains("Exception"));
        assertEquals("timeout", labels().get(McpImportLabels.REASON));
        assertEquals(1, count(McpToolImportService.METRIC_FAILURES, "reason", "timeout") - timeouts);
        assertEquals(1, versionCount(SERVER));
    }

    @Test
    void runningImportThatStoppedReportingIsReportedAsFailed() {
        setLabels(Map.of(McpImportLabels.STATE, "RUNNING", McpImportLabels.STARTED_ON, "2020-01-01T00:00:00Z",
                McpImportLabels.UPDATED_ON, "2020-01-01T00:00:00Z"));
        importStatus().statusCode(200).body("state", equalTo("FAILED"))
                .body("reason", equalTo("The import stopped reporting progress before it finished."))
                .body("startedOn", containsString("2020-01-01"));

        String now = Instant.now().toString();
        setLabels(Map.of(McpImportLabels.STATE, "RUNNING", McpImportLabels.STARTED_ON, now,
                McpImportLabels.UPDATED_ON, now));
        importStatus().statusCode(200).body("state", equalTo("RUNNING")).body("reason", nullValue());
    }

    @Test
    void editedStatusLabelsNeverReportTheirText() {
        setLabels(Map.of(McpImportLabels.STATE, "FAILED", McpImportLabels.REASON, "internal detail"));
        importStatus().statusCode(200).body("state", equalTo("FAILED"))
                .body("reason", equalTo("The import failed.")).body("startedOn", nullValue());

        setLabels(Map.of(McpImportLabels.STATE, "unknown"));
        importStatus().statusCode(404);
    }

    @Test
    void userLabelsAreKeptByImportsAndStatusLabelsByLabelEdits() {
        setLabels(Map.of("team", "search", "stage", "prod"));
        mcp.tools(McpTestServer.tool("search", "string"));

        importTools(Map.of("async", true)).statusCode(202);
        awaitFinished().body("state", equalTo("COMPLETED"));

        Map<String, String> labels = labels();
        assertEquals("search", labels.get("team"));
        assertEquals("prod", labels.get("stage"));
        assertEquals("COMPLETED", labels.get(McpImportLabels.STATE));
        assertEquals("2", labels.get(McpImportLabels.VERSION));

        // A label edit that does not send the status labels keeps them.
        setLabels(Map.of("team", "fetch"));
        assertEquals("fetch", labels().get("team"));
        importStatus().statusCode(200).body("state", equalTo("COMPLETED")).body("version", equalTo("2"));
    }

    @Test
    void asyncImportOverTheConcurrencyLimitIsRefused() throws Exception {
        int limit = importConfig.getMaxConcurrent();
        mcp.tools(McpTestServer.tool("search", "string"));
        CompletableFuture<Void> release = new CompletableFuture<>();
        mcp.release = release;
        int before = mcp.requests.get();
        double busy = count(McpToolImportService.METRIC_FAILURES, "reason", "busy");
        List<String> servers = otherServers(limit);
        try {
            for (String server : servers) {
                importInto(server, Map.of("async", true)).statusCode(202);
            }
            await().atMost(Duration.ofSeconds(10)).until(() -> mcp.requests.get() >= before + limit);
            int inFlight = mcp.requests.get();
            given().get(path(servers.get(0)) + "/mcp-import/status").then().statusCode(200)
                    .body("state", equalTo("RUNNING"));

            importTools(Map.of("async", true)).statusCode(429)
                    .body("title", equalTo("Too many MCP imports are running. Try again later."));
            importTools().statusCode(429);

            assertEquals(inFlight, mcp.requests.get(), "the refused imports must not contact the MCP server");
            assertEquals(2, count(McpToolImportService.METRIC_FAILURES, "reason", "busy") - busy);
        } finally {
            mcp.release = null;
            release.complete(null);
        }
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> given().get(path(servers.get(0))
                + "/mcp-import/status").then().statusCode(200).body("state", equalTo("COMPLETED")));
        importStatus().statusCode(404);
    }

    @Test
    void importIntoAServerWithARunningImportIsRefused() throws Exception {
        useGatedGroup();
        mcp.tools(McpTestServer.tool("search", "string"), McpTestServer.tool("fetch", "string"));
        CompletableFuture<Void> gate = gateHook.hold(SERVER + ".fetch");
        double alreadyRunning = count(McpToolImportService.METRIC_FAILURES, "reason", "already-running");

        importTools(Map.of("async", true)).statusCode(202);
        await().atMost(Duration.ofSeconds(10)).until(() -> gateHook.reached(SERVER + ".fetch"));
        String first = labels().get(McpImportLabels.ID);
        int requests = mcp.requests.get();

        importTools().statusCode(409).body("title", equalTo("An import into this MCP server is already running."));
        importTools(Map.of("async", true)).statusCode(409)
                .body("title", equalTo("An import into this MCP server is already running."));

        assertEquals(requests, mcp.requests.get(), "a refused import must not contact the MCP server");
        assertEquals(2, count(McpToolImportService.METRIC_FAILURES, "reason", "already-running") - alreadyRunning);
        assertEquals(first, labels().get(McpImportLabels.ID));
        importStatus().statusCode(200).body("state", equalTo("RUNNING"));

        gate.complete(null);
        awaitFinished().body("state", equalTo("COMPLETED")).body("counts", equalTo(Map.of("CREATED", 2)));
        assertEquals(first, labels().get(McpImportLabels.ID));
    }

    @Test
    void runningImportRecordedByAnotherInstanceRefusesImportsButNotDryRuns() {
        mcp.tools(McpTestServer.tool("search", "string"));
        String now = Instant.now().toString();
        setLabels(Map.of(McpImportLabels.ID, "elsewhere", McpImportLabels.STATE, "RUNNING",
                McpImportLabels.STARTED_ON, now, McpImportLabels.UPDATED_ON, now));
        int requests = mcp.requests.get();

        importTools().statusCode(409);
        importTools(Map.of("async", true)).statusCode(409);
        assertEquals(requests, mcp.requests.get());
        importTools(Map.of("dryRun", true)).statusCode(200).body("tools[0].status", equalTo("CREATED"));
        assertEquals("elsewhere", labels().get(McpImportLabels.ID));

        // Once that import is stale, it no longer blocks.
        setLabels(Map.of(McpImportLabels.ID, "elsewhere", McpImportLabels.STATE, "RUNNING",
                McpImportLabels.STARTED_ON, "2020-01-01T00:00:00Z", McpImportLabels.UPDATED_ON,
                "2020-01-01T00:00:00Z"));
        importTools().statusCode(200).body("version", equalTo("2"));
        importStatus().statusCode(200).body("state", equalTo("COMPLETED"));
        assertNotEquals("elsewhere", labels().get(McpImportLabels.ID));
    }

    /**
     * Another instance can start an import between this import's start and its end, because the
     * label check is not atomic. This import then leaves that newer status alone.
     */
    @Test
    void olderImportDoesNotOverwriteTheStatusOfANewerOne() throws Exception {
        useGatedGroup();
        mcp.tools(McpTestServer.tool("search", "string"), McpTestServer.tool("fetch", "string"));
        CompletableFuture<Void> gate = gateHook.hold(SERVER + ".fetch");

        importTools(Map.of("async", true)).statusCode(202);
        await().atMost(Duration.ofSeconds(10)).until(() -> gateHook.reached(SERVER + ".fetch"));
        String now = Instant.now().toString();
        setLabels(Map.of(McpImportLabels.ID, "newer", McpImportLabels.STATE, "RUNNING",
                McpImportLabels.STARTED_ON, now, McpImportLabels.UPDATED_ON, now));
        gate.complete(null);
        await().atMost(Duration.ofSeconds(30))
                .until(() -> importService.availablePermits() == importConfig.getMaxConcurrent());

        // The older import finished: both tools and the server version are written...
        assertEquals(List.of("fetch=srv.fetch@1", "search=srv.search@1"), references("2"));
        // ...but its COMPLETED did not replace the newer import's status.
        Map<String, String> labels = labels();
        assertEquals("newer", labels.get(McpImportLabels.ID));
        assertEquals("RUNNING", labels.get(McpImportLabels.STATE));
        assertEquals(now, labels.get(McpImportLabels.UPDATED_ON));
        assertFalse(labels.containsKey(McpImportLabels.FINISHED_ON));
        importStatus().statusCode(200).body("state", equalTo("RUNNING"));
    }

    @Test
    void malformedStatusLabelsGiveADefinedStatus() {
        // A failure without a reason, and dates that cannot be read
        setLabels(Map.of(McpImportLabels.STATE, "FAILED", McpImportLabels.STARTED_ON, "yesterday",
                McpImportLabels.FINISHED_ON, "2026-13-45"));
        importStatus().statusCode(200).body("state", equalTo("FAILED"))
                .body("reason", equalTo("The import failed.")).body("startedOn", nullValue())
                .body("finishedOn", nullValue());

        // Counts that are not numbers are left out; the others are reported
        setLabels(Map.of(McpImportLabels.STATE, "COMPLETED", McpImportLabels.VERSION, "7",
                McpImportLabels.COUNT_PREFIX + "created", "many", McpImportLabels.COUNT_PREFIX + "unchanged", "3",
                McpImportLabels.COUNT_PREFIX + "bogus", "1"));
        importStatus().statusCode(200).body("state", equalTo("COMPLETED")).body("version", equalTo("7"))
                .body("counts", equalTo(Map.of("UNCHANGED", 3))).body("reason", nullValue());

        // A running import with no time it can be judged by is reported as stale
        setLabels(Map.of(McpImportLabels.STATE, "RUNNING", McpImportLabels.STARTED_ON, "soon"));
        importStatus().statusCode(200).body("state", equalTo("FAILED"))
                .body("reason", equalTo("The import stopped reporting progress before it finished."));

        // A state that is empty, or not in upper case, is not a recorded import
        setLabels(Map.of(McpImportLabels.STATE, ""));
        importStatus().statusCode(404);
        setLabels(Map.of(McpImportLabels.STATE, "completed"));
        importStatus().statusCode(404);
    }
}
