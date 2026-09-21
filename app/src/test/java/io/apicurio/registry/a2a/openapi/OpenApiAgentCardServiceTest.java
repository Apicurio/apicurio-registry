package io.apicurio.registry.a2a.openapi;

import io.apicurio.registry.a2a.A2AConstants;
import io.apicurio.registry.auth.AuthConfig;
import io.apicurio.registry.auth.AdminOverride;
import io.apicurio.registry.auth.RoleBasedAccessController;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.json.content.canon.JsonContentCanonicalizer;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.rules.RulesService;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.error.CommitFailedException;
import io.apicurio.registry.rules.RuleApplicationType;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.VersionState;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenApiAgentCardServiceTest {
    private static final String GROUP = "group";
    private static final String SOURCE = "weather";
    private static final String CARD = "weather-agent-card";
    private RegistryStorage storage;
    private OpenApiAgentCardService service;
    private OpenApiAgentCardConfig config;

    private String openApi(String title) {
        return """
                {"openapi":"3.0.0","info":{"title":"%s","description":"Weather","version":"1",
                 "x-agent-card":{"capabilities":{},"skills":[{"id":"weather","name":"Weather",
                 "description":"Forecast","tags":["weather"]}],"defaultInputModes":["text"],
                 "defaultOutputModes":["text"]}},"servers":[{"url":"https://example.com"}],"paths":{}}
                """.formatted(title);
    }

    private String assembled(String title) throws Exception {
        return new OpenApiAgentCardAssembler().assemble(TypedContent.create(openApi(title), ContentTypes.APPLICATION_JSON));
    }

    private String hash(String content) {
        return DigestUtils.sha256Hex(new JsonContentCanonicalizer().canonicalize(
                TypedContent.create(content, ContentTypes.APPLICATION_JSON), Map.of()).getContent().bytes());
    }

    private Map<String, String> labels(String content) {
        return Map.of(A2AConstants.LABEL_OPENAPI_AGENT_CARD_GENERATED,"true",
                A2AConstants.LABEL_OPENAPI_AGENT_CARD_SOURCE_GROUP_ID,GROUP,
                A2AConstants.LABEL_OPENAPI_AGENT_CARD_SOURCE_ARTIFACT_ID,SOURCE,
                A2AConstants.LABEL_OPENAPI_AGENT_CARD_SOURCE_GLOBAL_ID,"10",
                A2AConstants.LABEL_OPENAPI_AGENT_CARD_GENERATED_HASH,hash(content));
    }

    @BeforeEach
    void setup() {
        storage = mock(RegistryStorage.class);
        config = new OpenApiAgentCardConfig();
        config.enabled = true;
        config.syncOnUpdateEnabled = true;
        service = new OpenApiAgentCardService();
        service.config = config;
        service.log = LoggerFactory.getLogger(getClass());
        service.rulesService = mock(RulesService.class);
        service.authConfig = mock(AuthConfig.class);
        service.rbac = mock(RoleBasedAccessController.class);
        service.adminOverride = mock(AdminOverride.class);
        when(storage.getBranchTip(any(), any(), any())).thenAnswer(call -> new GAV((GA) call.getArgument(0), "1"));
        var source = ArtifactVersionMetaDataDto.builder().globalId(10).version("1").versionOrder(1)
                .groupId(GROUP).artifactId(SOURCE).artifactType(ArtifactType.OPENAPI).state(VersionState.ENABLED).build();
        when(storage.getArtifactVersionMetaData(GROUP, SOURCE, "1")).thenReturn(source);
        when(storage.getArtifactVersionMetaData(10L)).thenReturn(source);
        when(storage.getArtifactVersionContent(GROUP, SOURCE, "1")).thenReturn(StoredArtifactVersionDto.builder()
                .content(ContentHandle.create(openApi("New"))).contentType(ContentTypes.APPLICATION_JSON).build());
    }

    private void existing(String content, Map<String,String> provenance) {
        when(storage.getArtifactVersions(GROUP,CARD,RegistryStorage.RetrievalBehavior.ALL_STATES)).thenReturn(List.of("1"));
        when(storage.getArtifactMetaData(GROUP,CARD)).thenReturn(ArtifactMetaDataDto.builder()
                .artifactType(ArtifactType.AGENT_CARD).owner("alice").labels(provenance).build());
        when(storage.getArtifactVersionMetaData(GROUP,CARD,"1")).thenReturn(ArtifactVersionMetaDataDto.builder()
                .version("1").versionOrder(1).globalId(20).labels(provenance).build());
        when(storage.getArtifactVersionContent(GROUP,CARD,"1")).thenReturn(StoredArtifactVersionDto.builder()
                .content(ContentHandle.create(content)).build());
    }

    private void sync() { service.createOrSyncCompanion(storage,GROUP,SOURCE,"ignored stale payload","alice"); }

    private void noVersionWrite() {
        verify(storage, never()).createArtifactVersionIfLatest(anyString(),anyString(),any(),anyString(),
                any(),any(),any(),eq(false),anyString(),anyInt(),any());
    }

    @Test void featureAndSyncTogglesAreRespected() {
        config.enabled = false;
        assertNull(service.validateAndAssemble(TypedContent.create(openApi("New"),ContentTypes.APPLICATION_JSON),false));
        config.enabled = true;
        config.syncOnUpdateEnabled = false;
        assertNull(service.validateAndAssemble(TypedContent.create(openApi("New"),ContentTypes.APPLICATION_JSON),true));
    }

    @Test void generatedVersionCarriesItsOwnHashAndConditionalBase() throws Exception {
        String before = assembled("Old");
        existing(before, labels(before));
        sync();
        var metadata = ArgumentCaptor.forClass(EditableVersionMetaDataDto.class);
        verify(storage).createArtifactVersionIfLatest(eq(GROUP),eq(CARD),isNull(),eq(ArtifactType.AGENT_CARD),
                any(),metadata.capture(),eq(List.of()),eq(false),eq("alice"),eq(1),isNull());
        assertEquals(hash(assembled("New")),metadata.getValue().getLabels().get(A2AConstants.LABEL_OPENAPI_AGENT_CARD_GENERATED_HASH));
        assertEquals("10",metadata.getValue().getLabels().get(A2AConstants.LABEL_OPENAPI_AGENT_CARD_SOURCE_GLOBAL_ID));
        verify(storage,never()).mergeArtifactLabels(eq(GROUP),eq(CARD),anyString(),any());
    }

    @Test void missingHashStopsSync() throws Exception {
        String before = assembled("Old");
        var provenance = new HashMap<>(labels(before));
        provenance.remove(A2AConstants.LABEL_OPENAPI_AGENT_CARD_GENERATED_HASH);
        existing(before, provenance);
        sync();
        noVersionWrite();
    }

    @Test void manualEditAndUnrelatedArtifactArePreserved() throws Exception {
        existing(assembled("Human"),labels(assembled("Old")));
        sync();
        noVersionWrite();
    }

    @Test void existingUpToDateCardRepairsSourceLinkWithoutNewVersion() throws Exception {
        String current=assembled("New");
        existing(current,labels(current));
        doThrow(new RegistryStorageException("temporary link failure")).when(storage)
                .mergeArtifactLabels(eq(GROUP),eq(SOURCE),anyString(),any());
        sync();
        doNothing().when(storage).mergeArtifactLabels(eq(GROUP),eq(SOURCE),anyString(),any());
        sync();
        noVersionWrite();
        verify(storage,times(2)).mergeArtifactLabels(eq(GROUP),eq(SOURCE),anyString(),
                eq(Map.of(A2AConstants.LABEL_OPENAPI_AGENT_CARD_ARTIFACT_ID,CARD)));
    }

    @Test void sourceRecreationDoesNotAdoptOldGeneration() throws Exception {
        String before=assembled("Old");
        existing(before,labels(before));
        when(storage.getArtifactVersionMetaData(10L)).thenThrow(new ArtifactNotFoundException(GROUP,SOURCE));
        sync();
        noVersionWrite();
    }

    @Test void unauthorizedCompanionNeverReachesWrite() throws Exception {
        String before=assembled("Old");
        existing(before,labels(before));
        when(service.authConfig.isObacEnabled()).thenReturn(true);
        service.createOrSyncCompanion(storage,GROUP,SOURCE,"ignored","bob");
        noVersionWrite();
    }

    @Test void initialCompanionCreationHonorsConfiguredRules() {
        when(storage.getArtifactMetaData(GROUP,CARD)).thenThrow(new ArtifactNotFoundException(GROUP,CARD));
        doThrow(new RuleViolationException("Rejected",RuleType.VALIDITY,"FULL",
                Set.of(new RuleViolation("Rejected card","/"))))
                .when(service.rulesService).applyRules(eq(GROUP),eq(CARD),eq(ArtifactType.AGENT_CARD),any(),
                        eq(RuleApplicationType.CREATE),any(),any());
        sync();
        verify(storage,never()).createArtifact(anyString(),anyString(),anyString(),any(),any(),any(),any(),any(),
                eq(false),eq(false),anyString());
    }

    @Test void concurrentCompanionUpdateRejectsWithoutChangingProvenance() throws Exception {
        String before=assembled("Old");
        existing(before,labels(before));
        when(storage.createArtifactVersionIfLatest(eq(GROUP),eq(CARD),isNull(),eq(ArtifactType.AGENT_CARD),
                any(),any(),any(),eq(false),eq("alice"),eq(1),isNull()))
                .thenThrow(new CommitFailedException(GROUP,CARD,"Tip changed"));
        sync();
        verify(storage,times(3)).createArtifactVersionIfLatest(eq(GROUP),eq(CARD),isNull(),eq(ArtifactType.AGENT_CARD),
                any(),any(),any(),eq(false),eq("alice"),eq(1),isNull());
        verify(storage,never()).mergeArtifactLabels(anyString(),anyString(),anyString(),any());
        verify(storage,never()).createArtifactVersion(anyString(),anyString(),any(),anyString(),
                any(),any(),any(),eq(false),eq(false),anyString());
    }

    @Test void competingManualVersionIsReReadAndPreserved() throws Exception {
        String before=assembled("Old");
        existing(before,labels(before));
        when(storage.createArtifactVersionIfLatest(eq(GROUP),eq(CARD),isNull(),eq(ArtifactType.AGENT_CARD),
                any(),any(),any(),eq(false),eq("alice"),eq(1),isNull())).thenAnswer(call -> {
                    when(storage.getArtifactVersions(GROUP,CARD,RegistryStorage.RetrievalBehavior.ALL_STATES))
                            .thenReturn(List.of("1","2"));
                    when(storage.getArtifactVersionMetaData(GROUP,CARD,"2")).thenReturn(
                            ArtifactVersionMetaDataDto.builder().version("2").versionOrder(2).labels(Map.of()).build());
                    throw new CommitFailedException(GROUP,CARD,"Manual version won");
                });
        sync();
        verify(storage,times(1)).createArtifactVersionIfLatest(eq(GROUP),eq(CARD),isNull(),eq(ArtifactType.AGENT_CARD),
                any(),any(),any(),eq(false),eq("alice"),eq(1),isNull());
        verify(storage,never()).mergeArtifactLabels(anyString(),anyString(),anyString(),any());
        verify(storage).getArtifactVersionMetaData(GROUP,CARD,"2");
    }
}
