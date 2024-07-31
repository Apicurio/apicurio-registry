package io.apicurio.registry.storage.importing.v3;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.error.VersionAlreadyExistsException;
import io.apicurio.registry.storage.impexp.EntityInputStream;
import io.apicurio.registry.storage.impl.sql.RegistryStorageContentUtils;
import io.apicurio.registry.storage.impl.sql.SqlUtil;
import io.apicurio.registry.types.RegistryException;
import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.v3.ArtifactEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.v3.BranchEntity;
import io.apicurio.registry.utils.impexp.v3.CommentEntity;
import io.apicurio.registry.utils.impexp.v3.ContentEntity;
import io.apicurio.registry.utils.impexp.v3.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.v3.GroupEntity;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SqlDataImporter extends AbstractDataImporter {

    protected RegistryStorageContentUtils utils;

    protected final RegistryStorage storage;

    protected final boolean preserveGlobalId;

    protected final boolean preserveContentId;

    // To handle the case where we are trying to import a version before its content has been imported
    protected final List<ArtifactVersionEntity> waitingForContent = new ArrayList<>();

    // To handle the case where we are trying to import a comment before its version has been imported
    private final List<CommentEntity> waitingForVersion = new ArrayList<>();

    // ID remapping
    protected final Map<Long, Long> globalIdMapping = new HashMap<>();
    protected final Map<Long, Long> contentIdMapping = new HashMap<>();

    // To keep track of which versions have been imported
    private final Set<GAV> gavDone = new HashSet<>();
    private final Map<GAV, List<BranchEntity>> artifactBranchesWaitingForVersion = new HashMap<>();

    public SqlDataImporter(Logger logger, RegistryStorageContentUtils utils, RegistryStorage storage,
            boolean preserveGlobalId, boolean preserveContentId) {
        super(logger);
        this.utils = utils;
        this.storage = storage;
        this.preserveGlobalId = preserveGlobalId;
        this.preserveContentId = preserveContentId;
    }

    @Override
    public void importArtifactRule(ArtifactRuleEntity entity) {
        try {
            storage.importArtifactRule(entity);
            log.debug("Artifact rule imported successfully: {}", entity);
        } catch (Exception ex) {
            log.warn("Failed to import artifact rule {}: {}", entity, ex.getMessage());
        }
    }

    @Override
    protected void importArtifact(ArtifactEntity entity) {
        try {
            storage.importArtifact(entity);
            log.debug("Artifact imported successfully: {}", entity);
        } catch (Exception ex) {
            log.warn("Failed to import artifact {} / {}: {}", entity.groupId, entity.artifactId,
                    ex.getMessage());
        }
    }

    @Override
    public void importArtifactVersion(ArtifactVersionEntity entity) {
        try {
            // Content needs to be imported before artifact version
            if (!contentIdMapping.containsKey(entity.contentId)) {
                // Add to the queue waiting for content imported
                waitingForContent.add(entity);
                return;
            }

            entity.contentId = contentIdMapping.get(entity.contentId);

            var oldGlobalId = entity.globalId;
            if (!preserveGlobalId) {
                entity.globalId = storage.nextGlobalId();
            }

            storage.importArtifactVersion(entity);
            log.debug("Artifact version imported successfully: {}", entity);
            globalIdMapping.put(oldGlobalId, entity.globalId);
            var gav = new GAV(entity.groupId, entity.artifactId, entity.version);
            gavDone.add(gav);

            // Import comments that were waiting for this version
            var commentsToImport = waitingForVersion.stream()
                    .filter(comment -> comment.globalId == oldGlobalId).collect(Collectors.toList());
            for (CommentEntity commentEntity : commentsToImport) {
                importComment(commentEntity);
            }
            waitingForVersion.removeAll(commentsToImport);

            // Import branches waiting for version
            artifactBranchesWaitingForVersion.computeIfAbsent(gav, _ignored -> List.of())
                    .forEach(this::importEntity);
            artifactBranchesWaitingForVersion.remove(gav);

        } catch (VersionAlreadyExistsException ex) {
            if (ex.getGlobalId() != null) {
                log.warn("Duplicate globalId {} detected, skipping import of artifact version: {}",
                        ex.getGlobalId(), entity);
            } else {
                log.warn("Failed to import artifact version {}: {}", entity, ex.getMessage());
            }
        } catch (Exception ex) {
            log.warn("Failed to import artifact version {}: {}", entity, ex.getMessage());
        }
    }

    @Override
    public void importContent(ContentEntity entity) {
        try {
            List<ArtifactReferenceDto> references = SqlUtil
                    .deserializeReferences(entity.serializedReferences);

            if (entity.contentType == null) {
                throw new RuntimeException("ContentEntity is missing required field: contentType");
            }

            TypedContent typedContent = TypedContent.create(ContentHandle.create(entity.contentBytes),
                    entity.contentType);

            // We do not need canonicalHash if we have artifactType
            if (entity.canonicalHash == null && entity.artifactType != null) {
                TypedContent canonicalContent = utils.canonicalizeContent(entity.artifactType, typedContent,
                        storage.resolveReferences(references));
                entity.canonicalHash = DigestUtils.sha256Hex(canonicalContent.getContent().bytes());
            }

            var oldContentId = entity.contentId;
            if (!preserveContentId) {
                entity.contentId = storage.nextContentId();
            }

            storage.importContent(entity);
            log.debug("Content imported successfully: {}", entity);

            contentIdMapping.put(oldContentId, entity.contentId);

            // Import artifact versions that were waiting for this content
            var artifactsToImport = waitingForContent.stream()
                    .filter(artifactVersion -> artifactVersion.contentId == oldContentId)
                    .collect(Collectors.toList());

            for (ArtifactVersionEntity artifactVersionEntity : artifactsToImport) {
                artifactVersionEntity.contentId = entity.contentId;
                importArtifactVersion(artifactVersionEntity);
            }
            waitingForContent.removeAll(artifactsToImport);

        } catch (Exception ex) {
            log.warn("Failed to import content {}: {}", entity, ex.getMessage());
        }
    }

    @Override
    public void importGlobalRule(GlobalRuleEntity entity) {
        try {
            storage.importGlobalRule(entity);
            log.debug("Global rule imported successfully: {}", entity);
        } catch (Exception ex) {
            log.warn("Failed to import global rule {}: {}", entity, ex.getMessage());
        }
    }

    @Override
    public void importGroup(GroupEntity entity) {
        try {
            storage.importGroup(entity);
            log.debug("Group imported successfully: {}", entity);
        } catch (Exception ex) {
            log.warn("Failed to import group {}: {}", entity, ex.getMessage());
        }
    }

    @Override
    public void importComment(CommentEntity entity) {
        try {
            if (!globalIdMapping.containsKey(entity.globalId)) {
                // The version hasn't been imported yet. Need to wait for it.
                waitingForVersion.add(entity);
                return;
            }
            entity.globalId = globalIdMapping.get(entity.globalId);

            storage.importComment(entity);
            log.debug("Comment imported successfully: {}", entity);
        } catch (Exception ex) {
            log.warn("Failed to import comment {}: {}", entity, ex.getMessage());
        }
    }

    @Override
    protected void importBranch(BranchEntity entity) {
        try {
            storage.importBranch(entity);
            log.debug("Branch imported successfully: {}", entity);
        } catch (Exception ex) {
            log.warn("Failed to import branch {}: {}", entity, ex.getMessage());
        }
    }

    /**
     * WARNING: Must be executed within a transaction!
     */
    @Override
    public void importData(EntityInputStream entities, Runnable postImportAction) {
        try {
            Entity entity = null;
            while ((entity = entities.nextEntity()) != null) {
                importEntity(entity);
            }

            postImportAction.run();

            // Make sure the contentId sequence is set high enough
            storage.resetContentId();

            // Make sure the globalId sequence is set high enough
            storage.resetGlobalId();

            // Make sure the commentId sequence is set high enough
            storage.resetCommentId();

        } catch (IOException ex) {
            throw new RegistryException("Could not read next entity to import", ex);
        }
    }
}
