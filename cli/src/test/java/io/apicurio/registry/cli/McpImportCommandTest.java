package io.apicurio.registry.cli;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static java.time.Duration.ofSeconds;
import static java.util.Optional.ofNullable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@code acr mcp import} against a Registry with MCP import enabled and an MCP server stub.
 * <p>
 * Needs a Registry image with the {@code mcp-import} endpoint (3.4 or later), such as
 * {@code -Dtest.app.image=quay.io/apicurio/apicurio-registry:latest-snapshot}. With an older
 * image, such as the default {@code latest-release}, the class is skipped.
 */
@QuarkusTest
class McpImportCommandTest extends AbstractCLITest {

    // Fails on anything after the first JSON value, so "stdout is only JSON" is really checked.
    private static final ObjectMapper MAPPER = new JsonMapper();
    private static final String TOKEN = "Bearer t0ken-" + UUID.randomUUID();
    private static final String TENANT = "tenant-" + UUID.randomUUID();
    private static final String SERVER_JSON = "{\"name\":\"io.github.test/srv\",\"description\":\"A server\","
            + "\"version\":\"1.0.0\"}";

    static McpStubServer mcp;
    static String mcpUrl;

    String group;

    /**
     * Hides {@link AbstractCLITest#beforeAll()}, so JUnit runs only this one: the container needs
     * MCP import enabled, and the stub's port exposed to it, before it starts.
     */
    @BeforeAll
    public static void beforeAll() {
        try {
            mcp = new McpStubServer();
        } catch (Exception e) {
            throw new IllegalStateException("Could not start the MCP stub server", e);
        }
        Testcontainers.exposeHostPorts(mcp.port());
        mcpUrl = "http://host.testcontainers.internal:" + mcp.port() + "/mcp";

        var appImage = ofNullable(System.getProperty("test.app.image"))
                .orElse("quay.io/apicurio/apicurio-registry:latest-release");
        registryContainer = new GenericContainer<>(appImage)
                .withEnv("APICURIO_FEATURES_EXPERIMENTAL_ENABLED", "true")
                .withEnv("APICURIO_MCP_IMPORT_ENABLED", "true")
                .withEnv("APICURIO_MCP_IMPORT_ALLOWED_HOSTS", "host.testcontainers.internal")
                .withEnv("APICURIO_MCP_IMPORT_INSECURE_HTTP_ENABLED", "true")
                .withExposedPorts(8080)
                .waitingFor(Wait.forHttp("/apis/registry/v3/system/info")
                        .forStatusCode(200)
                        .withStartupTimeout(ofSeconds(60)));
        registryContainer.start();
        registryUrl = "http://localhost:" + registryContainer.getMappedPort(8080);
        assumeTrue(supportsMcpImport(), "The Registry image has no mcp-import endpoint (needs 3.4 or later)");
    }

    @AfterAll
    static void stopMcp() throws Exception {
        if (mcp != null) {
            mcp.close();
        }
    }

    /** True for Registry 3.4 or later, which has the endpoint. */
    private static boolean supportsMcpImport() {
        return isAtLeast34(registryUrl);
    }

