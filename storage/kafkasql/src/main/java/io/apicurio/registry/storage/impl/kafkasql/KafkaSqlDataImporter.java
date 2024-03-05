package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ContentAndReferencesDto;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.util.AbstractDataImporter;
import io.apicurio.registry.utils.impexp.*;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

public class KafkaSqlDataImporter extends AbstractDataImporter {
    private final KafkaSqlRegistryStorage registryStorage;

    private final boolean preserveGlobalId;

    // To handle the case where we are trying to import a version before its content has been imported...
    private final Map<Long, Long> contentIdMapping = new HashMap<>();
    private final ArrayList<ArtifactVersionEntity> waitingForContent = new ArrayList<>();

    // To handle the case where we are trying to import a comment before its version has been imported
    private final Set<Long> globalIds = new HashSet<>();
    private final ArrayList<CommentEntity> waitingForVersion = new ArrayList<>();

    /**
     * Constructor.
     * @param logger
     * @param registryStorage
     * @param preserveGlobalId
     */
    public KafkaSqlDataImporter(Logger logger, KafkaSqlRegistryStorage registryStorage, boolean preserveGlobalId) {
        super(logger);
        this.registryStorage = registryStorage;
        this.preserveGlobalId = preserveGlobalId;
    }

    @Override
    public void importArtifactRule(ArtifactRuleEntity entity) {
        registryStorage.importArtifactRule(entity);
    }

    @Override
    public void importArtifactVersion(ArtifactVersionEntity entity) {
        // Content needs to be imported before artifact version
        if (!contentIdMapping.containsKey(entity.contentId)) {
            // Add to the queue waiting for content imported
            waitingForContent.add(entity);
            return;
        }

        entity.contentId = contentIdMapping.get(entity.contentId);

        if (!preserveGlobalId) {
            entity.globalId = -1;
        }

        registryStorage.importArtifactVersion(entity);
        globalIds.add(entity.globalId);
        
        // Import comments that were waiting for this version
        var commentsToImport = waitingForVersion.stream()
                .filter(comment -> comment.globalId == entity.globalId)
                .collect(Collectors.toList());
        commentsToImport.forEach(this::importComment);
        waitingForVersion.removeAll(commentsToImport);
    }

    @Override
    public void importContent(ContentEntity entity) {
        // We need to resolve recursive references to compute canonical content hash.
        // We could wait for content dependencies, but they might not actually exist (reference integrity rule is optional).
        // TODO: We should either not compute the canonical hash during import, or do it after the import is finished,
        // but the column would have to be nullable.

        List<ArtifactReferenceDto> references = RegistryContentUtils.deserializeReferences(entity.serializedReferences);

        // We do not need canonicalHash if we have artifactType
        if (entity.canonicalHash == null && entity.artifactType != null) {
            entity.canonicalHash = RegistryContentUtils.canonicalContentHash(entity.artifactType,
                    ContentAndReferencesDto.builder()
                            .content(ContentHandle.create(entity.contentBytes))
                            .references(references)
                            .build(),
                    r -> getRegistryStorage().getContentByReference(r));
        }

        getContentIdMapping().put(entity.contentId, entity.contentId);

        registryStorage.importContent(entity);

        // Import artifact versions that were waiting for this content
        var artifactsToImport = waitingForContent.stream()
                .filter(artifactVersion -> artifactVersion.contentId == entity.contentId)
                .collect(Collectors.toList());

        artifactsToImport.forEach(this::importArtifactVersion);
        waitingForContent.removeAll(artifactsToImport);
    }

    @Override
    public void importGlobalRule(GlobalRuleEntity entity) {
        registryStorage.importGlobalRule(entity);
    }

    @Override
    public void importGroup(GroupEntity entity) {
        registryStorage.importGroup(entity);
    }

    @Override
    public void importComment(CommentEntity entity) {
        if (!globalIds.contains(entity.globalId)) {
            // The version hasn't been imported yet.  Need to wait for it.
            waitingForVersion.add(entity);
            return;
        }
        
        registryStorage.importComment(entity);
    }

    protected Map<Long, Long> getContentIdMapping() {
        return contentIdMapping;
    }

    protected ArrayList<ArtifactVersionEntity> getWaitingForContent() {
        return waitingForContent;
    }

    protected KafkaSqlRegistryStorage getRegistryStorage() {
        return registryStorage;
    }
}
