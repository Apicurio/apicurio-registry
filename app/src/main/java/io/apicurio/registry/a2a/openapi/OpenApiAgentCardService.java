package io.apicurio.registry.a2a.openapi;

import io.apicurio.registry.a2a.A2AConstants;
import io.apicurio.registry.auth.AuthConfig;
import io.apicurio.registry.auth.AdminOverride;
import io.apicurio.registry.auth.RoleBasedAccessController;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.json.content.canon.JsonContentCanonicalizer;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.rules.RuleApplicationType;
import io.apicurio.registry.rules.RulesService;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorage.RetrievalBehavior;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.error.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.CommitFailedException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.VersionState;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Comparator;
import java.util.Objects;

/** Best-effort, authorized generation of published OpenAPI companions. */
@ApplicationScoped
public class OpenApiAgentCardService {

    @Inject
    Logger log;
    @Inject
    OpenApiAgentCardConfig config;
    @Inject
    RulesService rulesService;
    @Inject
    AuthConfig authConfig;
    @Inject
    RoleBasedAccessController rbac;
    @Inject
    AdminOverride adminOverride;

    private final OpenApiAgentCardAssembler assembler = new OpenApiAgentCardAssembler();
    private final JsonContentCanonicalizer canonicalizer = new JsonContentCanonicalizer();

    /** Preflight only for a version being published; callers must skip ordinary drafts. */
    public String validateAndAssemble(TypedContent content, boolean isUpdate) {
        if (!config.isEnabled() || (isUpdate && !config.isSyncOnUpdateEnabled())) {
            return null;
        }
        try {
            return assembler.assemble(content);
        } catch (IOException e) {
            log.debug("Cannot parse OpenAPI content for Agent Card generation", e);
            return null;
        }
    }

