package io.apicurio.registry.client.refs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.content.refs.resolve.ExistingArtifact;
import io.apicurio.registry.content.refs.resolve.InMemorySchemaSource;
import io.apicurio.registry.content.refs.resolve.PlanOptions;
import io.apicurio.registry.content.refs.resolve.PlannedArtifact;
import io.apicurio.registry.content.refs.resolve.ReferenceResolver;
import io.apicurio.registry.content.refs.resolve.RegistrationPlan;
import io.apicurio.registry.content.refs.resolve.RootArtifact;
import io.apicurio.registry.content.refs.resolve.SchemaSource;
import io.apicurio.registry.content.refs.resolve.SchemaSourceSet;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReferenceRegistrarTest {

    private static final String REC = "{\"type\":\"record\",\"name\":\"%s\",\"namespace\":\"%s\",\"fields\":[%s]}";
    private static final String REF_FIELD = "{\"name\":\"x\",\"type\":\"c.Common\"}";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WireMockServer server;
    private RegistryClient client;

    @BeforeEach
    void setup() {
        server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        server.start();
        stubCreate("c", "Common", "1");
        stubCreate("g", "A", "3");
        client = RegistryClientFactory.create(RegistryClientOptions.create(server.baseUrl() + "/apis/registry/v3"));
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private void stubCreate(String groupId, String artifactId, String version) {
        server.stubFor(post(urlPathEqualTo("/apis/registry/v3/groups/" + groupId + "/artifacts"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"version":{"groupId":"%s","artifactId":"%s","version":"%s","globalId":1},
                                 "artifact":{"groupId":"%s","artifactId":"%s"}}
                                """.formatted(groupId, artifactId, version, groupId, artifactId))));
    }

    private static SchemaSource avro(String loc, String name, String ns, String fields) {
        return InMemorySchemaSource.of(loc, REC.formatted(name, ns, fields), ContentTypes.APPLICATION_JSON);
    }

    private List<LoggedRequest> posts() {
        List<LoggedRequest> requests = new ArrayList<>(server.findAll(postRequestedFor(anyUrl())));
        requests.sort(Comparator.comparing(LoggedRequest::getLoggedDate));
        return requests;
    }

    private static JsonNode body(LoggedRequest request) throws Exception {
        return MAPPER.readTree(request.getBodyAsString());
    }

    private RegistrationPlan commonAndA(PlanOptions options, SchemaSource... extra) {
        List<SchemaSource> sources = new ArrayList<>(List.of(avro("A.avsc", "A", "a", REF_FIELD)));
        sources.addAll(List.of(extra));
        return ReferenceResolver.plan(List.of(RootArtifact.of("A.avsc", "g", "A")),
                SchemaSourceSet.of(sources.toArray(new SchemaSource[0])), options);
    }

    @Test
    void registersDependenciesFirstAndWiresReferences() throws Exception {
        RegistrationPlan plan = commonAndA(PlanOptions.builder().build(), avro("Common.avsc", "Common", "c", ""));

        RegistrationResult result = ReferenceRegistrar.builder(client)
                .ifExists(IfArtifactExists.FIND_OR_CREATE_VERSION).canonical(true).build().execute(plan);

        List<LoggedRequest> posts = posts();
        assertEquals(2, posts.size());
        assertEquals("/apis/registry/v3/groups/c/artifacts", posts.get(0).getUrl().split("\\?")[0]);
        assertEquals("Common", body(posts.get(0)).get("artifactId").asText());
        assertEquals("/apis/registry/v3/groups/g/artifacts", posts.get(1).getUrl().split("\\?")[0]);
        assertEquals(MAPPER.readTree("[{\"name\":\"c.Common\",\"groupId\":\"c\",\"artifactId\":\"Common\","
                + "\"version\":\"1\"}]"), body(posts.get(1)).at("/firstVersion/content/references"));
        for (LoggedRequest post : posts) {
            assertEquals("FIND_OR_CREATE_VERSION", post.queryParameter("ifExists").firstValue());
            assertEquals("true", post.queryParameter("canonical").firstValue());
            assertFalse(post.queryParameter("dryRun").isPresent());
        }
        assertEquals("A", result.roots().get(0).getArtifactId());
        assertEquals(1, result.roots().size());
        assertEquals(2, result.registered().size());
        assertFalse(result.isDryRun());
    }

    @Test
    void dryRunSendsFlagOnEveryRequest() throws Exception {
        RegistrationPlan plan = commonAndA(PlanOptions.builder().build(), avro("Common.avsc", "Common", "c", ""));

        RegistrationResult result = ReferenceRegistrar.builder(client).dryRun(true).build().execute(plan);

        List<LoggedRequest> posts = posts();
        assertEquals(2, posts.size());
        for (LoggedRequest post : posts) {
            assertEquals("true", post.queryParameter("dryRun").firstValue());
            assertFalse(post.queryParameter("ifExists").isPresent());
        }
        assertEquals("1", body(posts.get(1)).at("/firstVersion/content/references/0/version").asText());
        assertTrue(result.isDryRun());
    }

    @Test
    void existingArtifactWithoutVersionUsesMemoizedLatest() throws Exception {
        server.stubFor(get(urlPathMatching("/apis/registry/v3/groups/c/artifacts/Common/versions/branch.*latest"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"groupId\":\"c\",\"artifactId\":\"Common\",\"version\":\"7\"}")));
        PlanOptions options = PlanOptions.builder()
                .existingArtifacts(List.of(new ExistingArtifact("c.Common", "c", "Common", null))).build();
        RegistrationPlan plan = ReferenceResolver.plan(
                List.of(RootArtifact.of("A.avsc", "g", "A"), RootArtifact.of("B.avsc", "g", "B")),
                SchemaSourceSet.of(avro("A.avsc", "A", "a", REF_FIELD), avro("B.avsc", "B", "b", REF_FIELD)),
                options);

        ReferenceRegistrar.builder(client).build().execute(plan);

        assertEquals(1, server.findAll(getRequestedFor(anyUrl())).size());
        List<LoggedRequest> posts = posts();
        assertEquals(2, posts.size());
        for (LoggedRequest post : posts) {
            assertEquals(MAPPER.readTree("[{\"name\":\"c.Common\",\"groupId\":\"c\",\"artifactId\":\"Common\","
                    + "\"version\":\"7\"}]"), body(post).at("/firstVersion/content/references"));
        }
    }

    @Test
    void planWarningsAreReportedOnce() {
        SchemaSource s = InMemorySchemaSource.of("s.json", """
                {"$schema":"https://json-schema.org/draft/2020-12/schema","properties":{
                 "a":{"$ref":"https://other.example/x.json"}}}
                """, ContentTypes.APPLICATION_JSON);
        server.stubFor(post(urlPathEqualTo("/apis/registry/v3/groups/g/artifacts"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"version\":{\"groupId\":\"g\",\"artifactId\":\"S\",\"version\":\"1\"}}")));
        RegistrationPlan plan = ReferenceResolver.plan(List.of(RootArtifact.of("s.json", "g", "S")),
                SchemaSourceSet.of(s), PlanOptions.builder().build());
        List<String> warnings = new ArrayList<>();
        List<String> registered = new ArrayList<>();

        ReferenceRegistrar.builder(client).listener(new RegistrationListener() {
            @Override
            public void onWarning(String message) {
                warnings.add(message);
            }

            @Override
            public void onRegistered(PlannedArtifact artifact, VersionMetaData result) {
                registered.add(artifact.coordinates().artifactId() + "@" + result.getVersion());
            }
        }).build().execute(plan);

        assertEquals(List.of("Skipping external reference not managed by Apicurio Registry: "
                + "https://other.example/x.json"), warnings);
        assertEquals(List.of("S@1"), registered);
        assertEquals(1, server.findAll(anyRequestedFor(anyUrl())).size());
    }

    @Test
    void minifyCompactsJsonContent() throws Exception {
        SchemaSource common = InMemorySchemaSource.of("Common.avsc",
                "{\n  \"type\" : \"record\",\n  \"name\" : \"Common\",\n  \"namespace\" : \"c\",\n  \"fields\" : [ ]\n}",
                ContentTypes.APPLICATION_JSON);
        RegistrationPlan plan = commonAndA(PlanOptions.builder().build(), common);

        ReferenceRegistrar.builder(client).minify(true).build().execute(plan);

        assertEquals("{\"type\":\"record\",\"name\":\"Common\",\"namespace\":\"c\",\"fields\":[]}",
                body(posts().get(0)).at("/firstVersion/content/content").asText());
    }

    @Test
    void minifyLeavesYamlContentUnchanged() throws Exception {
        server.stubFor(post(urlPathEqualTo("/apis/registry/v3/groups/g/artifacts"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"version\":{\"groupId\":\"g\",\"artifactId\":\"R\",\"version\":\"1\"}}")));
        String yaml = openApiYaml("1.0.0");
        RegistrationPlan plan = ReferenceResolver.plan(List.of(RootArtifact.of("api.yaml", "g", "R")),
                SchemaSourceSet.of(InMemorySchemaSource.of("api.yaml", yaml, ContentTypes.APPLICATION_YAML)),
                PlanOptions.builder().build());

        ReferenceRegistrar.builder(client).minify(true).build().execute(plan);

        JsonNode content = body(posts().get(0)).at("/firstVersion/content");
        assertEquals(yaml, content.get("content").asText());
        assertEquals(ContentTypes.APPLICATION_YAML, content.get("contentType").asText());
    }

    private JsonNode registerApiRoot(String location, String content, String contentType, String artifactType,
            String explicitVersion) throws Exception {
        server.stubFor(post(urlPathEqualTo("/apis/registry/v3/groups/g/artifacts"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"version\":{\"groupId\":\"g\",\"artifactId\":\"R\",\"version\":\"1\"}}")));
        RegistrationPlan plan = ReferenceResolver.plan(
                List.of(new RootArtifact(location, "g", "R", null, null, explicitVersion, null)),
                SchemaSourceSet.of(InMemorySchemaSource.of(location, content, contentType, artifactType)),
                PlanOptions.builder().build());

        ReferenceRegistrar.builder(client).versionStrategy(VersionStrategy.apiInfoVersion()).build().execute(plan);

        List<LoggedRequest> posts = posts();
        assertEquals(1, posts.size());
        return body(posts.get(0)).get("firstVersion");
    }

    private static String openApiYaml(String version) {
        return "openapi: 3.0.3\ninfo:\n  title: T\n  version: \"" + version + "\"\npaths: {}\n";
    }

    @Test
    void apiInfoVersionUsesInfoVersionFromYaml() throws Exception {
        JsonNode firstVersion = registerApiRoot("api.yaml", openApiYaml("2.0"), ContentTypes.APPLICATION_YAML,
                ArtifactType.OPENAPI, null);

        assertEquals("2.0", firstVersion.get("version").asText());
        assertFalse(firstVersion.path("isDraft").asBoolean(false));
    }

    @Test
    void apiInfoVersionSnapshotBecomesDraft() throws Exception {
        JsonNode firstVersion = registerApiRoot("api.yaml", openApiYaml("3.0-SNAPSHOT"),
                ContentTypes.APPLICATION_YAML, ArtifactType.OPENAPI, null);

        assertEquals("3.0", firstVersion.get("version").asText());
        assertTrue(firstVersion.get("isDraft").asBoolean());
    }

    @Test
    void apiInfoVersionExplicitRootVersionWins() throws Exception {
        JsonNode firstVersion = registerApiRoot("api.yaml", openApiYaml("2.0"), ContentTypes.APPLICATION_YAML,
                ArtifactType.OPENAPI, "9");

        assertEquals("9", firstVersion.get("version").asText());
    }

    @Test
    void apiInfoVersionIgnoresNonApiArtifacts() throws Exception {
        JsonNode firstVersion = registerApiRoot("R.avsc", REC.formatted("R", "r", ""),
                ContentTypes.APPLICATION_JSON, ArtifactType.AVRO, null);

        assertTrue(firstVersion.path("version").isMissingNode() || firstVersion.get("version").isNull());
    }
}
