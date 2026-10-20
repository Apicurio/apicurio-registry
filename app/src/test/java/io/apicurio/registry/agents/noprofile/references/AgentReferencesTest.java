package io.apicurio.registry.agents.noprofile.references;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.ReferenceType;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.client.models.VersionState;
import io.apicurio.registry.rest.client.models.WrappedVersionState;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * References between AI artifact types (AGENT_CARD, MCP_SERVER, MCP_TOOL) under the INTEGRITY rule.
 */
@QuarkusTest
public class AgentReferencesTest extends AbstractResourceTestBase {

    private static final String TOOL = """
            {
                "name": "%s",
                "description": "A test tool",
                "inputSchema": { "type": "object", "properties": { "city": { "type": "string" } } }
            }
            """;

    private static final String SERVER = """
            {
                "name": "io.github.example/weather",
                "version": "1.0.0",
                "description": "%s",
                "remotes": [ { "type": "streamable-http", "url": "https://example.com/mcp" } ]
            }
            """;

    private static final String AGENT = """
            {
                "name": "%s",
                "description": "A test agent",
                "version": "1.0.0",
                "supportedInterfaces": [
                    { "url": "https://example.com/agent", "protocolBinding": "JSONRPC", "protocolVersion": "1.0" }
                ],
                "capabilities": {},
                "skills": [ { "id": "s", "name": "S", "description": "A skill", "tags": ["t"] } ],
                "defaultInputModes": ["text"],
                "defaultOutputModes": ["text"]
            }
            """;

    private static final String MODEL = """
            {
                "modelId": "test-model",
                "input": { "type": "object", "properties": { "prompt": { "type": "string" } } },
                "output": { "type": "object", "properties": { "text": { "type": "string" } } }
            }
            """;

    private static final String PROMPT = """
            {"templateId":"t","template":"Hello {{name}}","variables":[{"name":"name","type":"string"}]}
            """;

    private static final String AVRO = """
            {"type":"record","name":"Rec","fields":[{"name":"id","type":"string"}]}
            """;

    @Inject
    @Current
    RegistryStorage storage;

    private String groupId;

    @BeforeEach
    void createGroup() {
        groupId = TestUtils.generateGroupId();
        CreateGroup group = new CreateGroup();
        group.setGroupId(groupId);
        clientV3.groups().post(group);
    }

    private void useIntegrityRule(IntegrityLevel level) {
        CreateRule rule = new CreateRule();
        rule.setRuleType(RuleType.INTEGRITY);
        rule.setConfig(level.name());
        clientV3.groups().byGroupId(groupId).rules().post(rule);
    }

    @Test
    void agentAndServerReferencesToAllowedTypesAreAccepted() {
        useIntegrityRule(IntegrityLevel.REFS_EXIST);
        create("tool", ArtifactType.MCP_TOOL, TOOL.formatted("get_weather"));
        create("server", ArtifactType.MCP_SERVER, SERVER.formatted("weather"), ref("tool", "get_weather"));
        create("model", ArtifactType.MODEL_SCHEMA, MODEL);
        create("prompt", ArtifactType.PROMPT_TEMPLATE, PROMPT);
        create("peer", ArtifactType.AGENT_CARD, AGENT.formatted("Peer"));

        create("agent", ArtifactType.AGENT_CARD, AGENT.formatted("Agent"), ref("tool", "get_weather"),
                ref("server", "weather"), ref("model", "test-model"), ref("prompt", "greeting"),
                ref("peer", "peer"));

        assertEquals(Set.of("tool", "server", "model", "prompt", "peer"),
                artifactIds(references("agent", ReferenceType.OUTBOUND)));
        assertEquals(Set.of("server", "agent"), artifactIds(references("tool", ReferenceType.INBOUND)));
    }

    @ParameterizedTest
    @EnumSource(value = IntegrityLevel.class, names = { "REFS_EXIST", "FULL" })
    void referencesToDisallowedTypesAreRejected(IntegrityLevel level) {
        create("tool", ArtifactType.MCP_TOOL, TOOL.formatted("get_weather"));
        create("agent", ArtifactType.AGENT_CARD, AGENT.formatted("Agent"));
        create("schema", ArtifactType.AVRO, AVRO);
        useIntegrityRule(level);

        assertRejected("server", ArtifactType.MCP_SERVER, SERVER.formatted("weather"), "agent",
                ref("tool", "get_weather"), ref("agent", "agent"));
        assertRejected("other-agent", ArtifactType.AGENT_CARD, AGENT.formatted("Other"), "schema",
                ref("schema", "schema"));
        assertRejected("other-tool", ArtifactType.MCP_TOOL, TOOL.formatted("other"), "get_weather",
                ref("tool", "get_weather"));
    }

    @Test
    void referenceTypesAreNotCheckedWithoutAnIntegrityRule() {
        create("agent", ArtifactType.AGENT_CARD, AGENT.formatted("Agent"));

        create("server", ArtifactType.MCP_SERVER, SERVER.formatted("weather"), ref("agent", "agent"));

        assertEquals(Set.of("agent"), artifactIds(references("server", ReferenceType.OUTBOUND)));
    }

