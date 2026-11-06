package io.apicurio.registry.client.refs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.content.refs.resolve.InMemorySchemaSource;
import io.apicurio.registry.content.refs.resolve.PlanOptions;
import io.apicurio.registry.content.refs.resolve.PlannedArtifact;
import io.apicurio.registry.content.refs.resolve.ReferenceResolver;
import io.apicurio.registry.content.refs.resolve.RegistrationPlan;
import io.apicurio.registry.content.refs.resolve.RootArtifact;
import io.apicurio.registry.content.refs.resolve.SchemaSourceSet;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReferenceRegistrarErrorsTest {

    private static final String BASE = "/apis/registry/v3";
    private static final String ROOT_ARTIFACTS = BASE + "/groups/g/artifacts";
    private static final String CONFLICT = """
            {"status":409,"title":"Artifact already exists","detail":"g/R exists","name":"ArtifactAlreadyExistsException"}
            """;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WireMockServer server;
    private RegistryClient client;

    @BeforeEach
    void setup() {
        server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        server.start();
        client = RegistryClientFactory.create(RegistryClientOptions.create(server.baseUrl() + BASE));
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private void stubJson(MappingBuilder mapping, int status, String body) {
        server.stubFor(mapping.willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", status >= 400 ? "application/problem+json" : "application/json")
                .withBody(body)));
    }

    private void stubRootConflict() {
        stubJson(post(urlPathEqualTo(ROOT_ARTIFACTS)), 409, CONFLICT);
    }

    private void stubVersionGet(String version, String state) {
        stubJson(get(urlPathEqualTo(ROOT_ARTIFACTS + "/R/versions/" + version)), 200,
                "{\"groupId\":\"g\",\"artifactId\":\"R\",\"version\":\"" + version + "\",\"state\":\"" + state
                        + "\"}");
    }

    private void stubContentPut(String version) {
        server.stubFor(put(urlPathEqualTo(ROOT_ARTIFACTS + "/R/versions/" + version + "/content"))
                .willReturn(aResponse().withStatus(204)));
    }

    private RegistrationPlan avroRoot(Boolean draft, String version) {
        return ReferenceResolver.plan(List.of(new RootArtifact("R.avsc", "g", "R", null, null, version, draft)),
                SchemaSourceSet.of(InMemorySchemaSource.of("R.avsc",
                        "{\"type\":\"record\",\"name\":\"R\",\"fields\":[]}", ContentTypes.APPLICATION_JSON)),
                PlanOptions.builder().build());
    }

    private RegistrationPlan openApiRoot(String infoVersion) {
        String yaml = "openapi: 3.0.3\ninfo:\n  title: T\n  version: \"" + infoVersion + "\"\npaths: {}\n";
        return ReferenceResolver.plan(List.of(RootArtifact.of("api.yaml", "g", "R")),
                SchemaSourceSet.of(InMemorySchemaSource.of("api.yaml", yaml, ContentTypes.APPLICATION_YAML,
                        ArtifactType.OPENAPI)),
                PlanOptions.builder().build());
    }

    private List<String> requestLog() {
        List<LoggedRequest> requests = new ArrayList<>(server.findAll(anyRequestedFor(anyUrl())));
        requests.sort((a, b) -> a.getLoggedDate().compareTo(b.getLoggedDate()));
        return requests.stream().map(r -> r.getMethod() + " " + r.getUrl().split("\\?")[0]).toList();
    }

    @Test
    void conflictWithFailPolicyThrowsWithPartialResult() {
        stubJson(post(urlPathEqualTo(BASE + "/groups/c/artifacts")), 200,
                "{\"version\":{\"groupId\":\"c\",\"artifactId\":\"Common\",\"version\":\"1\"}}");
        stubRootConflict();
        RegistrationPlan plan = ReferenceResolver.plan(List.of(RootArtifact.of("R.avsc", "g", "R")),
                SchemaSourceSet.of(
                        InMemorySchemaSource.of("R.avsc", "{\"type\":\"record\",\"name\":\"R\",\"namespace\":\"r\","
                                + "\"fields\":[{\"name\":\"x\",\"type\":\"c.Common\"}]}",
                                ContentTypes.APPLICATION_JSON),
                        InMemorySchemaSource.of("Common.avsc", "{\"type\":\"record\",\"name\":\"Common\","
                                + "\"namespace\":\"c\",\"fields\":[]}", ContentTypes.APPLICATION_JSON)),
                PlanOptions.builder().build());

        RegistrationException ex = assertThrows(RegistrationException.class,
                () -> ReferenceRegistrar.builder(client).ifExists(IfArtifactExists.FAIL).build().execute(plan));

        assertTrue(ex.failed().isRoot());
        assertEquals("R", ex.failed().coordinates().artifactId());
        assertEquals(409, ex.problem().getResponseStatusCode());
        assertEquals(1, ex.partialResult().registered().size());
        PlannedArtifact child = ex.partialResult().registered().keySet().iterator().next();
        assertEquals("Common", child.coordinates().artifactId());
        assertEquals("1", ex.partialResult().registered().get(child).getVersion());
        assertEquals("Failed to register artifact [g] / [R]: HTTP 409 Artifact already exists - g/R exists",
                ex.getMessage());
        assertEquals("FAIL", server.findAll(postRequestedFor(urlPathEqualTo(ROOT_ARTIFACTS))).get(0)
                .queryParameter("ifExists").firstValue());
    }

    @Test
    void draftConflictUpdatesContentAndReturnsVersion() {
        stubRootConflict();
        stubContentPut("1.0");
        stubVersionGet("1.0", "DRAFT");

        RegistrationResult result = ReferenceRegistrar.builder(client).build().execute(avroRoot(true, "1.0"));

        assertEquals("1.0", result.roots().get(0).getVersion());
        assertEquals(List.of("POST " + ROOT_ARTIFACTS, "PUT " + ROOT_ARTIFACTS + "/R/versions/1.0/content",
                "GET " + ROOT_ARTIFACTS + "/R/versions/1.0"), requestLog());
    }

    @Test
    void apiInfoVersionConflictOnDraftPromotesIt() throws Exception {
        stubRootConflict();
        stubVersionGet("2.0", "DRAFT");
        stubContentPut("2.0");
        server.stubFor(put(urlPathEqualTo(ROOT_ARTIFACTS + "/R/versions/2.0/state"))
                .willReturn(aResponse().withStatus(204)));

        RegistrationResult result = ReferenceRegistrar.builder(client)
                .versionStrategy(VersionStrategy.apiInfoVersion()).build().execute(openApiRoot("2.0"));

        assertEquals("2.0", result.roots().get(0).getVersion());
        assertEquals(List.of("POST " + ROOT_ARTIFACTS, "GET " + ROOT_ARTIFACTS + "/R/versions/2.0",
                "PUT " + ROOT_ARTIFACTS + "/R/versions/2.0/content",
                "PUT " + ROOT_ARTIFACTS + "/R/versions/2.0/state", "GET " + ROOT_ARTIFACTS + "/R/versions/2.0"),
                requestLog());
        server.verify(putRequestedFor(urlPathEqualTo(ROOT_ARTIFACTS + "/R/versions/2.0/state"))
                .withRequestBody(equalToJson("{\"state\":\"ENABLED\"}")));
        assertEquals("openapi: 3.0.3", MAPPER.readTree(server.findAll(
                putRequestedFor(urlPathEqualTo(ROOT_ARTIFACTS + "/R/versions/2.0/content"))).get(0)
                .getBodyAsString()).get("content").asText().lines().findFirst().orElseThrow());
    }

    @Test
    void apiInfoVersionConflictOnEnabledVersionThrows() {
        stubRootConflict();
        stubVersionGet("2.0", "ENABLED");

        RegistrationException ex = assertThrows(RegistrationException.class, () -> ReferenceRegistrar
                .builder(client).versionStrategy(VersionStrategy.apiInfoVersion()).build()
                .execute(openApiRoot("2.0")));

        assertEquals(409, ex.problem().getResponseStatusCode());
        assertEquals(0, ex.partialResult().registered().size());
        assertEquals(List.of("POST " + ROOT_ARTIFACTS, "GET " + ROOT_ARTIFACTS + "/R/versions/2.0"),
                requestLog());
    }

    @Test
    void apiInfoSnapshotPostsDraftWithoutSuffix() throws Exception {
        stubJson(post(urlPathEqualTo(ROOT_ARTIFACTS)), 200,
                "{\"version\":{\"groupId\":\"g\",\"artifactId\":\"R\",\"version\":\"3.0\"}}");

        ReferenceRegistrar.builder(client).versionStrategy(VersionStrategy.apiInfoVersion()).build()
                .execute(openApiRoot("3.0-SNAPSHOT"));

        JsonNode firstVersion = MAPPER.readTree(server.findAll(postRequestedFor(urlPathEqualTo(ROOT_ARTIFACTS))).get(0)
                .getBodyAsString()).get("firstVersion");
        assertEquals("3.0", firstVersion.get("version").asText());
        assertTrue(firstVersion.get("isDraft").asBoolean());
    }

    @Test
    void serverErrorWithoutProblemIsWrapped() {
        server.stubFor(post(urlPathEqualTo(ROOT_ARTIFACTS)).willReturn(aResponse().withStatus(503)));

        RegistrationException ex = assertThrows(RegistrationException.class,
                () -> ReferenceRegistrar.builder(client).build().execute(avroRoot(null, null)));

        assertNull(ex.problem());
        assertEquals("Failed to register artifact [g] / [R]", ex.getMessage());
        assertNotNull(ex.getCause());
    }

    @Test
    void interruptDuringRequestPropagates() throws Exception {
        server.stubFor(post(urlPathEqualTo(ROOT_ARTIFACTS)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withFixedDelay(30_000)
                .withBody("{\"version\":{\"groupId\":\"g\",\"artifactId\":\"R\",\"version\":\"1\"}}")));
        RegistrationPlan plan = avroRoot(null, null);
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicBoolean interruptedAfter = new AtomicBoolean();
        Thread worker = new Thread(() -> {
            try {
                ReferenceRegistrar.builder(client).build().execute(plan);
            } catch (Throwable t) {
                thrown.set(t);
            }
            interruptedAfter.set(Thread.currentThread().isInterrupted());
        });

        long start = System.nanoTime();
        worker.start();
        await().atMost(Duration.ofSeconds(10)).until(() -> !server.getAllServeEvents().isEmpty());
        worker.interrupt();
        worker.join(15_000);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertFalse(worker.isAlive());
        RegistrationException ex = assertInstanceOf(RegistrationException.class, thrown.get());
        assertInstanceOf(InterruptedException.class, ex.getCause());
        assertEquals("Interrupted while registering artifact [g] / [R]", ex.getMessage());
        assertTrue(interruptedAfter.get());
        // generous bound: the stub delays 30s, so finishing well before that proves the interrupt aborted it
        assertTrue(elapsedMs < 15_000, "execute should abort long before the 30s delay, took " + elapsedMs);
        assertEquals(1, server.getAllServeEvents().size());
    }
}
