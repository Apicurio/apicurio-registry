package io.apicurio.registry.storage.decorator;

import io.apicurio.registry.a2a.A2AConfig;
import io.apicurio.registry.a2a.AgentCardLabelExtractor;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.types.ArtifactType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Storage decorator that automatically extracts and adds labels for AGENT_CARD artifacts.
 * This enables capability-based search for agent cards.
 */
@ApplicationScoped
public class AgentCardLabelsStorageDecorator extends RegistryStorageDecoratorBase
        implements RegistryStorageDecorator {

    @Inject
    A2AConfig a2aConfig;

    @Inject
    AgentCardLabelExtractor labelExtractor;

    @Override
    public boolean isEnabled() {
        return a2aConfig.isEnabled();
    }

    @Override
    public int order() {
        return RegistryStorageDecoratorOrderConstants.AGENT_CARD_LABELS_DECORATOR;
    }

    @Override
    public Pair<ArtifactMetaDataDto, ArtifactVersionMetaDataDto> createArtifact(String groupId,
            String artifactId, String artifactType, EditableArtifactMetaDataDto artifactMetaData,
            String version, ContentWrapperDto versionContent, EditableVersionMetaDataDto versionMetaData,
            List<String> versionBranches, boolean versionIsDraft, boolean dryRun, String owner)
            throws RegistryStorageException {

        // If this is an AGENT_CARD, extract and merge labels
        if (ArtifactType.AGENT_CARD.equals(artifactType) && versionContent != null
                && versionContent.getContent() != null) {
            versionMetaData = mergeAgentCardLabels(versionContent, versionMetaData);
        }

        return super.createArtifact(groupId, artifactId, artifactType, artifactMetaData, version,
                versionContent, versionMetaData, versionBranches, versionIsDraft, dryRun, owner);
    }

    @Override
    public ArtifactVersionMetaDataDto createArtifactVersion(String groupId, String artifactId, String version,
            String artifactType, ContentWrapperDto content, EditableVersionMetaDataDto metaData,
            List<String> branches, boolean isDraft, boolean dryRun, String owner)
            throws RegistryStorageException {

        // If this is an AGENT_CARD, extract and merge labels
        if (ArtifactType.AGENT_CARD.equals(artifactType) && content != null && content.getContent() != null) {
            metaData = mergeAgentCardLabels(content, metaData);
        }

        return super.createArtifactVersion(groupId, artifactId, version, artifactType, content, metaData,
                branches, isDraft, dryRun, owner);
    }

    private EditableVersionMetaDataDto mergeAgentCardLabels(ContentWrapperDto content,
            EditableVersionMetaDataDto metaData) {
        // Extract labels from agent card content
        Map<String, String> extractedLabels = labelExtractor.extractLabels(content.getContent());

        if (extractedLabels.isEmpty()) {
            return metaData;
        }

        // Create a new metaData object if null
        if (metaData == null) {
            metaData = new EditableVersionMetaDataDto();
        }

        // Merge labels (user-provided labels take precedence)
        Map<String, String> mergedLabels = new HashMap<>(extractedLabels);
        if (metaData.getLabels() != null) {
            mergedLabels.putAll(metaData.getLabels());
        }
        metaData.setLabels(mergedLabels);

        return metaData;
    }
}
