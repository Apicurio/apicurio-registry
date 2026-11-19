package io.apicurio.registry.agents.inventory;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.agents.mcpimport.McpTestServer;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.kubernetes.client.KubernetesServer;
import io.quarkus.test.kubernetes.client.KubernetesTestServer;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;
import io.restassured.response.ValidatableResponse;
import io.vertx.core.json.Json;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Syncing a group with each inventory source: what is created, kept, versioned, reported missing
 * and refused, against a Kubernetes API mock and a Google Cloud Agent Registry stub.
 */
@QuarkusTest
@TestProfile(AgentInventoryTestProfile.class)
@WithKubernetesTestServer(crud = false)
@Execution(ExecutionMode.SAME_THREAD)
public class AgentInventorySyncTest extends AbstractResourceTestBase {

    static final String NS = AgentInventoryTestProfile.NAMESPACE;

    @KubernetesTestServer
    KubernetesServer kubernetes;

    // Each test class reads its own Google Cloud location, so classes never see each other's pages.
    String project;
    String location;

    @BeforeEach
    void resetGoogle() {
        project = getClass().getSimpleName().toLowerCase(Locale.ROOT);
        location = "projects/" + project + "/locations/us-central1";
        GoogleAgentRegistryStub.get().reset(location);
    }

    // ---------------------------------------------------------------- Kubernetes MCPServer

    @Test
    void mcpServersAreCreatedThenUnchanged() {
        String group = TestUtils.generateGroupId();
        serve("mcp.x-k8s.io", "v1", "mcpservers", NS, mcpServer("weather", "1.0.0", "http://weather.team-a.svc:8080/mcp"));

        sync(group, mcpServers()).statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].externalId", equalTo("team-a/weather"))
                .body("items[0].artifactId", equalTo("k8s-mcp.team-a.weather"))
                .body("items[0].artifactType", equalTo("MCP_SERVER"))
                .body("items[0].status", equalTo("CREATED"))
                .body("items[0].version", equalTo("1"));

        given().get(registryV3ApiUrl + "/groups/" + group + "/artifacts/k8s-mcp.team-a.weather/versions/1/content")
                .then().statusCode(200)
                .body("name", equalTo("io.k8s.team-a/weather"))
                .body("version", equalTo("1.0.0"))
                .body("remotes[0].type", equalTo("streamable-http"))
                .body("remotes[0].url", equalTo("http://weather.team-a.svc:8080/mcp"));
        given().get(registryV3ApiUrl + "/groups/" + group + "/artifacts/k8s-mcp.team-a.weather").then()
                .body("labels.'apicurio.agent-inventory.source'", equalTo("KUBERNETES_MCP_SERVERS"))
                .body("labels.'apicurio.agent-inventory.scope'", equalTo("team-a"))
                .body("labels.'apicurio.agent-inventory.external-id'", equalTo("team-a/weather"));

