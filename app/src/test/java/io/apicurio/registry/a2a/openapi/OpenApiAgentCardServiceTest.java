package io.apicurio.registry.a2a.openapi;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.json.content.canon.JsonContentCanonicalizer;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class OpenApiAgentCardServiceTest {

    private static final String GROUP_ID = "my-group";
    private static final String OPENAPI_ARTIFACT_ID = "weather-api";
    private static final String COMPANION_ARTIFACT_ID = "weather-api-agent-card";

    private static final String OPENAPI_WITH_CARD = """
            {
              "openapi": "3.0.0",
              "info": {
                "title": "Weather API",
                "description": "A weather service",
                "version": "1.0.0",
                "x-agent-card": {
                  "capabilities": {},
                  "skills": [
                    { "id": "get-weather", "name": "Get Weather",
                      "description": "Retrieve the current weather for a city", "tags": ["weather"] }
                  ],
                  "defaultInputModes": ["text"],
                  "defaultOutputModes": ["text"]
                }
              },
              "servers": [ { "url": "https://weather.example.com" } ],
              "paths": {}
            }
            """;

    private static final String OPENAPI_NO_CARD = """
            {
              "openapi": "3.0.0",
              "info": { "title": "Weather API", "version": "1.0.0" },
              "paths": {}
            }
            """;

    private RegistryStorage storage;
    private OpenApiAgentCardService service;
    private OpenApiAgentCardConfig config;

    @BeforeEach
    void setUp() {
        storage = mock(RegistryStorage.class);

        config = new OpenApiAgentCardConfig();
        config.enabled = true;
        config.syncOnUpdateEnabled = true;

        service = new OpenApiAgentCardService();
        service.log = LoggerFactory.getLogger(OpenApiAgentCardServiceTest.class);
        service.config = config;
    }

    private TypedContent openApiContent(String json) {
        return TypedContent.create(json, ContentTypes.APPLICATION_JSON);
    }

    private void sync(String openApiJson, boolean isUpdate) {
        String assembled = service.validateAndAssemble(openApiContent(openApiJson), isUpdate);
        if (assembled != null) {
            service.createOrSyncCompanion(storage, GROUP_ID, OPENAPI_ARTIFACT_ID, assembled, "alice");
        }
    }

    @Test
    void featureDisabled_doesNothing() {
        config.enabled = false;
        sync(OPENAPI_WITH_CARD, false);
        verifyNoStorageWrites();
    }

    @Test
    void syncOnUpdateDisabled_skipsOnUpdate() {
        config.syncOnUpdateEnabled = false;
        sync(OPENAPI_WITH_CARD, true);
        verifyNoStorageWrites();
    }

    @Test
    void syncOnUpdateDisabled_stillRunsOnCreate() {
        config.syncOnUpdateEnabled = false;
        when(storage.getArtifactMetaData(GROUP_ID, COMPANION_ARTIFACT_ID))
                .thenThrow(new ArtifactNotFoundException(GROUP_ID, COMPANION_ARTIFACT_ID));

        sync(OPENAPI_WITH_CARD, false);

        verify(storage, times(1)).createArtifact(eq(GROUP_ID), eq(COMPANION_ARTIFACT_ID),
                eq(ArtifactType.AGENT_CARD), any(), eq("1"), any(), any(), any(), eq(false), eq(false),
                eq("alice"));
    }

    @Test
    void noExtension_doesNothing() {
        sync(OPENAPI_NO_CARD, false);
        verifyNoStorageWrites();
    }

    @Test
    void malformedExtension_throwsBeforeTouchingStorageAtAll() {
        String malformed = """
                {
                  "openapi": "3.0.0",
                  "info": {
                    "title": "Weather API",
                    "version": "1.0.0",
                    "x-agent-card": { "capabilities": {} }
                  },
                  "paths": {}
                }
                """;

        assertThrows(RuleViolationException.class,
                () -> service.validateAndAssemble(openApiContent(malformed), false));

        // validateAndAssemble is called BEFORE the OpenAPI write, so it must never touch storage -
        // createOrSyncCompanion is a separate call the caller only makes after a successful write.
        verifyNoStorageWrites();
    }

    @Test
    void companionMissing_createsIt() {
        when(storage.getArtifactMetaData(GROUP_ID, COMPANION_ARTIFACT_ID))
                .thenThrow(new ArtifactNotFoundException(GROUP_ID, COMPANION_ARTIFACT_ID));

        sync(OPENAPI_WITH_CARD, false);

        verify(storage, times(1)).createArtifact(eq(GROUP_ID), eq(COMPANION_ARTIFACT_ID),
                eq(ArtifactType.AGENT_CARD), any(), eq("1"), any(), any(), any(), eq(false), eq(false),
                eq("alice"));
        // Source is marked with a pointer to the generated companion.
        verify(storage, times(1)).mergeArtifactLabels(eq(GROUP_ID), eq(OPENAPI_ARTIFACT_ID),
                eq("apicurio.a2a.openapi-agent-card."),
                eq(Map.of("apicurio.a2a.openapi-agent-card.artifact-id", COMPANION_ARTIFACT_ID)));
        verify(storage, never()).createArtifactVersion(anyString(), anyString(), any(), anyString(), any(),
                any(), any(), anyBoolean(), anyBoolean(), anyString());
    }

    @Test
    void companionNotOurs_skipsWithoutTouchingIt() {
        ArtifactMetaDataDto foreignCompanion = ArtifactMetaDataDto.builder().labels(Map.of()).build();
        when(storage.getArtifactMetaData(GROUP_ID, COMPANION_ARTIFACT_ID)).thenReturn(foreignCompanion);

        sync(OPENAPI_WITH_CARD, false);

        verifyNoStorageWrites();
    }

    @Test
    void companionOursAndUpToDate_doesNothing() {
        // Simulate a prior generation: record the actual hash the assembler will produce for
        // OPENAPI_WITH_CARD, and make the "current latest version content" match it exactly.
        String assembledJson = assemble(OPENAPI_WITH_CARD);
        String hash = canonicalHash(assembledJson);

        mockExistingGeneratedCompanion(hash, assembledJson);

        sync(OPENAPI_WITH_CARD, false);

        verify(storage, never()).createArtifactVersion(anyString(), anyString(), any(), anyString(), any(),
                any(), any(), anyBoolean(), anyBoolean(), anyString());
        verify(storage, never()).createArtifact(anyString(), anyString(), anyString(), any(), anyString(),
                any(), any(), any(), anyBoolean(), anyBoolean(), anyString());
    }

    @Test
    void companionOursAndStale_createsNewVersion() {
        // Recorded hash matches the *previously* generated content (so we know it wasn't hand-edited),
        // but that content differs from what today's OpenAPI (version 1.0.0) now assembles to.
        String previouslyGenerated = assemble(openApiWithVersion("0.9.0"));
        String previousHash = canonicalHash(previouslyGenerated);
        mockExistingGeneratedCompanion(previousHash, previouslyGenerated);

        sync(OPENAPI_WITH_CARD, false);

        verify(storage, times(1)).createArtifactVersion(eq(GROUP_ID), eq(COMPANION_ARTIFACT_ID), eq(null),
                eq(ArtifactType.AGENT_CARD), any(), any(), any(), eq(false), eq(false), eq("alice"));
        verify(storage, times(1)).mergeArtifactLabels(eq(GROUP_ID), eq(COMPANION_ARTIFACT_ID),
                eq("apicurio.a2a.openapi-agent-card."), any());
    }

    @Test
    void companionOursButManuallyEdited_skipsSync() {
        // Recorded hash reflects what we last generated, but the *actual* latest version content
        // (what fetchLatestVersionContent returns) no longer matches it - someone edited it directly.
        String weGenerated = assemble(openApiWithVersion("1.0.0"));
        String recordedHash = canonicalHash(weGenerated);
        String handEdited = assemble(openApiWithVersion("9.9.9-hand-edited"));

        mockExistingGeneratedCompanion(recordedHash, handEdited);

        sync(OPENAPI_WITH_CARD, false);

        verify(storage, never()).createArtifactVersion(anyString(), anyString(), any(), anyString(), any(),
                any(), any(), anyBoolean(), anyBoolean(), anyString());
    }

    // --- helpers ---

    private String assemble(String openApiJson) {
        try {
            return new OpenApiAgentCardAssembler().assemble(openApiContent(openApiJson));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String openApiWithVersion(String version) {
        return """
                {
                  "openapi": "3.0.0",
                  "info": {
                    "title": "Weather API",
                    "description": "A weather service",
                    "version": "%s",
                    "x-agent-card": {
                      "capabilities": {},
                      "skills": [
                        { "id": "get-weather", "name": "Get Weather",
                          "description": "Retrieve the current weather for a city", "tags": ["weather"] }
                      ],
                      "defaultInputModes": ["text"],
                      "defaultOutputModes": ["text"]
                    }
                  },
                  "servers": [ { "url": "https://weather.example.com" } ],
                  "paths": {}
                }
                """.formatted(version);
    }

    private String canonicalHash(String json) {
        // Mirror OpenApiAgentCardService's own hashing so test expectations stay in lockstep with it.
        JsonContentCanonicalizer canonicalizer = new JsonContentCanonicalizer();
        TypedContent canonical = canonicalizer.canonicalize(
                TypedContent.create(json, ContentTypes.APPLICATION_JSON), Collections.emptyMap());
        return DigestUtils.sha256Hex(canonical.getContent().bytes());
    }

    private void mockExistingGeneratedCompanion(String recordedHash, String latestVersionContent) {
        Map<String, String> labels = new HashMap<>();
        labels.put("apicurio.a2a.openapi-agent-card.generated", "true");
        labels.put("apicurio.a2a.openapi-agent-card.source-group-id", GROUP_ID);
        labels.put("apicurio.a2a.openapi-agent-card.source-artifact-id", OPENAPI_ARTIFACT_ID);
        labels.put("apicurio.a2a.openapi-agent-card.generated-hash", recordedHash);
        ArtifactMetaDataDto existing = ArtifactMetaDataDto.builder().labels(labels).build();
        when(storage.getArtifactMetaData(GROUP_ID, COMPANION_ARTIFACT_ID)).thenReturn(existing);

        GAV latestGav = new GAV(new GA(GROUP_ID, COMPANION_ARTIFACT_ID), "1");
        when(storage.getBranchTip(any(GA.class), any(BranchId.class), any())).thenReturn(latestGav);
        StoredArtifactVersionDto stored = StoredArtifactVersionDto.builder()
                .content(ContentHandle.create(latestVersionContent)).build();
        when(storage.getArtifactVersionContent(GROUP_ID, COMPANION_ARTIFACT_ID, "1")).thenReturn(stored);
    }

    private void verifyNoStorageWrites() {
        verify(storage, never()).createArtifact(anyString(), anyString(), anyString(), any(), anyString(),
                any(), any(), any(), anyBoolean(), anyBoolean(), anyString());
        verify(storage, never()).createArtifactVersion(anyString(), anyString(), any(), anyString(), any(),
                any(), any(), anyBoolean(), anyBoolean(), anyString());
        verify(storage, never()).mergeArtifactLabels(anyString(), anyString(), anyString(), any());
    }
}
