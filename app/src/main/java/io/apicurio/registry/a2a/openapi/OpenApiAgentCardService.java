package io.apicurio.registry.a2a.openapi;

import io.apicurio.registry.a2a.A2AConstants;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.json.content.canon.JsonContentCanonicalizer;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.model.VersionExpressionParser;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorage.RetrievalBehavior;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Auto-generates and keeps in sync a companion {@code AGENT_CARD} artifact for OPENAPI artifacts that
 * carry an {@code x-agent-card} vendor extension in their {@code info} block. See #7135.
 *
 * <p>The companion artifact is named {@code <openApiArtifactId>-agent-card} and lives in the same
 * group as the source OpenAPI artifact (so the existing group-level entitlement model applies to it
 * unchanged, per the A2A epic's key design decisions).
 *
 * <p>Callers must use this service in two steps, mirroring how {@code rulesService.applyRules(...)}
 * already validates OpenAPI content before it is persisted:
 * <ol>
 * <li>{@link #validateAndAssemble(TypedContent)} - called <b>before</b> the OpenAPI write. Lets
 * {@link io.apicurio.registry.rules.violation.RuleViolationException} propagate if the extension is
 * present but malformed, so a bad {@code x-agent-card} block rejects the OpenAPI write itself before
 * anything is persisted - exactly like any other content validation failure.</li>
 * <li>{@link #createOrSyncCompanion(RegistryStorage, String, String, String, String, boolean)} -
 * called <b>after</b> the OpenAPI write has already succeeded, passing the already-validated JSON
 * from step 1. Storage errors here (or a naming collision with an artifact this feature did not
 * create) are logged and swallowed: the OpenAPI write the user actually asked for must never fail
 * because of trouble with this derived side-artifact.</li>
 * </ol>
 */
@ApplicationScoped
public class OpenApiAgentCardService {

    @Inject
    Logger log;

    @Inject
    OpenApiAgentCardConfig config;

    private final OpenApiAgentCardAssembler assembler = new OpenApiAgentCardAssembler();
    private final JsonContentCanonicalizer canonicalizer = new JsonContentCanonicalizer();

    /**
     * Validates the given OpenAPI content's {@code x-agent-card} extension (if any) and returns the
     * assembled Agent Card JSON, ready to be passed to
     * {@link #createOrSyncCompanion(RegistryStorage, String, String, String, String, boolean)} once
     * the caller's OpenAPI write has succeeded. Call this <b>before</b> writing the OpenAPI content to
     * storage.
     *
     * @param openApiContent the OpenAPI content about to be written
     * @param isUpdate       {@code true} if this is a version update to an existing OpenAPI artifact,
     *                       {@code false} if the OpenAPI artifact is being created
     * @return the assembled Agent Card JSON, or {@code null} if there is nothing to do (the feature is
     *         disabled, sync-on-update is disabled and this is an update, or the OpenAPI content has no
     *         {@code x-agent-card} extension)
     * @throws io.apicurio.registry.rules.violation.RuleViolationException if the extension is present
     *         but does not assemble into a valid A2A v1.0 Agent Card. Intentionally not swallowed: this
     *         is what rejects the caller's OpenAPI write.
     */
    public String validateAndAssemble(TypedContent openApiContent, boolean isUpdate) {
        if (!config.isEnabled()) {
            return null;
        }
        if (isUpdate && !config.isSyncOnUpdateEnabled()) {
            return null;
        }

        try {
            return assembler.assemble(openApiContent);
        } catch (IOException e) {
            // The caller is about to attempt (or has already attempted, depending on call order) to
            // parse/validate this same content through its own content validator, which will surface
            // a much better syntax error than we could here. Treat this as "nothing to do" rather than
            // duplicating that failure with a less clear message.
            log.debug("Failed to parse OpenAPI content while checking for '{}': {}",
                    OpenApiAgentCardAssembler.EXTENSION_KEY, e.getMessage());
            return null;
        }
    }

    /**
     * Creates or synchronizes the companion AGENT_CARD artifact for an OpenAPI artifact whose
     * {@code x-agent-card} extension was already validated and assembled by
     * {@link #validateAndAssemble(TypedContent, boolean)}. Call this <b>after</b> the OpenAPI write has
     * already succeeded, passing that same {@code assembledJson}.
     *
     * @param storage          the storage to operate on (the full decorator-wrapped
     *                         {@code @Current RegistryStorage}, so the companion write is subject to
     *                         the same read-only/limits/search-indexing behavior as any other write)
     * @param groupId          the raw (already-normalized) group ID of the OpenAPI artifact
     * @param openApiArtifactId the artifact ID of the OpenAPI artifact
     * @param assembledJson    the Agent Card JSON previously returned by
     *                         {@link #validateAndAssemble(TypedContent, boolean)}; must not be
     *                         {@code null}
     * @param owner            the owner to record on the companion artifact/version
     */
    public void createOrSyncCompanion(RegistryStorage storage, String groupId, String openApiArtifactId,
            String assembledJson, String owner) {
        String companionArtifactId = companionArtifactId(openApiArtifactId);
        try {
            createOrSyncCompanionInternal(storage, groupId, openApiArtifactId, companionArtifactId,
                    assembledJson, owner);
        } catch (Exception e) {
            log.warn("Failed to auto-generate/sync Agent Card companion '{}' for OpenAPI artifact {}/{}: {}",
                    companionArtifactId, groupId, openApiArtifactId, e.getMessage(), e);
        }
    }

    private void createOrSyncCompanionInternal(RegistryStorage storage, String groupId, String openApiArtifactId,
            String companionArtifactId, String assembledJson, String owner) {
        String newHash = canonicalHash(assembledJson);

        ArtifactMetaDataDto existingCompanion;
        try {
            existingCompanion = storage.getArtifactMetaData(groupId, companionArtifactId);
        } catch (ArtifactNotFoundException e) {
            createCompanion(storage, groupId, openApiArtifactId, companionArtifactId, assembledJson, newHash, owner);
            return;
        }

        if (!isGeneratedFrom(existingCompanion, groupId, openApiArtifactId)) {
            log.warn("Skipping Agent Card auto-generation for OpenAPI artifact {}/{}: an artifact named "
                    + "'{}' already exists in the same group and was not generated by this feature",
                    groupId, openApiArtifactId, companionArtifactId);
            return;
        }

        String recordedHash = labelValue(existingCompanion.getLabels(),
                A2AConstants.LABEL_OPENAPI_AGENT_CARD_GENERATED_HASH);
        String actualLatestHash = canonicalHash(fetchLatestVersionContent(storage, groupId, companionArtifactId));

        if (recordedHash != null && !recordedHash.equals(actualLatestHash)) {
            log.warn("Skipping Agent Card auto-sync for OpenAPI artifact {}/{}: the generated Agent Card "
                    + "'{}' was modified directly since it was last generated", groupId, openApiArtifactId,
                    companionArtifactId);
            return;
        }

        if (newHash.equals(actualLatestHash)) {
            // Already up to date - nothing to do.
            return;
        }

        syncCompanion(storage, groupId, openApiArtifactId, companionArtifactId, assembledJson, newHash, owner);
    }

    private void createCompanion(RegistryStorage storage, String groupId, String openApiArtifactId,
            String companionArtifactId, String assembledJson, String contentHash, String owner) {
        Map<String, String> labels = generatedLabels(groupId, openApiArtifactId, contentHash);

        EditableArtifactMetaDataDto artifactMeta = EditableArtifactMetaDataDto.builder()
                .name(companionArtifactId)
                .description("Auto-generated from the 'x-agent-card' extension of OpenAPI artifact "
                        + describeSource(groupId, openApiArtifactId))
                .labels(labels)
                .build();
        EditableVersionMetaDataDto versionMeta = EditableVersionMetaDataDto.builder()
                .name(companionArtifactId).labels(labels).build();
        ContentWrapperDto contentWrapper = ContentWrapperDto.builder()
                .content(ContentHandle.create(assembledJson)).contentType(ContentTypes.APPLICATION_JSON)
                .references(Collections.emptyList()).build();

        storage.createArtifact(groupId, companionArtifactId, ArtifactType.AGENT_CARD, artifactMeta, "1",
                contentWrapper, versionMeta, Collections.emptyList(), false, false, owner);
        markSourceWithCompanionId(storage, groupId, openApiArtifactId, companionArtifactId);
        log.info("Auto-generated Agent Card artifact '{}' from OpenAPI artifact {}", companionArtifactId,
                describeSource(groupId, openApiArtifactId));
    }

    private void syncCompanion(RegistryStorage storage, String groupId, String openApiArtifactId,
            String companionArtifactId, String assembledJson, String contentHash, String owner) {
        EditableVersionMetaDataDto versionMeta = EditableVersionMetaDataDto.builder()
                .name(companionArtifactId).build();
        ContentWrapperDto contentWrapper = ContentWrapperDto.builder()
                .content(ContentHandle.create(assembledJson)).contentType(ContentTypes.APPLICATION_JSON)
                .references(Collections.emptyList()).build();

        storage.createArtifactVersion(groupId, companionArtifactId, null, ArtifactType.AGENT_CARD,
                contentWrapper, versionMeta, Collections.emptyList(), false, false, owner);
        storage.mergeArtifactLabels(groupId, companionArtifactId, A2AConstants.PREFIX_OPENAPI_AGENT_CARD,
                generatedLabels(groupId, openApiArtifactId, contentHash));
        log.info("Synced Agent Card artifact '{}' from updated OpenAPI artifact {}", companionArtifactId,
                describeSource(groupId, openApiArtifactId));
    }

    private boolean isGeneratedFrom(ArtifactMetaDataDto companion, String groupId, String openApiArtifactId) {
        Map<String, String> labels = companion.getLabels();
        if (labels == null) {
            return false;
        }
        return "true".equals(labels.get(A2AConstants.LABEL_OPENAPI_AGENT_CARD_GENERATED))
                && normalizeGroupId(groupId)
                        .equals(labels.get(A2AConstants.LABEL_OPENAPI_AGENT_CARD_SOURCE_GROUP_ID))
                && openApiArtifactId.equals(labels.get(A2AConstants.LABEL_OPENAPI_AGENT_CARD_SOURCE_ARTIFACT_ID));
    }

    /**
     * Records the generated companion's artifact ID as a label on the source OpenAPI artifact, so a
     * human or a tool (e.g. the UI) can find it without re-deriving the naming convention. Best-effort:
     * failures here are logged by the caller's blanket {@code catch (Exception e)} and never prevent
     * the companion itself from having been created successfully.
     */
    private void markSourceWithCompanionId(RegistryStorage storage, String groupId, String openApiArtifactId,
            String companionArtifactId) {
        storage.mergeArtifactLabels(groupId, openApiArtifactId, A2AConstants.PREFIX_OPENAPI_AGENT_CARD,
                Map.of(A2AConstants.LABEL_OPENAPI_AGENT_CARD_ARTIFACT_ID, companionArtifactId));
    }

    private Map<String, String> generatedLabels(String groupId, String openApiArtifactId, String contentHash) {
        Map<String, String> labels = new HashMap<>();
        labels.put(A2AConstants.LABEL_OPENAPI_AGENT_CARD_GENERATED, "true");
        labels.put(A2AConstants.LABEL_OPENAPI_AGENT_CARD_SOURCE_GROUP_ID, normalizeGroupId(groupId));
        labels.put(A2AConstants.LABEL_OPENAPI_AGENT_CARD_SOURCE_ARTIFACT_ID, openApiArtifactId);
        labels.put(A2AConstants.LABEL_OPENAPI_AGENT_CARD_GENERATED_HASH, contentHash);
        return labels;
    }

    private String fetchLatestVersionContent(RegistryStorage storage, String groupId, String artifactId) {
        GA ga = new GA(groupId, artifactId);
        GAV gav = VersionExpressionParser.parse(ga, "branch=latest",
                (g, branchId) -> storage.getBranchTip(g, branchId, RetrievalBehavior.SKIP_DISABLED_LATEST));
        StoredArtifactVersionDto stored = storage.getArtifactVersionContent(gav.getRawGroupIdWithNull(),
                gav.getRawArtifactId(), gav.getRawVersionId());
        return stored.getContent().content();
    }

    private String canonicalHash(String json) {
        TypedContent canonical = canonicalizer.canonicalize(
                TypedContent.create(json, ContentTypes.APPLICATION_JSON), Collections.emptyMap());
        return DigestUtils.sha256Hex(canonical.getContent().bytes());
    }

    private String labelValue(Map<String, String> labels, String key) {
        return labels == null ? null : labels.get(key);
    }

    private String normalizeGroupId(String groupId) {
        return new GroupId(groupId).getRawGroupIdWithDefaultString();
    }

    private String describeSource(String groupId, String openApiArtifactId) {
        return normalizeGroupId(groupId) + "/" + openApiArtifactId;
    }

    private String companionArtifactId(String openApiArtifactId) {
        return openApiArtifactId + "-agent-card";
    }
}