        serve("mcp.x-k8s.io", "v1", "mcpservers", NS, mcpServer("weather", "1.0.0", "http://weather.team-a.svc:8080/mcp"));
        sync(group, mcpServers()).statusCode(200)
                .body("items[0].status", equalTo("UNCHANGED"))
                .body("items[0].version", equalTo("1"));
    }

    @Test
    void changedServerGetsANewVersion() {
        String group = TestUtils.generateGroupId();
        serve("mcp.x-k8s.io", "v1", "mcpservers", NS, mcpServer("weather", "1.0.0", "http://weather.team-a.svc:8080/mcp"));
        sync(group, mcpServers()).statusCode(200).body("items[0].status", equalTo("CREATED"));

        serve("mcp.x-k8s.io", "v1", "mcpservers", NS, mcpServer("weather", "1.1.0", "http://weather.team-a.svc:8080/mcp"));
        sync(group, mcpServers()).statusCode(200)
                .body("items[0].status", equalTo("UPDATED"))
                .body("items[0].version", equalTo("2"));
        given().get(registryV3ApiUrl + "/groups/" + group + "/artifacts/k8s-mcp.team-a.weather/versions/2/content")
                .then().body("version", equalTo("1.1.0"));
    }

    @Test
    void removedResourceIsReportedMissingAndDeprecatedThenEnabledWhenListedAgain() {
        String group = TestUtils.generateGroupId();
        serve("mcp.x-k8s.io", "v1", "mcpservers", NS, mcpServer("weather", "1.0.0", "http://weather.team-a.svc:8080/mcp"),
                mcpServer("news", "1.0.0", "http://news.team-a.svc:8080/mcp"));
        sync(group, mcpServers()).statusCode(200).body("items.status", contains("CREATED", "CREATED"));

        serve("mcp.x-k8s.io", "v1", "mcpservers", NS, mcpServer("weather", "1.0.0", "http://weather.team-a.svc:8080/mcp"));
        sync(group, mcpServers()).statusCode(200)
                .body("items", hasSize(2))
                .body("items[1].artifactId", equalTo("k8s-mcp.team-a.news"))
                .body("items[1].externalId", equalTo("team-a/news"))
                .body("items[1].artifactType", equalTo("MCP_SERVER"))
                .body("items[1].version", equalTo("1"))
                .body("items[1].status", equalTo("MISSING"));
        assertEquals("DEPRECATED", state(group, "k8s-mcp.team-a.news"));

        serve("mcp.x-k8s.io", "v1", "mcpservers", NS, mcpServer("weather", "1.0.0", "http://weather.team-a.svc:8080/mcp"),
                mcpServer("news", "1.0.0", "http://news.team-a.svc:8080/mcp"));
        sync(group, mcpServers()).statusCode(200).body("items.status", contains("UNCHANGED", "UPDATED"));
        assertEquals("ENABLED", state(group, "k8s-mcp.team-a.news"));
    }

    @Test
    void anotherNamespaceIsAnotherScope() {
        String group = TestUtils.generateGroupId();
        serve("mcp.x-k8s.io", "v1", "mcpservers", NS, mcpServer("weather", "1.0.0", "http://weather.team-a.svc:8080/mcp"));
        sync(group, mcpServers()).statusCode(200).body("items[0].status", equalTo("CREATED"));

        // Syncing team-b, which lists nothing, does not report team-a's server as missing.
        serve("mcp.x-k8s.io", "v1", "mcpservers", "team-b");
        Map<String, Object> request = mcpServers();
        request.put("namespace", "team-b");
        sync(group, request).statusCode(200).body("items", hasSize(0));
        assertEquals("ENABLED", state(group, "k8s-mcp.team-a.weather"));
    }

    @Test
    void artifactTheSyncDidNotCreateIsNotChanged() throws Exception {
        String group = TestUtils.generateGroupId();
        createArtifact(group, "k8s-mcp.team-a.weather", ArtifactType.MCP_SERVER,
                "{\"name\":\"io.github.test/weather\",\"description\":\"Hand-made\",\"version\":\"9.9.9\"}",
                ContentTypes.APPLICATION_JSON);
        serve("mcp.x-k8s.io", "v1", "mcpservers", NS, mcpServer("weather", "1.0.0", "http://weather.team-a.svc:8080/mcp"));

        sync(group, mcpServers()).statusCode(200)
                .body("items[0].status", equalTo("NOT_MANAGED"))
                .body("items[0].version", nullValue());
        given().get(registryV3ApiUrl + "/groups/" + group + "/artifacts/k8s-mcp.team-a.weather/versions").then()
                .body("count", equalTo(1));
    }

    @Test
    void dryRunWritesNothing() {
        String group = TestUtils.generateGroupId();
        serve("mcp.x-k8s.io", "v1", "mcpservers", NS, mcpServer("weather", "1.0.0", "http://weather.team-a.svc:8080/mcp"));
        Map<String, Object> request = mcpServers();
        request.put("dryRun", true);

        sync(group, request).statusCode(200)
                .body("items[0].status", equalTo("CREATED"))
                .body("items[0].version", nullValue());
        given().get(registryV3ApiUrl + "/groups/" + group + "/artifacts/k8s-mcp.team-a.weather").then()
                .statusCode(404);
    }

    @Test
    void olderCustomResourceVersionIsUsedWhenTheNewestIsNotServed() {
        // Open Data Hub ships the MCPServer CRD with v1beta1 as its newest version.
        String group = TestUtils.generateGroupId();
        kubernetes.expect().get().withPath("/apis/mcp.x-k8s.io/v1/namespaces/" + NS + "/mcpservers")
                .andReturn(404, "{\"kind\":\"Status\",\"code\":404}").once();
        serve("mcp.x-k8s.io", "v1beta1", "mcpservers", NS,
                mcpServer("weather", "1.0.0", "http://weather.team-a.svc:8080/mcp"));

        sync(group, mcpServers()).statusCode(200).body("items[0].status", equalTo("CREATED"));
    }

    @Test
    void forbiddenListIsReportedAsASourceError() {
        String group = TestUtils.generateGroupId();
        kubernetes.expect().get().withPath("/apis/mcp.x-k8s.io/v1/namespaces/" + NS + "/mcpservers")
                .andReturn(403, "{\"kind\":\"Status\",\"code\":403}").once();

        sync(group, mcpServers()).statusCode(502)
                .body("title", equalTo("The registry is not allowed to list MCPServer resources in this namespace."));
    }

    @Test
    void importsTheToolsOfEachServer() throws Exception {
        String group = TestUtils.generateGroupId();
        try (McpTestServer mcp = new McpTestServer()) {
            mcp.tools(McpTestServer.tool("search", "string"));
            serve("mcp.x-k8s.io", "v1", "mcpservers", NS, mcpServer("search", "1.0.0", mcp.url()));
            Map<String, Object> request = mcpServers();
            request.put("importTools", true);

            sync(group, request).statusCode(200)
                    .body("items[0].status", equalTo("CREATED"))
                    .body("items[0].toolImport.status", equalTo("NEW_VERSION"))
                    .body("items[0].toolImport.tools[0].artifactId", equalTo("k8s-mcp.team-a.search.search"))
                    .body("items[0].toolImport.tools[0].status", equalTo("CREATED"));
            given().get(registryV3ApiUrl + "/groups/" + group + "/artifacts/k8s-mcp.team-a.search.search")
                    .then().statusCode(200).body("artifactType", equalTo("MCP_TOOL"));
        }
    }

    @Test
    void namespaceOutsideTheAllowListIsRefusedBeforeReading() {
        Map<String, Object> request = mcpServers();
        request.put("namespace", "kube-system");
        int before = kubernetes.getKubernetesMockServer().getRequestCount();

        sync(TestUtils.generateGroupId(), request).statusCode(400)
                .body("detail", containsString("The namespace is not allowed."));
        assertEquals(before, kubernetes.getKubernetesMockServer().getRequestCount());
    }

    @Test
    void kubernetesSourcesRefuseHeaders() {
        Map<String, Object> request = mcpServers();
        request.put("headers", Map.of("Authorization", "Bearer x"));

        sync(TestUtils.generateGroupId(), request).statusCode(400)
                .body("detail", containsString("Kubernetes inventory sources do not use request headers."));
    }

    @Test
    void tooManyItemsRefuseTheSync() {
        String group = TestUtils.generateGroupId();
        serve("mcp.x-k8s.io", "v1", "mcpservers", NS, mcpServer("a", "1", null), mcpServer("b", "1", null),
                mcpServer("c", "1", null), mcpServer("d", "1", null), mcpServer("e", "1", null),
                mcpServer("f", "1", null));

        sync(group, mcpServers()).statusCode(502)
                .body("title", equalTo("The namespace has more MCPServer resources than one sync accepts."));
        given().get(registryV3ApiUrl + "/search/artifacts?groupId=" + group).then().body("count", equalTo(0));
    }

    // ---------------------------------------------------------------- Kubernetes AgentCard

    @Test
    void agentCardsAreNormalizedToA2a1AndUnfetchedCardsAreSkipped() {
        String group = TestUtils.generateGroupId();
        Map<String, Object> card = Map.of("name", "Summarizer", "description", "Summarizes text",
                "version", "1.2.0", "url", "http://summarizer.team-a.svc:8000",
                "capabilities", Map.of("streaming", true), "defaultInputModes", List.of("text"),
                "defaultOutputModes", List.of("text"),
                "skills", List.of(Map.of("id", "summarize", "name", "Summarize", "description", "Summarize")));
        serve("agent.rossoctl.dev", "v1alpha1", "agentcards", NS, agentCard("summarizer", card),
                agentCard("not-fetched-yet", null));

        sync(group, Map.of("source", "KUBERNETES_AGENT_CARDS", "namespace", NS)).statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].artifactId", equalTo("k8s-agent.team-a.summarizer"))
                .body("items[0].artifactType", equalTo("AGENT_CARD"))
                .body("items[0].status", equalTo("CREATED"));
        given().get(registryV3ApiUrl + "/groups/" + group + "/artifacts/k8s-agent.team-a.summarizer/versions/1/content")
                .then().statusCode(200)
                .body("name", equalTo("Summarizer"))
                .body("supportedInterfaces[0].url", equalTo("http://summarizer.team-a.svc:8000"))
                .body("supportedInterfaces[0].protocolBinding", equalTo("JSONRPC"))
                .body("supportedInterfaces[0].protocolVersion", equalTo("0.3.0"));
    }

    // ---------------------------------------------------------------- Google Cloud Agent Registry

    @Test
    void googleAgentsAcrossPagesWithTheCallersCredentials() {
        String group = TestUtils.generateGroupId();
        GoogleAgentRegistryStub google = GoogleAgentRegistryStub.get();
        google.pages(location,
                "{\"agents\":[" + googleAgentWithCard("billing") + "],\"nextPageToken\":\"page-1\"}",
                "{\"agents\":[" + googleAgentWithoutCard("travel") + "]}");

        sync(group, Map.of("source", "GOOGLE_AGENT_REGISTRY", "location", location,
                "headers", Map.of("Authorization", "Bearer google-token"))).statusCode(200)
                .body("items.artifactId", contains("gcp-agent." + project + ".us-central1.billing",
                        "gcp-agent." + project + ".us-central1.travel"))
                .body("items.status", contains("CREATED", "CREATED"))
                .body("items[0].externalId", equalTo(location + "/agents/billing"));
        assertEquals(List.of("Bearer google-token", "Bearer google-token"), google.authorizations(location));

        given().get(registryV3ApiUrl + "/groups/" + group
                + "/artifacts/gcp-agent." + project + ".us-central1.billing/versions/1/content").then()
                .body("name", equalTo("Billing agent"))
                .body("supportedInterfaces[0].url", equalTo("https://billing.example.com/a2a"));
        // Built from the agent's own fields when Google stores no card
        given().get(registryV3ApiUrl + "/groups/" + group
                + "/artifacts/gcp-agent." + project + ".us-central1.travel/versions/1/content").then()
                .body("name", equalTo("travel display name"))
                .body("supportedInterfaces[0].url", equalTo("https://travel.example.com/a2a"))
                .body("supportedInterfaces[0].protocolBinding", equalTo("JSONRPC"))
                .body("supportedInterfaces[0].protocolVersion", equalTo("1.0"))
                .body("skills[0].id", equalTo("book"));
    }

    @Test
    void googleRefusingTheCredentialsIsABadRequest() {
        GoogleAgentRegistryStub.get().status(location, 401);

        sync(TestUtils.generateGroupId(), Map.of("source", "GOOGLE_AGENT_REGISTRY", "location", location,
                "headers", Map.of("Authorization", "Bearer expired"))).statusCode(400)
                .body("title", equalTo("Google Cloud Agent Registry refused the credentials in the request."));
    }

    @Test
    void invalidGoogleLocationIsRefused() {
        sync(TestUtils.generateGroupId(), Map.of("source", "GOOGLE_AGENT_REGISTRY",
                "location", "projects/../locations/x")).statusCode(400)
                .body("detail", containsString("A location is required for Google Cloud Agent Registry"));
    }

    @Test
    void transportHeadersAreRefused() {
        sync(TestUtils.generateGroupId(), Map.of("source", "GOOGLE_AGENT_REGISTRY", "location", location,
                "headers", Map.of("Host", "evil.example.com"))).statusCode(400)
                .body("detail", containsString("Header 'Host' cannot be sent to the inventory source."));
    }

    // ---------------------------------------------------------------- rules, ownership, lifecycle

    @Test
    void itemRejectedByARuleIsReportedWithItsCausesAndTheOthersAreStored() {
        String group = TestUtils.generateGroupId();
        given().contentType(ContentTypes.APPLICATION_JSON).body(Map.of("groupId", group))
                .post(registryV3ApiUrl + "/groups").then().statusCode(200);
        given().contentType(ContentTypes.APPLICATION_JSON).body(Map.of("ruleType", "VALIDITY", "config", "FULL"))
                .post(registryV3ApiUrl + "/groups/" + group + "/rules").then().statusCode(204);
        Map<String, Object> noSkills = Map.of("name", "Broken", "description", "Lists no skills",
                "version", "1.0.0", "url", "http://broken.team-a.svc:8000", "capabilities", Map.of(),
                "defaultInputModes", List.of("text"), "defaultOutputModes", List.of("text"));
        serve("agent.rossoctl.dev", "v1alpha1", "agentcards", NS, agentCard("broken", noSkills),
                agentCard("summarizer", validCard()));

        sync(group, Map.of("source", "KUBERNETES_AGENT_CARDS", "namespace", NS)).statusCode(200)
                .body("items.artifactId", contains("k8s-agent.team-a.broken", "k8s-agent.team-a.summarizer"))
                .body("items.status", contains("REJECTED", "CREATED"))
                .body("items[0].version", nullValue())
                .body("items[0].causes.description", hasItem("Agent Card must have a 'skills' field"));
        given().get(registryV3ApiUrl + "/groups/" + group + "/artifacts/k8s-agent.team-a.broken").then()
                .statusCode(404);
    }

    @Test
    void artifactLabelledForAnotherScopeIsNotManaged() throws Exception {
        String group = TestUtils.generateGroupId();
        createArtifact(group, "k8s-mcp.team-a.weather", ArtifactType.MCP_SERVER,
                "{\"name\":\"io.github.test/weather\",\"description\":\"Hand-made\",\"version\":\"9.9.9\"}",
                ContentTypes.APPLICATION_JSON);
        given().contentType(ContentTypes.APPLICATION_JSON)
                .body(Map.of("labels", Map.of("apicurio.agent-inventory.source", "KUBERNETES_MCP_SERVERS",
                        "apicurio.agent-inventory.scope", "team-b",
                        "apicurio.agent-inventory.external-id", "team-a/weather")))
                .put(registryV3ApiUrl + "/groups/" + group + "/artifacts/k8s-mcp.team-a.weather").then()
                .statusCode(204);
        serve("mcp.x-k8s.io", "v1", "mcpservers", NS, mcpServer("weather", "1.0.0", "http://weather.team-a.svc:8080/mcp"));

        sync(group, mcpServers()).statusCode(200).body("items[0].status", equalTo("NOT_MANAGED"));
        given().get(registryV3ApiUrl + "/groups/" + group + "/artifacts/k8s-mcp.team-a.weather/versions").then()
                .body("count", equalTo(1));
    }

    @Test
    void artifactWithoutAnExternalIdIsNeverReportedMissing() throws Exception {
        String group = TestUtils.generateGroupId();
        createArtifact(group, "hand-made", ArtifactType.MCP_SERVER,
                "{\"name\":\"io.github.test/hand\",\"description\":\"Hand-made\",\"version\":\"1.0.0\"}",
                ContentTypes.APPLICATION_JSON);
        given().contentType(ContentTypes.APPLICATION_JSON)
                .body(Map.of("labels", Map.of("apicurio.agent-inventory.source", "KUBERNETES_MCP_SERVERS",
                        "apicurio.agent-inventory.scope", NS)))
                .put(registryV3ApiUrl + "/groups/" + group + "/artifacts/hand-made").then().statusCode(204);
        serve("mcp.x-k8s.io", "v1", "mcpservers", NS);

        sync(group, mcpServers()).statusCode(200).body("items", hasSize(0));
        assertEquals("ENABLED", state(group, "hand-made"));
    }

    @Test
    void stillMissingStaysDeprecatedAndADisabledVersionIsLeftAlone() {
        String group = TestUtils.generateGroupId();
        serve("mcp.x-k8s.io", "v1", "mcpservers", NS, mcpServer("news", "1.0.0", "http://news.team-a.svc:8080/mcp"),
                mcpServer("old", "1.0.0", "http://old.team-a.svc:8080/mcp"));
        sync(group, mcpServers()).statusCode(200).body("items.status", contains("CREATED", "CREATED"));
        given().contentType(ContentTypes.APPLICATION_JSON).body(Map.of("state", "DISABLED"))
                .put(registryV3ApiUrl + "/groups/" + group + "/artifacts/k8s-mcp.team-a.old/versions/1/state")
                .then().statusCode(204);

        serve("mcp.x-k8s.io", "v1", "mcpservers", NS);
        sync(group, mcpServers()).statusCode(200).body("items.status", contains("MISSING", "MISSING"));
        serve("mcp.x-k8s.io", "v1", "mcpservers", NS);
        sync(group, mcpServers()).statusCode(200).body("items.status", contains("MISSING", "MISSING"));
        assertEquals("DEPRECATED", state(group, "k8s-mcp.team-a.news"));
        assertEquals("DISABLED", state(group, "k8s-mcp.team-a.old"));

        // Listed again: only a version this sync deprecated is enabled again.
        serve("mcp.x-k8s.io", "v1", "mcpservers", NS, mcpServer("news", "1.0.0", "http://news.team-a.svc:8080/mcp"),
                mcpServer("old", "1.0.0", "http://old.team-a.svc:8080/mcp"));
        sync(group, mcpServers()).statusCode(200).body("items.status", contains("UPDATED", "UNCHANGED"));
        assertEquals("ENABLED", state(group, "k8s-mcp.team-a.news"));
        assertEquals("DISABLED", state(group, "k8s-mcp.team-a.old"));
    }

    @Test
    void dryRunReportsMissingWithoutDeprecating() {
        String group = TestUtils.generateGroupId();
        serve("mcp.x-k8s.io", "v1", "mcpservers", NS, mcpServer("news", "1.0.0", "http://news.team-a.svc:8080/mcp"));
        sync(group, mcpServers()).statusCode(200).body("items[0].status", equalTo("CREATED"));
        serve("mcp.x-k8s.io", "v1", "mcpservers", NS);
        Map<String, Object> request = mcpServers();
        request.put("dryRun", true);

        sync(group, request).statusCode(200).body("items[0].status", equalTo("MISSING"));
        assertEquals("ENABLED", state(group, "k8s-mcp.team-a.news"));
    }

    @Test
    void importToolsIsRefusedForSourcesWithoutMcpServers() {
        sync(TestUtils.generateGroupId(), Map.of("source", "GOOGLE_AGENT_REGISTRY", "location", location,
                "importTools", true)).statusCode(400)
                .body("detail", containsString("importTools only applies to the KUBERNETES_MCP_SERVERS source."));
        assertEquals(List.of(), GoogleAgentRegistryStub.get().authorizations(location));
    }

    @Test
    void googleErrorsAreReportedWithFixedMessages() {
        String group = TestUtils.generateGroupId();
        GoogleAgentRegistryStub google = GoogleAgentRegistryStub.get();
        Map<String, Object> request = Map.of("source", "GOOGLE_AGENT_REGISTRY", "location", location);

        // Nothing set for the location: the API answers 404, as for an unknown location
        sync(group, request).statusCode(400)
                .body("title", equalTo("Google Cloud Agent Registry does not know this location."));
        google.status(location, 503);
        sync(group, request).statusCode(502)
                .body("title", equalTo("Google Cloud Agent Registry could not list the agents."));
        google.pages(location, "{\"agents\":[" + googleAgentWithCard("billing") + "],\"nextPageToken\":\"page-0\"}");
        sync(group, request).statusCode(502)
                .body("title", equalTo("Google Cloud Agent Registry's agent list does not end."));
        given().get(registryV3ApiUrl + "/search/artifacts?groupId=" + group).then().body("count", equalTo(0));
    }

    @Test
    void namespaceAndLocationBelongToTheirOwnSources() {
        Map<String, Object> google = Map.of("source", "GOOGLE_AGENT_REGISTRY", "location", location, "namespace", NS);
        sync(TestUtils.generateGroupId(), google).statusCode(400)
                .body("detail", containsString("A namespace is only used by Kubernetes inventory sources."));
        Map<String, Object> kubernetesRequest = mcpServers();
        kubernetesRequest.put("location", location);
        sync(TestUtils.generateGroupId(), kubernetesRequest).statusCode(400)
                .body("detail", containsString("A location is only used by the GOOGLE_AGENT_REGISTRY source."));
    }

    // ---------------------------------------------------------------- review follow-ups

    @Test
    void itemListedAgainAfterAChangeGetsANewLatestVersion() {
        // A, then B, then A again: the latest version must be A, not the B left by the second sync.
        String group = TestUtils.generateGroupId();
        for (String version : List.of("1.0.0", "1.1.0", "1.0.0")) {
            serve("mcp.x-k8s.io", "v1", "mcpservers", NS, mcpServer("weather", version, "http://weather.team-a.svc:8080/mcp"));
            sync(group, mcpServers()).statusCode(200);
        }
        given().get(registryV3ApiUrl + "/groups/" + group + "/artifacts/k8s-mcp.team-a.weather/versions/branch=latest")
                .then().body("version", equalTo("3"));
        given().get(registryV3ApiUrl + "/groups/" + group
                + "/artifacts/k8s-mcp.team-a.weather/versions/branch=latest/content").then()
                .body("version", equalTo("1.0.0"));
    }

    @Test
    void disabledServerGetsNoToolImport() throws Exception {
        String group = TestUtils.generateGroupId();
        try (McpTestServer mcp = new McpTestServer()) {
            mcp.tools(McpTestServer.tool("search", "string"));
            serve("mcp.x-k8s.io", "v1", "mcpservers", NS, mcpServer("search", "1.0.0", mcp.url()));
            sync(group, mcpServers()).statusCode(200).body("items[0].status", equalTo("CREATED"));
            given().contentType(ContentTypes.APPLICATION_JSON).body(Map.of("state", "DISABLED"))
                    .put(registryV3ApiUrl + "/groups/" + group + "/artifacts/k8s-mcp.team-a.search/versions/1/state")
                    .then().statusCode(204);
            int before = mcp.requests.get();
            serve("mcp.x-k8s.io", "v1", "mcpservers", NS, mcpServer("search", "1.0.0", mcp.url()));
            Map<String, Object> request = mcpServers();
            request.put("importTools", true);

            sync(group, request).statusCode(200)
                    .body("items[0].status", equalTo("UNCHANGED"))
                    .body("items[0].toolImport", nullValue());
            assertEquals(before, mcp.requests.get());
            assertEquals("DISABLED", state(group, "k8s-mcp.team-a.search"));
        }
    }

    @Test
    void changedExternalIdLabelStopsReconciliation() {
        String group = TestUtils.generateGroupId();
        serve("mcp.x-k8s.io", "v1", "mcpservers", NS, mcpServer("weather", "1.0.0", "http://weather.team-a.svc:8080/mcp"));
        sync(group, mcpServers()).statusCode(200).body("items[0].status", equalTo("CREATED"));
        given().contentType(ContentTypes.APPLICATION_JSON)
                .body(Map.of("labels", Map.of("apicurio.agent-inventory.source", "KUBERNETES_MCP_SERVERS",
                        "apicurio.agent-inventory.scope", NS, "apicurio.agent-inventory.external-id", "team-a/other")))
                .put(registryV3ApiUrl + "/groups/" + group + "/artifacts/k8s-mcp.team-a.weather").then()
                .statusCode(204);

        serve("mcp.x-k8s.io", "v1", "mcpservers", NS);
        sync(group, mcpServers()).statusCode(200).body("items", hasSize(0));
        assertEquals("ENABLED", state(group, "k8s-mcp.team-a.weather"));
    }

    @Test
    void agentCardNotFetchedYetIsNotReportedMissing() {
        String group = TestUtils.generateGroupId();
        serve("agent.rossoctl.dev", "v1alpha1", "agentcards", NS, agentCard("summarizer", validCard()));
        sync(group, Map.of("source", "KUBERNETES_AGENT_CARDS", "namespace", NS)).statusCode(200)
                .body("items[0].status", equalTo("CREATED"));

        // The resource is still there, its card is not cached at the moment
        serve("agent.rossoctl.dev", "v1alpha1", "agentcards", NS, agentCard("summarizer", null));
        sync(group, Map.of("source", "KUBERNETES_AGENT_CARDS", "namespace", NS)).statusCode(200)
                .body("items", hasSize(0));
        assertEquals("ENABLED", state(group, "k8s-agent.team-a.summarizer"));
    }

    @Test
    void malformedGooglePagesFailBeforeAnythingIsWritten() {
        String group = TestUtils.generateGroupId();
        GoogleAgentRegistryStub google = GoogleAgentRegistryStub.get();
        Map<String, Object> request = Map.of("source", "GOOGLE_AGENT_REGISTRY", "location", location);
        google.pages(location, "{\"agents\":[" + googleAgentWithCard("billing") + "]}");
        sync(group, request).statusCode(200).body("items[0].status", equalTo("CREATED"));

        // Neither reads as "the agents are gone"
        google.pages(location, "{\"agents\":\"unavailable\"}");
        sync(group, request).statusCode(502)
                .body("title", equalTo("Google Cloud Agent Registry sent an invalid response."));
        google.pages(location, "{\"agents\":[" + googleAgentWithCard("billing") + "],\"nextPageToken\":5}");
        sync(group, request).statusCode(502)
                .body("title", equalTo("Google Cloud Agent Registry sent an invalid response."));
        assertEquals("ENABLED", state(group, "gcp-agent." + project + ".us-central1.billing"));
    }

    @Test
    void stalledGoogleResponseBodyTimesOut() {
        GoogleAgentRegistryStub.get().stall(location);
        long start = System.nanoTime();

        sync(TestUtils.generateGroupId(), Map.of("source", "GOOGLE_AGENT_REGISTRY", "location", location))
                .statusCode(502)
                .body("title", equalTo("Google Cloud Agent Registry did not answer in time."));
        // The profile's timeout is 2 s; the stub would stall for 10 s.
        assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(8));
    }

    // ---------------------------------------------------------------- helpers

    private ValidatableResponse sync(String group, Map<String, Object> request) {
        return given().contentType(ContentTypes.APPLICATION_JSON).body(request)
                .post(registryV3ApiUrl + "/groups/" + group + "/agent-inventory-sync").then();
    }

    private static Map<String, Object> mcpServers() {
        Map<String, Object> request = new HashMap<>();
        request.put("source", "KUBERNETES_MCP_SERVERS");
        request.put("namespace", NS);
        return request;
    }

    /** The state of the newest version, whatever its state: branch=latest skips disabled versions. */
    private String state(String group, String artifactId) {
        return given().get(registryV3ApiUrl + "/groups/" + group + "/artifacts/" + artifactId
                + "/versions?orderby=createdOn&order=desc&limit=1").then().statusCode(200)
                .extract().path("versions[0].state");
    }

    /** Answers the next list request for the resources with these items. */
    private void serve(String apiGroup, String version, String plural, String namespace, String... items) {
        kubernetes.expect().get().withPath("/apis/" + apiGroup + "/" + version + "/namespaces/" + namespace + "/" + plural)
                .andReturn(200, "{\"apiVersion\":\"" + apiGroup + "/" + version + "\",\"kind\":\"List\","
                        + "\"metadata\":{},\"items\":[" + String.join(",", items) + "]}")
                .once();
    }

    private static String mcpServer(String name, String version, String url) {
        String address = url == null ? "" : ",\"address\":{\"url\":\"" + url + "\"}";
        return "{\"apiVersion\":\"mcp.x-k8s.io/v1\",\"kind\":\"MCPServer\",\"metadata\":{\"name\":\"" + name
                + "\",\"namespace\":\"" + NS + "\"},\"spec\":{},\"status\":{\"serverInfo\":{\"name\":\"" + name
                + "\",\"version\":\"" + version + "\"}" + address + "}}";
    }

    private static Map<String, Object> validCard() {
        return Map.of("name", "Summarizer", "description", "Summarizes text", "version", "1.2.0",
                "url", "http://summarizer.team-a.svc:8000", "capabilities", Map.of(),
                "defaultInputModes", List.of("text"), "defaultOutputModes", List.of("text"),
                "skills", List.of(Map.of("id", "summarize", "name", "Summarize", "description", "Summarize",
                        "tags", List.of("text"))));
    }

    private static String agentCard(String name, Map<String, Object> card) {
        String status = card == null ? "{}" : "{\"card\":" + Json.encode(card) + "}";
        return "{\"apiVersion\":\"agent.rossoctl.dev/v1alpha1\",\"kind\":\"AgentCard\",\"metadata\":{\"name\":\""
                + name + "\",\"namespace\":\"" + NS + "\"},\"spec\":{},\"status\":" + status + "}";
    }

    private String googleAgentWithCard(String id) {
        return "{\"name\":\"" + location + "/agents/" + id + "\",\"agentId\":\"" + id + "\",\"displayName\":\"" + id
                + "\",\"card\":{\"type\":\"A2A_AGENT_CARD\",\"content\":{\"name\":\"Billing agent\","
                + "\"description\":\"Answers billing questions\",\"version\":\"2.0.0\",\"supportedInterfaces\":"
                + "[{\"url\":\"https://billing.example.com/a2a\",\"protocolBinding\":\"JSONRPC\","
                + "\"protocolVersion\":\"1.0\"}],\"capabilities\":{},\"defaultInputModes\":[\"text\"],"
                + "\"defaultOutputModes\":[\"text\"],\"skills\":[{\"id\":\"bill\",\"name\":\"Bill\","
                + "\"description\":\"Billing\",\"tags\":[\"billing\"]}]}}}";
    }

    private String googleAgentWithoutCard(String id) {
        return "{\"name\":\"" + location + "/agents/" + id + "\",\"agentId\":\"" + id + "\",\"displayName\":\"" + id
                + " display name\",\"description\":\"Books trips\",\"version\":\"1.0.0\",\"protocols\":[{\"type\":"
                + "\"A2A_AGENT\",\"protocolVersion\":\"1.0\",\"interfaces\":[{\"url\":\"https://" + id
                + ".example.com/a2a\",\"protocolBinding\":\"JSONRPC\"}]}],\"skills\":[{\"id\":\"book\","
                + "\"name\":\"Book\",\"description\":\"Book a trip\",\"tags\":[\"travel\"]}]}";
    }
}