    /** True when the Registry at the URL reports version 3.4 or later; false if it cannot tell. */
    static boolean isAtLeast34(String url) {
        String version;
        try {
            var info = HttpClient.newBuilder().connectTimeout(ofSeconds(10)).build().send(
                    HttpRequest.newBuilder(URI.create(url + "/apis/registry/v3/system/info"))
                            .timeout(ofSeconds(10)).build(),
                    HttpResponse.BodyHandlers.ofString());
            version = MAPPER.readTree(info.body()).path("version").asString();
        } catch (Exception e) {
            throw new IllegalStateException("Could not read the Registry version", e);
        }
        var matcher = Pattern.compile("^(\\d+)\\.(\\d+)").matcher(version);
        if (!matcher.find()) {
            return false;
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        return major > 3 || major == 3 && minor >= 4;
    }

    @BeforeEach
    void serverArtifact() throws Exception {
        mcp.receivedHeaders.clear();
        mcp.tools(McpStubServer.tool("search", "string"), McpStubServer.tool("fetch", "string"));
        group = "mcp-" + UUID.randomUUID();
        var file = Files.createTempFile("server", ".json");
        try {
            Files.writeString(file, SERVER_JSON);
            executeAndAssertSuccess("artifact", "create", "srv", "-g", group, "--type", "MCP_SERVER",
                    "--content-type", "application/json", "-f", file.toString());
        } finally {
            Files.deleteIfExists(file);
        }
        clearOutput();
    }

    private void clearOutput() {
        out.getBuffer().setLength(0);
        err.getBuffer().setLength(0);
    }

    private int versionCount(String groupId, String artifactId) {
        return client.getRegistryClient().groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().get().getCount();
    }

    private boolean artifactExists(String groupId, String artifactId) {
        try {
            client.getRegistryClient().groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
            return true;
        } catch (io.apicurio.registry.rest.client.models.ProblemDetails e) {
            assertThat(e.getResponseStatusCode()).isEqualTo(404);
            return false;
        }
    }

    @Test
    void importPrintsEachToolAndTheNewServerVersion() {
        int code = cmd.execute("mcp", "import", "srv", "-g", group, "--url", mcpUrl);

        assertThat(code).as(withCliOutput("import should succeed")).isZero();
        assertThat(out.toString())
                .containsPattern("search\\s+" + group + "\\s+srv\\.search\\s+1\\s+CREATED")
                .containsPattern("fetch\\s+" + group + "\\s+srv\\.fetch\\s+1\\s+CREATED")
                .contains("MCP server '" + group + "/srv' now at version 2.");
        assertThat(versionCount(group, "srv")).isEqualTo(2);
        assertThat(versionCount(group, "srv.search")).isEqualTo(1);
    }

    @Test
    void artifactIdDefaultsToTheCurrentContext() {
        executeAndAssertSuccess("context", "update", "-g", group, "-a", "srv");
        clearOutput();

        int code = cmd.execute("mcp", "import", "--url", mcpUrl);

        assertThat(code).as(withCliOutput("import using the context artifact should succeed")).isZero();
        assertThat(out.toString()).contains("MCP server '" + group + "/srv' now at version 2.");
        assertThat(versionCount(group, "srv")).isEqualTo(2);
    }

    /**
     * The edge case agreed for this change: a dry run into another tool group, as JSON. Nothing is
     * written, and stdout holds only the JSON document.
     */
    @Test
    void dryRunIntoAnotherToolGroupAsJsonWritesNothing() throws Exception {
        String toolGroup = "tools-" + UUID.randomUUID();

        int code = cmd.execute("mcp", "import", "srv", "-g", group, "--tool-group", toolGroup,
                "--dry-run", "-o", "json", "--url", mcpUrl);

        assertThat(code).as(withCliOutput("dry run should succeed")).isZero();
        JsonNode result = MAPPER.readTree(out.toString());
        assertThat(result.path("groupId").asString()).isEqualTo(group);
        assertThat(result.path("status").asString()).isEqualTo("NEW_VERSION");
        assertThat(result.has("version")).isFalse();
        assertThat(result.path("tools")).hasSize(2);
        for (JsonNode tool : result.path("tools")) {
            assertThat(tool.path("groupId").asString()).isEqualTo(toolGroup);
            assertThat(tool.path("status").asString()).isEqualTo("CREATED");
            assertThat(tool.has("version")).isFalse();
        }
        assertThat(artifactExists(toolGroup, "srv.search")).isFalse();
        assertThat(versionCount(group, "srv")).isEqualTo(1);
    }

    @Test
    void rejectedToolIsReportedWithItsViolationsAndExitsZero() {
        executeAndAssertSuccess("mcp", "import", "srv", "-g", group, "--url", mcpUrl);
        var rule = new CreateRule();
        rule.setRuleType(RuleType.COMPATIBILITY);
        rule.setConfig("BACKWARD");
        client.getRegistryClient().groups().byGroupId(group).rules().post(rule);
        mcp.tools(McpStubServer.tool("search", "integer"), McpStubServer.tool("fetch", "string"));
        clearOutput();

        int code = cmd.execute("mcp", "import", "srv", "-g", group, "--url", mcpUrl);

        assertThat(code).as(withCliOutput("a rejected tool is a reported outcome, not a failure")).isZero();
        assertThat(out.toString())
                .containsPattern("search\\s+" + group + "\\s+srv\\.search\\s+REJECTED")
                .contains("Rejected 'search':")
                .contains("-> /inputSchema/properties/query/type: ");
        assertThat(versionCount(group, "srv.search")).isEqualTo(1);
    }

    /**
     * Headers reach the MCP server, and the token appears nowhere: not in the output, and not in
     * the HTTP log that --verbose turns on, which would otherwise include the request body.
     */
    @Test
    void headersReachTheServerButNeverTheOutputOrTheVerboseLog() throws Exception {
        var logged = new CopyOnWriteArrayList<String>();
        var capture = new Handler() {
            @Override
            public void publish(LogRecord logRecord) {
                logged.add(String.valueOf(logRecord.getMessage()) + " "
                        + (logRecord.getParameters() == null ? "" : List.of(logRecord.getParameters())));
            }

            @Override
            public void flush() {
                // Nothing is buffered.
            }

            @Override
            public void close() {
                // Nothing to release.
            }
        };
        Logger rootLogger = Logger.getLogger("");
        Logger httpLogger = Logger.getLogger("io.apicurio.registry.client.http");
        Level originalRootLevel = rootLogger.getLevel();
        Level originalHttpLevel = httpLogger.getLevel();
        httpLogger.addHandler(capture);
        Path headerFile = Files.createTempFile("headers", ".txt");
        try {
            // Shows the capture works: a verbose command does log its HTTP traffic here. The
            // client cached by the setup was created without logging, so start from a fresh one.
            client.reset();
            executeAndAssertSuccess("--verbose", "artifact", "get", "srv", "-g", group);
            assertThat(logged).as("--verbose should log HTTP traffic").isNotEmpty();
            logged.clear();
            clearOutput();

            Files.writeString(headerFile, "Authorization: " + TOKEN + "\n");
            config.setEnvOverride("MCP_TENANT", TENANT);

            // No reset: the cached client still logs, from the verbose command above. A later
            // command without --verbose in the same JVM must not reuse it for the import.
            int quietCode = cmd.execute("mcp", "import", "srv", "-g", group, "--url", mcpUrl,
                    "--header-file", headerFile.toString(), "--header-env", "X-Tenant=MCP_TENANT");
            assertThat(quietCode).as(withCliOutput("non-verbose import with headers should succeed")).isZero();
            assertThat(logged).noneMatch(line -> line.contains(TOKEN) || line.contains(TENANT)
                    || line.contains("mcp-import"));
            clearOutput();

            int code = cmd.execute("--verbose", "mcp", "import", "srv", "-g", group, "--url", mcpUrl,
                    "--header-file", headerFile.toString(), "--header-env", "X-Tenant=MCP_TENANT");

            assertThat(code).as(withCliOutput("import with headers should succeed")).isZero();
            assertThat(mcp.receivedHeaders).isNotEmpty()
                    .allSatisfy(h -> {
                        assertThat(h).containsEntry("authorization", TOKEN);
                        assertThat(h).containsEntry("x-tenant", TENANT);
                    });
            assertThat(out.toString() + err.toString()).doesNotContain(TOKEN).doesNotContain(TENANT);
            assertThat(err.toString()).contains("HTTP logging is disabled for this request");
            assertThat(logged).noneMatch(line -> line.contains(TOKEN) || line.contains(TENANT)
                    || line.contains("mcp-import"));
        } finally {
            httpLogger.removeHandler(capture);
            // --verbose lowers the root level for the whole JVM; do not leave it for other tests.
            rootLogger.setLevel(originalRootLevel);
            httpLogger.setLevel(originalHttpLevel);
            config.clearEnvOverrides();
            Files.deleteIfExists(headerFile);
        }
    }

    @Test
    void refusedTargetPrintsTheServerReasonAndExitsWithTheServerErrorCode() {
        int code = cmd.execute("mcp", "import", "srv", "-g", group, "--url", "https://not-allowed.example.com/mcp");

        assertThat(code).as(withCliOutput("a refused host is a server error")).isEqualTo(3);
        assertThat(err.toString() + out.toString()).contains("The MCP server URL is not allowed by this registry.");
        assertThat(versionCount(group, "srv")).isEqualTo(1);
    }
}