    @Test
    void referenceToMissingToolIsRejected() {
        useIntegrityRule(IntegrityLevel.REFS_EXIST);
        var error = assertThrows(RuleViolationProblemDetails.class, () -> create("agent",
                ArtifactType.AGENT_CARD, AGENT.formatted("Agent"), ref("missing-tool", "get_weather")));
        assertEquals(400, error.getStatus());
        assertEquals(List.of("get_weather"),
                error.getCauses().stream().map(c -> c.getContext()).collect(Collectors.toList()));
        assertArtifactAbsent("agent");
    }

    @Test
    void changedToolReferencesNeedANewServerVersion() {
        useIntegrityRule(IntegrityLevel.REFS_EXIST);
        create("tool-a", ArtifactType.MCP_TOOL, TOOL.formatted("a"));
        create("tool-b", ArtifactType.MCP_TOOL, TOOL.formatted("b"));
        create("server", ArtifactType.MCP_SERVER, SERVER.formatted("weather"), ref("tool-a", "a"));

        // Same server.json, different tool inventory: reusing the existing version is rejected...
        var error = assertThrows(ProblemDetails.class, () -> addVersion("server", "1",
                SERVER.formatted("weather"), ref("tool-a", "a"), ref("tool-b", "b")));
        assertEquals(409, error.getStatus());
        assertEquals("VersionAlreadyExistsException", error.getName());

        // ...a new version is created...
        addVersion("server", null, SERVER.formatted("weather"), ref("tool-a", "a"), ref("tool-b", "b"));
        assertEquals(Set.of("tool-a", "tool-b"), artifactIds(references("server", "2", ReferenceType.OUTBOUND)));

        // ...and the existing version keeps its references.
        assertEquals(Set.of("tool-a"), artifactIds(references("server", "1", ReferenceType.OUTBOUND)));
    }

    /**
     * Retiring a referenced tool does not touch the agents that reference it: disabling it leaves the
     * reference in place, and deleting it leaves a dangling reference on the agent version.
     */
    @Test
    void retiringAReferencedToolKeepsTheAgentReference() {
        useIntegrityRule(IntegrityLevel.REFS_EXIST);
        create("tool", ArtifactType.MCP_TOOL, TOOL.formatted("get_weather"));
        create("agent", ArtifactType.AGENT_CARD, AGENT.formatted("Agent"), ref("tool", "get_weather"));

        WrappedVersionState disabled = new WrappedVersionState();
        disabled.setState(VersionState.DISABLED);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId("tool").versions()
                .byVersionExpression("1").state().put(disabled);
        assertEquals(Set.of("tool"), artifactIds(references("agent", ReferenceType.OUTBOUND)));

        // REST deletion is disabled by default; delete through storage rather than toggle shared config.
        storage.deleteArtifact(groupId, "tool");
        assertEquals(Set.of("tool"), artifactIds(references("agent", ReferenceType.OUTBOUND)));
    }

    private ArtifactReference ref(String artifactId, String name) {
        ArtifactReference ref = new ArtifactReference();
        ref.setGroupId(groupId);
        ref.setArtifactId(artifactId);
        ref.setVersion("1");
        ref.setName(name);
        return ref;
    }

    private void create(String artifactId, String type, String content, ArtifactReference... refs) {
        CreateArtifact artifact = new CreateArtifact();
        artifact.setArtifactId(artifactId);
        artifact.setArtifactType(type);
        artifact.setFirstVersion(version("1", content, refs));
        clientV3.groups().byGroupId(groupId).artifacts().post(artifact);
    }

    private void addVersion(String artifactId, String version, String content, ArtifactReference... refs) {
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(version(version, content, refs));
    }

    private static CreateVersion version(String version, String content, ArtifactReference... refs) {
        VersionContent versionContent = new VersionContent();
        versionContent.setContent(content);
        versionContent.setContentType(ContentTypes.APPLICATION_JSON);
        versionContent.setReferences(Arrays.asList(refs));
        CreateVersion createVersion = new CreateVersion();
        createVersion.setVersion(version);
        createVersion.setContent(versionContent);
        return createVersion;
    }

    private void assertRejected(String artifactId, String type, String content, String offendingRef,
            ArtifactReference... refs) {
        var error = assertThrows(RuleViolationProblemDetails.class, () -> create(artifactId, type, content, refs));
        assertEquals(400, error.getStatus());
        assertEquals(List.of(offendingRef),
                error.getCauses().stream().map(c -> c.getContext()).collect(Collectors.toList()));
        assertArtifactAbsent(artifactId);
    }

    private void assertArtifactAbsent(String artifactId) {
        var error = assertThrows(ProblemDetails.class,
                () -> clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get());
        assertEquals(404, error.getStatus());
    }

    private List<ArtifactReference> references(String artifactId, ReferenceType refType) {
        return references(artifactId, "1", refType);
    }

    private List<ArtifactReference> references(String artifactId, String version, ReferenceType refType) {
        return clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression(version).references().get(config -> config.queryParameters.refType = refType);
    }

    private static Set<String> artifactIds(List<ArtifactReference> references) {
        return references.stream().map(ArtifactReference::getArtifactId).collect(Collectors.toSet());
    }
}