    /**
     * Called after publication. Re-read the published source tip instead of trusting a stale request
     * body. The source write is independent; companion failures leave it intact and are logged.
     */
    public void createOrSyncCompanion(RegistryStorage storage, String groupId, String sourceId,
            String assembledJson, String owner) {
        // Optimistic conflicts require a fresh source/target snapshot, not a retry of stale writes.
        // Bound contention work. Genuine storage failures remain best-effort and are not retried here.
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                synchronize(storage, groupId, sourceId, owner);
                return;
            } catch (CommitFailedException | ArtifactAlreadyExistsException e) {
                log.debug("Concurrent companion change for {}/{}; retrying from current source", groupId, sourceId);
            } catch (Exception e) {
                log.warn("Could not synchronize Agent Card for OpenAPI {}/{}", groupId, sourceId, e);
                return;
            }
        }
        log.warn("Companion synchronization deferred after concurrent writes for {}/{}", groupId, sourceId);
    }

    private void synchronize(RegistryStorage storage, String groupId, String sourceId, String owner)
            throws IOException {
        if (!config.isEnabled()) {
            return;
        }
        String companionId = sourceId + "-agent-card";
        ArtifactMetaDataDto companion;
        try {
            companion = storage.getArtifactMetaData(groupId, companionId);
        } catch (ArtifactNotFoundException e) {
            companion = null;
        }
        ArtifactVersionMetaDataDto source = latest(storage, groupId, sourceId);
        if (!ArtifactType.OPENAPI.equals(source.getArtifactType()) || source.getState() == VersionState.DRAFT) {
            return;
        }
        var storedSource = storage.getArtifactVersionContent(groupId, sourceId, source.getVersion());
        String json = assembler.assemble(TypedContent.create(storedSource.getContent(), storedSource.getContentType()));
        if (json == null) {
            return;
        }
        String hash = canonicalHash(json);
        Map<String, String> provenance = provenance(groupId, sourceId, source.getGlobalId(), hash);
        TypedContent card = TypedContent.create(json, ContentTypes.APPLICATION_JSON);

        if (companion == null) {
            rulesService.applyRules(groupId, companionId, ArtifactType.AGENT_CARD, card,
                    RuleApplicationType.CREATE, List.of(), Map.of());
            // If another request already published the companion, inspect it rather than overwrite it.
            storage.createArtifact(groupId, companionId, ArtifactType.AGENT_CARD,
                        EditableArtifactMetaDataDto.builder().name(companionId).labels(provenance).build(), "1",
                        content(json), EditableVersionMetaDataDto.builder().name(companionId).labels(provenance).build(),
                        List.of(), false, false, owner);
        } else {
            requireCompanionOwner(companion, owner);
            if (!ArtifactType.AGENT_CARD.equals(companion.getArtifactType())
                    || !isGeneratedFrom(companion.getLabels(), groupId, sourceId)) {
                log.warn("Skipping unrelated companion {}/{}", groupId, companionId);
                return;
            }
            ArtifactVersionMetaDataDto previous = newest(storage, groupId, companionId);
            if (previous.getState() == VersionState.DRAFT) {
                log.warn("Preserving draft companion {}/{}", groupId, companionId);
                return;
            }
            Map<String, String> labels = previous.getLabels();
            if (!isGeneratedFrom(labels, groupId, sourceId)
                    || !validSourceVersion(storage, groupId, sourceId, labels)) {
                log.warn("Missing generation provenance for {}/{}; explicit regeneration required", groupId, companionId);
                return;
            }
            String recorded = labels.get(A2AConstants.LABEL_OPENAPI_AGENT_CARD_GENERATED_HASH);
            String actual = canonicalHash(storage.getArtifactVersionContent(groupId, companionId,
                    previous.getVersion()).getContent().content());
            if (recorded == null || !recorded.equals(actual)) {
                log.warn("Preserving manually edited companion {}/{}", groupId, companionId);
                return;
            }
            if (!hash.equals(actual)) {
                rulesService.applyRules(groupId, companionId, ArtifactType.AGENT_CARD, card,
                        RuleApplicationType.UPDATE, List.of(), Map.of());
                if (latest(storage, groupId, sourceId).getGlobalId() != source.getGlobalId()) {
                    throw new CommitFailedException(groupId, companionId, "Source advanced during generation");
                }
                // Content and provenance commit together. Concurrent manual/generated versions reject
                // this write instead of moving the companion tip backwards or losing manual changes.
                storage.createArtifactVersionIfLatest(groupId, companionId, null, ArtifactType.AGENT_CARD,
                        content(json), EditableVersionMetaDataDto.builder().name(companionId).labels(provenance).build(),
                        List.of(), false, owner, previous.getVersionOrder(), null);
            }
        }
        if (latest(storage, groupId, sourceId).getGlobalId() != source.getGlobalId()) {
            throw new CommitFailedException(groupId, companionId, "Source advanced during generation");
        }
        // Repairable, best-effort navigation hint. It is not ownership or generation proof.
        storage.mergeArtifactLabels(groupId, sourceId, A2AConstants.PREFIX_OPENAPI_AGENT_CARD,
                Map.of(A2AConstants.LABEL_OPENAPI_AGENT_CARD_ARTIFACT_ID, companionId));
    }

    private void requireCompanionOwner(ArtifactMetaDataDto companion, String owner) {
        if (authConfig.isObacEnabled() && companion.getOwner() != null
                && !Objects.equals(companion.getOwner(), owner)
                && !adminOverride.isAdmin()
                && !(authConfig.isRbacEnabled() && rbac.isAdmin())) {
            throw new ForbiddenException("Not authorized to synchronize the companion artifact");
        }
    }

    private boolean validSourceVersion(RegistryStorage storage, String groupId, String sourceId,
            Map<String, String> labels) {
        String id = labels.get(A2AConstants.LABEL_OPENAPI_AGENT_CARD_SOURCE_GLOBAL_ID);
        if (id == null) {
            return false;
        }
        try {
            var source = storage.getArtifactVersionMetaData(Long.parseLong(id));
            return Objects.equals(new GroupId(groupId).getRawGroupIdWithDefaultString(),
                    new GroupId(source.getGroupId()).getRawGroupIdWithDefaultString())
                    && sourceId.equals(source.getArtifactId()) && ArtifactType.OPENAPI.equals(source.getArtifactType());
        } catch (NumberFormatException | ArtifactNotFoundException | VersionNotFoundException e) {
            return false;
        }
    }

    private ArtifactVersionMetaDataDto latest(RegistryStorage storage, String groupId, String artifactId) {
        var gav = storage.getBranchTip(new GA(groupId, artifactId), BranchId.LATEST,
                RetrievalBehavior.SKIP_DISABLED_LATEST);
        return storage.getArtifactVersionMetaData(groupId, artifactId, gav.getRawVersionId());
    }

    private ArtifactVersionMetaDataDto newest(RegistryStorage storage, String groupId, String artifactId) {
        return storage.getArtifactVersions(groupId, artifactId, RetrievalBehavior.ALL_STATES).stream()
                .map(version -> storage.getArtifactVersionMetaData(groupId, artifactId, version))
                .max(Comparator.comparingInt(ArtifactVersionMetaDataDto::getVersionOrder))
                .orElseThrow(() -> new ArtifactNotFoundException(groupId, artifactId));
    }

    private boolean isGeneratedFrom(Map<String, String> labels, String groupId, String sourceId) {
        return labels != null && "true".equals(labels.get(A2AConstants.LABEL_OPENAPI_AGENT_CARD_GENERATED))
                && new GroupId(groupId).getRawGroupIdWithDefaultString()
                        .equals(labels.get(A2AConstants.LABEL_OPENAPI_AGENT_CARD_SOURCE_GROUP_ID))
                && sourceId.equals(labels.get(A2AConstants.LABEL_OPENAPI_AGENT_CARD_SOURCE_ARTIFACT_ID));
    }

    private Map<String, String> provenance(String groupId, String sourceId, long globalId, String hash) {
        Map<String, String> labels = new HashMap<>();
        labels.put(A2AConstants.LABEL_OPENAPI_AGENT_CARD_GENERATED, "true");
        labels.put(A2AConstants.LABEL_OPENAPI_AGENT_CARD_SOURCE_GROUP_ID, new GroupId(groupId).getRawGroupIdWithDefaultString());
        labels.put(A2AConstants.LABEL_OPENAPI_AGENT_CARD_SOURCE_ARTIFACT_ID, sourceId);
        labels.put(A2AConstants.LABEL_OPENAPI_AGENT_CARD_SOURCE_GLOBAL_ID, Long.toString(globalId));
        labels.put(A2AConstants.LABEL_OPENAPI_AGENT_CARD_GENERATED_HASH, hash);
        return labels;
    }

    private ContentWrapperDto content(String json) {
        return ContentWrapperDto.builder().content(ContentHandle.create(json))
                .contentType(ContentTypes.APPLICATION_JSON).references(List.of()).build();
    }

    private String canonicalHash(String json) {
        return DigestUtils.sha256Hex(canonicalizer.canonicalize(TypedContent.create(json,
                ContentTypes.APPLICATION_JSON), Map.of()).getContent().bytes());
    }
}
